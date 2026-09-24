package com.ofss.services;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.Objects;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ofss.beans.Account;
import com.ofss.beans.AuditLog;
import com.ofss.beans.Beneficiary;
import com.ofss.beans.RuleBasedRiskResult;
import com.ofss.beans.AssessmentRiskTier;
import com.ofss.beans.RiskTier;
import com.ofss.beans.TransactionDb;
import com.ofss.beans.TransactionState;
import com.ofss.beans.PaymentCategory;
import com.ofss.excp.TransactionValidationException;
import com.ofss.excp.InvalidStateTransitionException;
import com.ofss.excp.ResourceNotFoundExcp;
import com.ofss.repository.AccountDao;
import com.ofss.repository.AuditLogDao;
import com.ofss.repository.TransactionDao;

@Service
public class TransactionServiceImpl implements TransactionService {
    private static final Logger log = LoggerFactory.getLogger(TransactionServiceImpl.class);

    private final TransactionDao transactionDao;
    private final AccountDao accountDao;
    private final TransactionPreRiskValidator validator;
    private final AuditLogDao auditLogDao;
    private final RiskAssessmentEngine riskEngine;
    private final ExpiredTransactionSettlementService settlements;

    @Autowired
    public TransactionServiceImpl(TransactionDao transactionDao, AccountDao accountDao,
            TransactionPreRiskValidator validator, AuditLogDao auditLogDao,
            ExpiredTransactionSettlementService settlements) {
        this(transactionDao, accountDao, validator, auditLogDao, new RiskAssessmentEngine(), settlements);
    }

    TransactionServiceImpl(TransactionDao transactionDao, AccountDao accountDao,
            TransactionPreRiskValidator validator, AuditLogDao auditLogDao, RiskAssessmentEngine riskEngine,
            ExpiredTransactionSettlementService settlements) {
        this.transactionDao = transactionDao;
        this.accountDao = accountDao;
        this.validator = validator;
        this.auditLogDao = auditLogDao;
        this.riskEngine = riskEngine;
        this.settlements = settlements;
    }

    /** Keep existing internal callers inside the same transactional boundary. */
    @Override
    @Transactional
    public TransactionDb initiate(Long accountId, Long beneficiaryId, BigDecimal amount, String purpose,
            String idempotencyKey, Long callerId) {
        return initiate(accountId, beneficiaryId, amount, purpose, idempotencyKey, callerId, null);
    }

