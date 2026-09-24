package com.ofss.services;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import com.ofss.beans.*;
import com.ofss.excp.InsufficientBalanceException;
import com.ofss.excp.InvalidStateTransitionException;
import com.ofss.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Each invocation crosses a Spring proxy and commits or rolls back one payment. */
@Service
public class ExpiredTransactionSettlementService {
    private static final BigDecimal MINIMUM = new BigDecimal("5000.00");
    private final TransactionDao transactions;
    private final AccountDao accounts;
    private final AuditLogDao audits;

    public ExpiredTransactionSettlementService(TransactionDao transactions, AccountDao accounts, AuditLogDao audits) {
        this.transactions = transactions;
        this.accounts = accounts;
        this.audits = audits;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean settle(Long id) {
        Long accountId = transactions.findAccountId(id).orElse(null);
        if (accountId == null) return false;
        Long receiverId = accounts.findPaymentRecipientId(id).orElse(null);
        Account account = TransferBalances.lockPair(accounts, accountId, receiverId);
        TransactionDb transaction = transactions.findById(id).orElseThrow();
        if (transaction.getState() != TransactionState.PROTECTED) return false;
        if (account.getStatus() != AccountStatus.ACTIVE)
            throw new InvalidStateTransitionException("Source account must be ACTIVE");
        LocalDateTime now = Objects.requireNonNull(transactions.currentDatabaseTime(accountId));
        if (transaction.getProtectionExpiresAt() == null) {
            throw new InvalidStateTransitionException("Protected transaction has no expiry");
        }
        if (now.isBefore(transaction.getProtectionExpiresAt())) return false;
        if (transaction.getAmount() == null || transaction.getAmount().signum() <= 0) {
            throw new InvalidStateTransitionException("Transaction amount is invalid");
        }
        BigDecimal reserved = transactions.pendingAmount(accountId,
                List.of(TransactionState.PROTECTED, TransactionState.HARD_HOLD));
        if (account.getBalance() == null || reserved == null
                || reserved.compareTo(transaction.getAmount()) < 0
                || account.getBalance().subtract(reserved).compareTo(MINIMUM) < 0) {
            throw new InsufficientBalanceException("Settlement must preserve other holds and the minimum balance");
        }
        Long ownerId = account.getUserId();
        BigDecimal amount = transaction.getAmount();
        if (transactions.settleProtected(id, transaction.getVersion()) != 1) return false;
        // The bulk update cleared managed entities; reload under the same database lock.
        Account savedAccount = accounts.findForSettlement(accountId).orElseThrow();
        Account receiver = receiverId == null ? null : TransferBalances.lock(accounts, receiverId);
        TransferBalances.move(accounts, savedAccount, receiver, amount, now);
        if (receiver != null) {
            TransactionDb settled = transactions.findById(id).orElseThrow();
            settled.setToAccount(receiver);
            transactions.save(settled);
        }
        AuditLog audit = new AuditLog();
        audit.setTransactionId(id);
        audit.setUserId(ownerId);
        audit.setAction("TRANSACTION_AUTO_SETTLED");
        audit.setOldState("PROTECTED");
        audit.setNewState("SETTLED");
        audit.setCreatedAt(now);
        audits.save(audit);
        return true;
    }
}
