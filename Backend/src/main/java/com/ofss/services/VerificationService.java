package com.ofss.services;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import com.ofss.beans.*;
import com.ofss.excp.ResourceNotFoundExcp;
import com.ofss.excp.TransactionValidationException;
import com.ofss.repository.*;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VerificationService {
    private static final BigDecimal MINIMUM_BALANCE = new BigDecimal("5000.00");
    private final VerificationRepository verifications;
    private final AccountDao accounts;
    private final TransactionDao transactions;
    private final AuditLogDao audits;
    private final LoginService login;

    public VerificationService(VerificationRepository verifications, AccountDao accounts,
            TransactionDao transactions, AuditLogDao audits, LoginService login) {
        this.verifications = verifications;
        this.accounts = accounts;
        this.transactions = transactions;
        this.audits = audits;
        this.login = login;
    }

    // Wrong credentials must commit LoginService's persisted attempt/lockout update.
    // All payment failures still roll back the debit, state and audit atomically.
    @Transactional(noRollbackFor = {BadCredentialsException.class, LockedException.class})
    public VerifiedTransactionResponse verify(Long id, Long callerId, String password, String key) {
        if (id == null || id <= 0 || callerId == null || callerId <= 0) {
            throw new IllegalArgumentException("A valid transaction and signed-in customer are required");
        }
        if (key == null || key.isBlank() || key.getBytes(StandardCharsets.UTF_8).length > 100) {
            throw new IllegalArgumentException("Idempotency-Key is required and must not exceed 100 UTF-8 bytes");
        }
        if (password == null || password.isBlank() || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("A password of at most 72 UTF-8 bytes is required");
        }
        Long accountId = verifications.ownedAccountId(id, callerId)
                .orElseThrow(() -> new ResourceNotFoundExcp("Transaction not found"));
        LoginPrincipal authenticated = login.verifyPassword(callerId, password);
        if (!Objects.equals(authenticated.userId(), callerId) || !"CUSTOMER".equals(authenticated.role())) {
            throw new TransactionValidationException(403, "An active CUSTOMER must verify this payment");
        }
        // Login takes the user lock first. Every money writer shares the account lock.
        Account account = accounts.findForTransaction(accountId, callerId)
                .orElseThrow(() -> new ResourceNotFoundExcp("Account not found"));
        TransactionDb replay = verifications.findByVerificationIdempotencyKey(key).orElse(null);
        if (replay != null) {
            if (!Objects.equals(replay.getFromAccount().getUserId(), callerId)) {
                throw new ResourceNotFoundExcp("Transaction not found");
            }
            if (!Objects.equals(replay.getTransactionId(), id) || replay.getState() != TransactionState.SETTLED
                    || replay.getVerifiedAt() == null) {
                throw new TransactionValidationException(409, "Idempotency-Key was already used for another verification");
            }
            return VerifiedTransactionResponse.from(replay);
        }
        TransactionDb transaction = verifications.findByTransactionIdAndFromAccountUserUserId(id, callerId)
                .orElseThrow(() -> new ResourceNotFoundExcp("Transaction not found"));
        if (transaction.getState() != TransactionState.HARD_HOLD || !transaction.isAuthenticationRequired()
                || (transaction.getRiskTier() != RiskTier.VERY_HIGH && transaction.getRiskTier() != RiskTier.HARD_HOLD)) {
            throw new TransactionValidationException(409, "Only an authentication-required HARD_HOLD payment may be verified");
        }
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new TransactionValidationException(409, "Source account must be ACTIVE");
        }
        BigDecimal pending = transactions.pendingAmount(accountId, List.of(TransactionState.PROTECTED, TransactionState.HARD_HOLD));
        if (account.getBalance() == null || pending == null || transaction.getAmount() == null
                || transaction.getAmount().signum() <= 0 || pending.compareTo(transaction.getAmount()) < 0
                || account.getBalance().subtract(pending).compareTo(MINIMUM_BALANCE) < 0) {
            throw new TransactionValidationException(409, "Available funds must cover all held payments and the INR 5000 minimum");
        }
        BigDecimal remaining = account.getBalance().subtract(transaction.getAmount());
        if (verifications.settleVerified(id, transaction.getVersion(), key) != 1) {
            throw new TransactionValidationException(409, "Payment changed concurrently; refresh before retrying");
        }
        // The conditional state update clears managed entities; reload under the held DB lock.
        Account locked = accounts.findForTransaction(accountId, callerId)
                .orElseThrow(() -> new ResourceNotFoundExcp("Account not found"));
        locked.setBalance(remaining);
        accounts.save(locked);
        TransactionDb settled = verifications.findByTransactionIdAndFromAccountUserUserId(id, callerId)
                .orElseThrow(() -> new ResourceNotFoundExcp("Transaction not found"));
        AuditLog audit = new AuditLog();
        audit.setTransactionId(id);
        audit.setUserId(callerId);
        audit.setAction("TRANSACTION_VERIFIED_SETTLED");
        audit.setOldState(TransactionState.HARD_HOLD.name());
        audit.setNewState(TransactionState.SETTLED.name());
        audit.setCreatedAt(settled.getSettledAt());
        audits.save(audit);
        return VerifiedTransactionResponse.from(settled);
    }
}