    @Override
    @Transactional
    public TransactionDb initiate(Long accountId, Long beneficiaryId, BigDecimal amount, String purpose,
            String idempotencyKey, Long callerId, PaymentCategory category) {
        // Normalize only the new category-specific note, not historical uncategorized requests.
        if (category == PaymentCategory.OTHERS && purpose != null) purpose = purpose.trim();
        validator.validateRequest(accountId, beneficiaryId, amount, purpose, idempotencyKey);
        validator.requireCustomer(callerId);
        Long receiverId = accountDao.findRecipientId(beneficiaryId, accountId, callerId).orElse(null);
        Account account = validator.requireOwnedAccount(accountId, callerId, receiverId);
        TransactionDb existing = transactionDao.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (existing != null) {
            if (!existing.getFromAccount().getUserId().equals(callerId)) {
                throw new ResourceNotFoundExcp("Transaction not found");
            }
            if (!Objects.equals(existing.getFromAccount().getAccountId(), accountId)
                    || !Objects.equals(existing.getBeneficiary().getBeneficiaryId(), beneficiaryId)
                    || existing.getAmount().compareTo(amount) != 0 || !Objects.equals(existing.getPurpose(), purpose)
                    || existing.getCategory() != category) {
                throw new TransactionValidationException(409, "Idempotency-Key was already used for a different request");
            }
            // Replay has no new debit: do not apply today's balance/eligibility to a past transaction.
            return existing;
        }
        // Historical idempotency replays above remain valid without invented metadata.
        PaymentCategory.requireForNewPayment(amount, category, purpose);
        Beneficiary beneficiary = validator.validateNewTransfer(account, beneficiaryId, callerId, amount);
        Account receiver = receiverId == null ? null : TransferBalances.lock(accountDao, receiverId);
        TransferBalances.validateReceiver(account, receiver, amount);

        // The approved policy classifies only the validated amount. Context/history
        // must neither raise the tier nor prevent an otherwise valid assessment.
        RuleBasedRiskResult assessment = riskEngine.assessAmount(amount);
        LocalDateTime now = Objects.requireNonNull(transactionDao.currentDatabaseTime(accountId),
                "Database time is unavailable");
        TransactionDb transaction = new TransactionDb();
        transaction.setTransactionRef("TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        transaction.setIdempotencyKey(idempotencyKey);
        transaction.setFromAccount(account);
        transaction.setToAccount(receiver);
        transaction.setBeneficiary(beneficiary);
        transaction.setAmount(amount);
        transaction.setPurpose(purpose);
        transaction.setCategory(category);
        transaction.setCreatedAt(now);
        transaction.setState(TransactionState.CREATED);

        transition(transaction, TransactionState.AUTHORIZED);
        transaction.setAuthorizedAt(now);
        transaction.setRiskTier(RiskTier.valueOf(assessment.riskTier().name()));
        transaction.setProtectionSeconds(assessment.protectionDurationSeconds());
        transaction.setAuthenticationRequired(assessment.authenticationRequired());
        transaction.setRiskReason(assessment.reason());
        transition(transaction, TransactionState.RISK_ASSESSED);

        if (assessment.riskTier() == AssessmentRiskTier.LOW) {
            transition(transaction, TransactionState.SETTLED);
            transaction.setSettledAt(now);
            transaction.setReleasedAt(now);
            TransferBalances.move(accountDao, account, receiver, amount, now);
        } else if (assessment.authenticationRequired()) {
            transition(transaction, TransactionState.HARD_HOLD);
        } else {
            transition(transaction, TransactionState.PROTECTED);
            transaction.setProtectionExpiresAt(now.plusSeconds(assessment.protectionDurationSeconds()));
        }

        TransactionDb saved = transactionDao.save(transaction);
        writeAudit(saved, account.getUser().getUserId(), "TRANSACTION_INITIATED", null, saved.getState().name());
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public TransactionDb getTransaction(Long transactionId, String email) {
        return transactionDao.findByTransactionIdAndFromAccountUserEmail(transactionId, email)
                .or(() -> transactionDao.findReceivedTransaction(transactionId, email))
                .orElseThrow(() -> new ResourceNotFoundExcp("Transaction not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<TransactionDb> getTransactions(String state, String email) {
        if (state == null || state.isBlank()) {
            return java.util.stream.Stream.concat(transactionDao.findByFromAccountUserEmail(email).stream(),
                    transactionDao.findReceivedTransactions(email).stream()).distinct().toList();
        }
        TransactionState selected = TransactionState.valueOf(state);
        return java.util.stream.Stream.concat(transactionDao.findByFromAccountUserEmailAndState(email, selected).stream(),
                selected == TransactionState.SETTLED ? transactionDao.findReceivedTransactions(email).stream()
                        : java.util.stream.Stream.empty()).distinct().toList();
    }

    @Override
    @Transactional
    public TransactionDb cancel(Long transactionId, String idempotencyKey, String email) {
        if (idempotencyKey == null || idempotencyKey.isBlank()
                || idempotencyKey.getBytes(StandardCharsets.UTF_8).length > 100) {
            throw new IllegalArgumentException("Idempotency-Key must contain 1 to 100 UTF-8 bytes");
        }
        Long accountId = transactionDao.findOwnedAccountId(transactionId, email)
                .orElseThrow(() -> new ResourceNotFoundExcp("Transaction not found"));
        accountDao.findForSettlement(accountId)
                .orElseThrow(() -> new ResourceNotFoundExcp("Account not found"));
        TransactionDb replay = transactionDao.findByCancelIdempotencyKey(idempotencyKey).orElse(null);
        if (replay != null && !Objects.equals(replay.getTransactionId(), transactionId)) {
            throw new TransactionValidationException(409, "Idempotency-Key was already used for another cancellation");
        }
        TransactionDb transaction = getTransaction(transactionId, email);
        if (transaction.getState() == TransactionState.CANCELLED) {
            return transaction;
        }
        TransactionState previousState = transaction.getState();
        int changed;
        if (previousState == TransactionState.HARD_HOLD) {
            // The source-account lock is shared with admin approval. Only one decision can win.
            changed = transactionDao.cancelHeld(transactionId, transaction.getVersion(), idempotencyKey);
        } else if (previousState == TransactionState.PROTECTED) {
            LocalDateTime now = Objects.requireNonNull(transactionDao.currentDatabaseTime(accountId),
                    "Database time is unavailable");
            if (transaction.getProtectionExpiresAt() == null || !now.isBefore(transaction.getProtectionExpiresAt())) {
                throw new InvalidStateTransitionException("The protection window has ended. This payment can no longer be cancelled");
            }
            changed = transactionDao.cancelProtected(transactionId, transaction.getVersion(), idempotencyKey);
        } else {
            throw new InvalidStateTransitionException("Only a payment still in protection or awaiting administrator approval can be cancelled");
        }
        if (changed == 0) {
            throw new InvalidStateTransitionException("Transaction was already changed by another request");
        }
        TransactionDb saved = getTransaction(transactionId, email);
        writeAudit(saved, saved.getFromAccount().getUserId(), "TRANSACTION_CANCELLED",
                previousState.name(), TransactionState.CANCELLED.name());
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public LocalDateTime currentDatabaseTime(Long accountId) {
        return Objects.requireNonNull(transactionDao.currentDatabaseTime(accountId), "Database time is unavailable");
    }

    @Override
    public void releaseExpiredTransactions() {
        int released = 0;
        for (Long id : transactionDao.findExpiredTransactionIds()) {
            try {
                if (settlements.settle(id)) released++;
            } catch (RuntimeException exception) {
                // The proxied worker has already rolled back only this payment.
                log.warn("Scheduled settlement failed for transaction {}; it remains retryable", id, exception);
            }
        }
        log.debug("Automatically settled {} protected transactions", released);
    }

    private void transition(TransactionDb transaction, TransactionState nextState) {
        TransactionState currentState = transaction.getState();
        boolean valid = (currentState == TransactionState.CREATED && nextState == TransactionState.AUTHORIZED)
                || (currentState == TransactionState.AUTHORIZED && nextState == TransactionState.RISK_ASSESSED)
                || (currentState == TransactionState.RISK_ASSESSED && (nextState == TransactionState.SETTLED
                        || nextState == TransactionState.PROTECTED || nextState == TransactionState.HARD_HOLD));
        if (!valid) {
            throw new InvalidStateTransitionException("Invalid transition from " + currentState + " to " + nextState);
        }
        transaction.setState(nextState);
    }

    private void writeAudit(TransactionDb transaction, Long userId, String action, String oldState, String newState) {
        AuditLog audit = new AuditLog();
        audit.setTransactionId(transaction.getTransactionId());
        audit.setUserId(userId);
        audit.setAction(action);
        audit.setOldState(oldState);
        audit.setNewState(newState);
        audit.setCreatedAt(LocalDateTime.now());
        auditLogDao.save(audit);
    }
}
