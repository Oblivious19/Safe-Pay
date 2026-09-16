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

    @Override
    @Transactional
    public TransactionDb initiate(Long accountId, Long beneficiaryId, BigDecimal amount, String purpose,
            String idempotencyKey, Long callerId) {
        validator.validateRequest(accountId, beneficiaryId, amount, purpose, idempotencyKey);
        validator.requireCustomer(callerId);
        Account account = validator.requireOwnedAccount(accountId, callerId);
        TransactionDb existing = transactionDao.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (existing != null) {
            if (!existing.getFromAccount().getUserId().equals(callerId)) {
                throw new ResourceNotFoundExcp("Transaction not found");
            }
            if (!Objects.equals(existing.getFromAccount().getAccountId(), accountId)
                    || !Objects.equals(existing.getBeneficiary().getBeneficiaryId(), beneficiaryId)
                    || existing.getAmount().compareTo(amount) != 0 || !Objects.equals(existing.getPurpose(), purpose)) {
                throw new TransactionValidationException(409, "Idempotency-Key was already used for a different request");
            }
            // Replay has no new debit: do not apply today's balance/eligibility to a past transaction.
            return existing;
        }
        Beneficiary beneficiary = validator.validateNewTransfer(account, beneficiaryId, callerId, amount);

        // The approved policy classifies only the validated amount. Context/history
        // must neither raise the tier nor prevent an otherwise valid assessment.
        RuleBasedRiskResult assessment = riskEngine.assessAmount(amount);
        LocalDateTime now = Objects.requireNonNull(transactionDao.currentDatabaseTime(accountId),
                "Database time is unavailable");
        TransactionDb transaction = new TransactionDb();
        transaction.setTransactionRef("TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        transaction.setIdempotencyKey(idempotencyKey);
        transaction.setFromAccount(account);
        transaction.setBeneficiary(beneficiary);
        transaction.setAmount(amount);
        transaction.setPurpose(purpose);
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
            account.setBalance(account.getBalance().subtract(amount));
            accountDao.save(account);
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
                .orElseThrow(() -> new ResourceNotFoundExcp("Transaction not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<TransactionDb> getTransactions(String state, String email) {
        if (state == null || state.isBlank()) {
            return transactionDao.findByFromAccountUserEmail(email);
        }
        return transactionDao.findByFromAccountUserEmailAndState(email, TransactionState.valueOf(state));
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
        LocalDateTime now = Objects.requireNonNull(transactionDao.currentDatabaseTime(accountId),
                "Database time is unavailable");
        if (transaction.getState() != TransactionState.PROTECTED || transaction.getProtectionExpiresAt() == null
                || !now.isBefore(transaction.getProtectionExpiresAt())) {
            throw new InvalidStateTransitionException("Only an active protected transaction can be cancelled");
        }
        int changed = transactionDao.cancelProtected(transactionId, transaction.getVersion(), idempotencyKey);
        if (changed == 0) {
            throw new InvalidStateTransitionException("Transaction was already changed by another request");
        }
        TransactionDb saved = getTransaction(transactionId, email);
        writeAudit(saved, saved.getFromAccount().getUserId(), "TRANSACTION_CANCELLED",
                TransactionState.PROTECTED.name(), TransactionState.CANCELLED.name());
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
