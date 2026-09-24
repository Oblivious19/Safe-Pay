package com.ofss.services;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import com.ofss.beans.*;
import com.ofss.excp.ResourceNotFoundExcp;
import com.ofss.excp.TransactionValidationException;
import com.ofss.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminApprovalService {
    private static final BigDecimal MINIMUM_BALANCE = new BigDecimal("5000.00");
    private static final String ACTION = "ADMIN_APPROVED_SETTLED";
    private final VerificationRepository verifications;
    private final AccountDao accounts;
    private final TransactionDao transactions;
    private final AuditLogDao audits;
    private final CurrentSessionService sessions;

    public AdminApprovalService(VerificationRepository verifications, AccountDao accounts,
            TransactionDao transactions, AuditLogDao audits, CurrentSessionService sessions) {
        this.verifications = verifications; this.accounts = accounts; this.transactions = transactions;
        this.audits = audits; this.sessions = sessions;
    }

    private void requireAdmin(LoginPrincipal caller) {
        if (caller == null || !"ADMIN".equals(caller.role()) || !sessions.isCurrent(caller)) {
            throw new TransactionValidationException(403, "An active administrator must review this payment");
        }
    }

    @Transactional(readOnly = true)
    public List<AdminApprovalRequestView> pending(LoginPrincipal caller) {
        requireAdmin(caller);
        // The transaction is the durable notification; it survives logout and server restarts.
        return verifications.findPendingByCategoryPriority()
                .stream().map(AdminApprovalRequestView::from).toList();
    }

    @Transactional
    public VerifiedTransactionResponse approve(Long id, LoginPrincipal caller, String key) {
        requireAdmin(caller);
        if (id == null || id <= 0) throw new IllegalArgumentException("A valid transaction is required");
        if (key == null || key.isBlank() || key.getBytes(StandardCharsets.UTF_8).length > 100) {
            throw new IllegalArgumentException("Idempotency-Key is required and must not exceed 100 UTF-8 bytes");
        }
        Long accountId = verifications.accountId(id)
                .orElseThrow(() -> new ResourceNotFoundExcp("Transaction not found"));
        Long receiverId = accounts.findPaymentRecipientId(id).orElse(null);
        // Lock both accounts in a stable order before reading payment state.
        Account account = TransferBalances.lockPair(accounts, accountId, receiverId);
        AuditLog receipt = audits.findByRequestKey(key).orElse(null);
        TransactionDb transaction = verifications.findByTransactionId(id)
                .orElseThrow(() -> new ResourceNotFoundExcp("Transaction not found"));
        if (receipt != null) {
            if (!ACTION.equals(receipt.getAction()) || !Objects.equals(receipt.getTransactionId(), id)
                    || !Objects.equals(receipt.getUserId(), caller.userId())
                    || transaction.getState() != TransactionState.SETTLED || transaction.getVerifiedAt() == null
                    || !key.equals(transaction.getVerificationIdempotencyKey())) {
                throw new TransactionValidationException(409, "Idempotency-Key was already used for another operation");
            }
            return VerifiedTransactionResponse.from(transaction);
        }
        if (verifications.findByVerificationIdempotencyKey(key).isPresent()) {
            throw new TransactionValidationException(409, "Idempotency-Key was already used for another verification");
        }
        if (transaction.getState() != TransactionState.HARD_HOLD || !transaction.isAuthenticationRequired()
                || (transaction.getRiskTier() != RiskTier.VERY_HIGH && transaction.getRiskTier() != RiskTier.HARD_HOLD)) {
            throw new TransactionValidationException(409, "Only a HARD_HOLD payment awaiting approval may be approved");
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
        if (verifications.settleVerified(id, transaction.getVersion(), key) != 1) {
            throw new TransactionValidationException(409, "Payment changed concurrently; refresh before retrying");
        }
        // The conditional update clears the persistence context, but the DB lock remains held.
        Account locked = accounts.findForSettlement(accountId)
                .orElseThrow(() -> new ResourceNotFoundExcp("Account not found"));
        Account receiver = receiverId == null ? null : TransferBalances.lock(accounts, receiverId);
        TransactionDb settled = verifications.findByTransactionId(id)
                .orElseThrow(() -> new ResourceNotFoundExcp("Transaction not found"));
        TransferBalances.move(accounts, locked, receiver, transaction.getAmount(), settled.getSettledAt());
        if (receiver != null) {
            settled.setToAccount(receiver);
            verifications.save(settled);
        }
        AuditLog audit = new AuditLog();
        audit.setTransactionId(id); audit.setUserId(caller.userId()); audit.setRequestKey(key);
        audit.setAction(ACTION); audit.setOldState(TransactionState.HARD_HOLD.name());
        audit.setNewState(TransactionState.SETTLED.name()); audit.setCreatedAt(settled.getSettledAt());
        // Unique request key, debit, state and administrator identity commit atomically.
        audits.saveAndFlush(audit);
        return VerifiedTransactionResponse.from(settled);
    }

    /** Declining releases the reservation; held funds were never debited. */
    @Transactional
    public VerifiedTransactionResponse decline(Long id, LoginPrincipal caller, String key) {
        requireAdmin(caller);
        if (id == null || id <= 0) throw new IllegalArgumentException("A valid transaction is required");
        if (key == null || key.isBlank() || key.getBytes(StandardCharsets.UTF_8).length > 100)
            throw new IllegalArgumentException("Idempotency-Key is required and must not exceed 100 UTF-8 bytes");
        Long accountId = verifications.accountId(id)
                .orElseThrow(() -> new ResourceNotFoundExcp("Transaction not found"));
        accounts.findForSettlement(accountId).orElseThrow(() -> new ResourceNotFoundExcp("Account not found"));
        AuditLog receipt = audits.findByRequestKey(key).orElse(null);
        TransactionDb payment = verifications.findByTransactionId(id)
                .orElseThrow(() -> new ResourceNotFoundExcp("Transaction not found"));
        if (receipt != null) {
            if (!"ADMIN_DECLINED_CANCELLED".equals(receipt.getAction())
                    || !Objects.equals(receipt.getTransactionId(), id)
                    || !Objects.equals(receipt.getUserId(), caller.userId())
                    || payment.getState() != TransactionState.CANCELLED
                    || !key.equals(payment.getCancelIdempotencyKey()))
                throw new TransactionValidationException(409, "Idempotency-Key was already used for another operation");
            return VerifiedTransactionResponse.from(payment);
        }
        if (transactions.findByCancelIdempotencyKey(key).isPresent())
            throw new TransactionValidationException(409, "Idempotency-Key was already used for another cancellation");
        if (payment.getState() != TransactionState.HARD_HOLD)
            throw new TransactionValidationException(409, "Only a HARD_HOLD payment awaiting review may be declined");
        if (verifications.declineHeld(id, payment.getVersion(), key) != 1)
            throw new TransactionValidationException(409, "Payment changed concurrently; refresh before retrying");
        TransactionDb declined = verifications.findByTransactionId(id)
                .orElseThrow(() -> new ResourceNotFoundExcp("Transaction not found"));
        AuditLog audit = new AuditLog(); audit.setTransactionId(id); audit.setUserId(caller.userId());
        audit.setRequestKey(key); audit.setAction("ADMIN_DECLINED_CANCELLED");
        audit.setOldState(TransactionState.HARD_HOLD.name()); audit.setNewState(TransactionState.CANCELLED.name());
        audit.setCreatedAt(declined.getCancelledAt()); audits.saveAndFlush(audit);
        return VerifiedTransactionResponse.from(declined);
    }
}
