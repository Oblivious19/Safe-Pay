package com.ofss.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.ofss.beans.Account;
import com.ofss.beans.AuditLog;
import com.ofss.beans.Beneficiary;
import com.ofss.beans.RiskAssessment;
import com.ofss.beans.RiskTier;
import com.ofss.beans.TransactionDb;
import com.ofss.beans.TransactionState;
import com.ofss.beans.User;
import com.ofss.excp.IdempotencyConflictException;
import com.ofss.excp.InsufficientBalanceException;
import com.ofss.excp.InvalidStateTransitionException;
import com.ofss.excp.ResourceNotFoundExcp;
import com.ofss.repository.AccountDao;
import com.ofss.repository.AuditLogDao;
import com.ofss.repository.BeneficiaryDao;
import com.ofss.repository.TransactionDao;
import com.ofss.repository.UserDao;

@Service
public class TransactionServiceImpl implements TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionServiceImpl.class);
    private static final BigDecimal MINIMUM_BALANCE = new BigDecimal("5000.00");

    private final TransactionDao transactionDao;
    private final AccountDao accountDao;
    private final BeneficiaryDao beneficiaryDao;
    private final AuditLogDao auditLogDao;
    private final UserDao userDao;
    private final TransactionTemplate settlementTransaction;
    private final AmountRiskEngine riskEngine = new AmountRiskEngine();

    public TransactionServiceImpl(TransactionDao transactionDao, AccountDao accountDao,
            BeneficiaryDao beneficiaryDao, AuditLogDao auditLogDao, UserDao userDao,
            PlatformTransactionManager transactionManager) {
        this.transactionDao = transactionDao;
        this.accountDao = accountDao;
        this.beneficiaryDao = beneficiaryDao;
        this.auditLogDao = auditLogDao;
        this.userDao = userDao;
        this.settlementTransaction = new TransactionTemplate(transactionManager);
        this.settlementTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    @Transactional
    public TransactionDb initiate(Long accountId, Long beneficiaryId, BigDecimal amount, String purpose,
            String idempotencyKey, String email) {
        validateIdempotencyKey(idempotencyKey);
        BigDecimal paymentAmount = normalizeAmount(amount);
        String paymentPurpose = purpose == null || purpose.isEmpty() ? null : purpose;
        if (paymentPurpose != null && paymentPurpose.length() > 255) {
            throw new IllegalArgumentException("Purpose must be at most 255 characters");
        }

        // Lock order is always user -> account. Same-user retries wait for the first commit.
        User user = lockUser(email);
        TransactionDb existing = transactionDao.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (existing != null) {
            requireOwner(existing, user.getUserId());
            if (!Objects.equals(existing.getFromAccount().getAccountId(), accountId)
                    || !Objects.equals(existing.getBeneficiary().getBeneficiaryId(), beneficiaryId)
                    || existing.getAmount().compareTo(paymentAmount) != 0
                    || !Objects.equals(existing.getPurpose(), paymentPurpose)) {
                throw new IdempotencyConflictException("Idempotency-Key was already used for a different payment");
            }
            return existing;
        }

        Account account = lockAccount(accountId);
        if (!Objects.equals(account.getUser().getUserId(), user.getUserId())) {
            throw new ResourceNotFoundExcp("Account not found");
        }
        Beneficiary beneficiary = beneficiaryDao.findByBeneficiaryIdAndAccountUserEmail(beneficiaryId, email)
                .orElseThrow(() -> new ResourceNotFoundExcp("Beneficiary not found"));
        if (!Objects.equals(beneficiary.getAccount().getAccountId(), accountId)) {
            throw new IllegalArgumentException("Beneficiary does not belong to the selected account");
        }
        if (!"ACTIVE".equals(beneficiary.getStatus())) {
            throw new IllegalArgumentException("Beneficiary is inactive");
        }

        BigDecimal available = account.getBalance().subtract(transactionDao.sumHeldAmount(accountId));
        if (available.subtract(paymentAmount).compareTo(MINIMUM_BALANCE) < 0) {
            throw new InsufficientBalanceException("Insufficient available balance after pending payments; "
                    + "a minimum balance of ₹5,000 must remain");
        }

        RiskAssessment assessment = riskEngine.assess(paymentAmount);
        LocalDateTime now = transactionDao.currentDatabaseTime(accountId);
        TransactionDb transaction = new TransactionDb();
        transaction.setTransactionRef("TXN-" + UUID.randomUUID().toString().toUpperCase());
        transaction.setIdempotencyKey(idempotencyKey);
        transaction.setFromAccount(account);
        transaction.setBeneficiary(beneficiary);
        transaction.setAmount(paymentAmount);
        transaction.setPurpose(paymentPurpose);
        transaction.setCreatedAt(now);
        transaction.setState(TransactionState.CREATED);

        transition(transaction, TransactionState.AUTHORIZED);
        transaction.setAuthorizedAt(now);
        transaction.setRiskTier(assessment.riskTier());
        transaction.setProtectionSeconds(assessment.protectionSeconds());
        transaction.setAuthenticationRequired(assessment.authenticationRequired() ? "Y" : "N");
        transaction.setRiskReason(assessment.reason());
        transition(transaction, TransactionState.RISK_ASSESSED);

        if (assessment.riskTier() == RiskTier.LOW) {
            transition(transaction, TransactionState.SETTLED);
            transaction.setSettledAt(now);
            account.setBalance(account.getBalance().subtract(paymentAmount));
            accountDao.save(account);
        } else if (assessment.riskTier() == RiskTier.HARD_HOLD) {
            transition(transaction, TransactionState.HARD_HOLD);
        } else {
            transition(transaction, TransactionState.PROTECTED);
            transaction.setProtectionExpiresAt(now.plusSeconds(assessment.protectionSeconds()));
        }

        TransactionDb saved = transactionDao.saveAndFlush(transaction);
        writeAudit(saved, user.getUserId(), "TRANSACTION_INITIATED", null, saved.getState().name(), now);
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
            return transactionDao.findByFromAccountUserEmailOrderByCreatedAtDescTransactionIdDesc(email);
        }
        return transactionDao.findByFromAccountUserEmailAndStateOrderByCreatedAtDescTransactionIdDesc(
                email, TransactionState.valueOf(state));
    }

    @Override
    @Transactional
    public TransactionDb cancel(Long transactionId, String idempotencyKey, String email) {
        validateIdempotencyKey(idempotencyKey);
        User user = lockUser(email);
        Long userId = user.getUserId();
        TransactionDb existing = transactionDao.findByCancelIdempotencyKey(idempotencyKey).orElse(null);
        if (existing != null) {
            requireOwner(existing, userId);
            if (!Objects.equals(existing.getTransactionId(), transactionId)) {
                throw new IdempotencyConflictException("Idempotency-Key was already used to cancel a different payment");
            }
            if (existing.getState() != TransactionState.CANCELLED) {
                throw new InvalidStateTransitionException("Cancellation has not completed");
            }
            return existing;
        }

        Long accountId = transactionDao.findAccountId(transactionId)
                .orElseThrow(() -> new ResourceNotFoundExcp("Transaction not found"));
        Account account = lockAccount(accountId);
        if (!Objects.equals(account.getUser().getUserId(), userId)) {
            throw new ResourceNotFoundExcp("Transaction not found");
        }
        TransactionDb transaction = getTransaction(transactionId, email);
        if (transaction.getState() != TransactionState.PROTECTED || transaction.getProtectionExpiresAt() == null) {
            throw new InvalidStateTransitionException("Only an active protected transaction can be cancelled");
        }
        // The database checks its clock at the conditional update, including time spent waiting for locks.
        int changed = transactionDao.cancelProtected(transactionId, transaction.getVersion(), idempotencyKey);
        if (changed == 0) {
            throw new InvalidStateTransitionException("Protection window expired or transaction was already changed");
        }
        TransactionDb cancelled = getTransaction(transactionId, email);
        writeAudit(cancelled, userId, "TRANSACTION_CANCELLED", TransactionState.PROTECTED.name(),
                TransactionState.CANCELLED.name(), cancelled.getCancelledAt());
        return cancelled;
    }

    @Override
    public void releaseExpiredTransactions() {
        int released = 0;
        // Only scalar IDs are loaded here. Each payment commits or rolls back independently.
        for (Long transactionId : transactionDao.findExpiredTransactionIds()) {
            try {
                if (Boolean.TRUE.equals(settlementTransaction.execute(status -> settleExpiredTransaction(transactionId)))) {
                    released++;
                }
            } catch (RuntimeException exception) {
                log.warn("Could not settle protected transaction {}; it remains available for retry", transactionId, exception);
            }
        }
        log.debug("Automatically settled {} protected transactions", released);
    }

    private boolean settleExpiredTransaction(Long transactionId) {
        Long accountId = transactionDao.findAccountId(transactionId).orElse(null);
        if (accountId == null) {
            return false;
        }
        Account account = lockAccount(accountId);
        TransactionDb transaction = transactionDao.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundExcp("Transaction not found"));
        if (transaction.getState() != TransactionState.PROTECTED) {
            return false;
        }
        BigDecimal balanceAfterSettlement = account.getBalance().subtract(transaction.getAmount());
        if (balanceAfterSettlement.compareTo(MINIMUM_BALANCE) < 0) {
            throw new InsufficientBalanceException("Protected payment cannot settle below the minimum balance");
        }
        Long userId = account.getUser().getUserId();
        if (transactionDao.settleProtected(transactionId, transaction.getVersion()) == 0) {
            return false;
        }
        // The bulk update clears the persistence context, so reload the still-locked account.
        Account settledAccount = lockAccount(accountId);
        settledAccount.setBalance(balanceAfterSettlement);
        accountDao.save(settledAccount);
        TransactionDb settled = transactionDao.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundExcp("Transaction not found"));
        writeAudit(settled, userId, "TRANSACTION_AUTO_SETTLED", TransactionState.PROTECTED.name(),
                TransactionState.SETTLED.name(), settled.getSettledAt());
        return true;
    }

    private User lockUser(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("User email is required");
        }
        return userDao.findByEmailForUpdate(email)
                .orElseThrow(() -> new ResourceNotFoundExcp("User not found"));
    }

    private Account lockAccount(Long accountId) {
        if (accountId == null) {
            throw new IllegalArgumentException("Account ID is required");
        }
        return accountDao.findByAccountIdForUpdate(accountId)
                .orElseThrow(() -> new ResourceNotFoundExcp("Account not found"));
    }

    private void requireOwner(TransactionDb transaction, Long userId) {
        if (!Objects.equals(transaction.getFromAccount().getUser().getUserId(), userId)) {
            throw new IdempotencyConflictException("Idempotency-Key is already in use");
        }
    }

    private void validateIdempotencyKey(String key) {
        if (key == null || key.isBlank() || key.length() > 100) {
            throw new IllegalArgumentException("Idempotency-Key must contain between 1 and 100 characters");
        }
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        try {
            BigDecimal normalized = amount.setScale(2, RoundingMode.UNNECESSARY);
            if (normalized.precision() > 18) {
                throw new IllegalArgumentException("Amount must have at most 16 integer digits");
            }
            return normalized;
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Amount must have at most two decimal places");
        }
    }

    private void transition(TransactionDb transaction, TransactionState nextState) {
        TransactionState currentState = transaction.getState();
        RiskTier tier = transaction.getRiskTier();
        boolean valid = (currentState == TransactionState.CREATED && nextState == TransactionState.AUTHORIZED)
                || (currentState == TransactionState.AUTHORIZED && nextState == TransactionState.RISK_ASSESSED)
                || (currentState == TransactionState.RISK_ASSESSED
                        && ((nextState == TransactionState.SETTLED && tier == RiskTier.LOW)
                            || (nextState == TransactionState.PROTECTED && (tier == RiskTier.MEDIUM || tier == RiskTier.HIGH))
                            || (nextState == TransactionState.HARD_HOLD && tier == RiskTier.HARD_HOLD)));
        if (!valid) {
            throw new InvalidStateTransitionException("Invalid transition from " + currentState + " to " + nextState);
        }
        transaction.setState(nextState);
    }

    private void writeAudit(TransactionDb transaction, Long userId, String action, String oldState,
            String newState, LocalDateTime timestamp) {
        AuditLog audit = new AuditLog();
        audit.setTransactionId(transaction.getTransactionId());
        audit.setUserId(userId);
        audit.setAction(action);
        audit.setOldState(oldState);
        audit.setNewState(newState);
        audit.setCreatedAt(timestamp);
        auditLogDao.save(audit);
    }
}
