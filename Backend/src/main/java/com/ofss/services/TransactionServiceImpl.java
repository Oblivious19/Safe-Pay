package com.ofss.services;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ofss.beans.Account;
import com.ofss.beans.AuditLog;
import com.ofss.beans.Beneficiary;
import com.ofss.beans.RiskAssessment;
import com.ofss.beans.RiskTier;
import com.ofss.beans.TransactionDb;
import com.ofss.beans.TransactionState;
import com.ofss.excp.InsufficientBalanceException;
import com.ofss.excp.InvalidStateTransitionException;
import com.ofss.excp.ResourceNotFoundExcp;
import com.ofss.repository.AccountDao;
import com.ofss.repository.AuditLogDao;
import com.ofss.repository.BeneficiaryDao;
import com.ofss.repository.TransactionDao;

@Service
public class TransactionServiceImpl implements TransactionService {

    private static final BigDecimal MINIMUM_BALANCE = new BigDecimal("5000.00");

    private final TransactionDao transactionDao;
    private final AccountDao accountDao;
    private final BeneficiaryDao beneficiaryDao;
    private final AuditLogDao auditLogDao;
    private final AmountRiskEngine riskEngine = new AmountRiskEngine();

    public TransactionServiceImpl(TransactionDao transactionDao, AccountDao accountDao,
            BeneficiaryDao beneficiaryDao, AuditLogDao auditLogDao) {
        this.transactionDao = transactionDao;
        this.accountDao = accountDao;
        this.beneficiaryDao = beneficiaryDao;
        this.auditLogDao = auditLogDao;
    }

    @Override
    @Transactional
    public TransactionDb initiate(Long accountId, Long beneficiaryId, BigDecimal amount, String purpose,
            String idempotencyKey, String email) {
        TransactionDb existing = transactionDao.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (existing != null) {
            if (!existing.getFromAccount().getUser().getEmail().equals(email)) {
                throw new ResourceNotFoundExcp("Transaction not found");
            }
            return existing;
        }

        Account account = accountDao.findByAccountIdAndUserEmail(accountId, email)
                .orElseThrow(() -> new ResourceNotFoundExcp("Account not found"));
        Beneficiary beneficiary = beneficiaryDao.findByBeneficiaryIdAndAccountUserEmail(beneficiaryId, email)
                .orElseThrow(() -> new ResourceNotFoundExcp("Beneficiary not found"));

        if (!"ACTIVE".equals(beneficiary.getStatus())) {
            throw new IllegalArgumentException("Beneficiary is inactive");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        if (account.getBalance().subtract(amount).compareTo(MINIMUM_BALANCE) < 0) {
            throw new InsufficientBalanceException("Minimum balance of ₹5,000 must remain in the account");
        }

        RiskAssessment assessment = riskEngine.assess(amount);
        LocalDateTime now = LocalDateTime.now();
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
        transaction.setRiskTier(assessment.riskTier());
        transaction.setProtectionSeconds(assessment.protectionSeconds());
        transaction.setAuthenticationRequired(assessment.authenticationRequired() ? "Y" : "N");
        transaction.setRiskReason(assessment.reason());
        transition(transaction, TransactionState.RISK_ASSESSED);

        if (assessment.riskTier() == RiskTier.LOW) {
            transition(transaction, TransactionState.SETTLED);
            transaction.setSettledAt(now);
            account.setBalance(account.getBalance().subtract(amount));
            accountDao.save(account);
        } else if (assessment.riskTier() == RiskTier.HARD_HOLD) {
            transition(transaction, TransactionState.HARD_HOLD);
        } else {
            transition(transaction, TransactionState.PROTECTED);
            transaction.setProtectionExpiresAt(now.plusSeconds(assessment.protectionSeconds()));
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
        TransactionDb transaction = getTransaction(transactionId, email);
        if (transaction.getState() == TransactionState.CANCELLED) {
            return transaction;
        }
        if (transaction.getState() != TransactionState.PROTECTED || !LocalDateTime.now().isBefore(transaction.getProtectionExpiresAt())) {
            throw new InvalidStateTransitionException("Only an active protected transaction can be cancelled");
        }
        int changed = transactionDao.cancelProtected(transactionId, transaction.getVersion(), LocalDateTime.now());
        if (changed == 0) {
            throw new InvalidStateTransitionException("Transaction was already changed by another request");
        }
        writeAudit(transaction, transaction.getFromAccount().getUser().getUserId(), "TRANSACTION_CANCELLED",
                TransactionState.PROTECTED.name(), TransactionState.CANCELLED.name());
        return getTransaction(transactionId, email);
    }

    @Override
    @Transactional
    public void releaseExpiredTransactions() {
        for (TransactionDb transaction : transactionDao.findByStateAndProtectionExpiresAtLessThanEqual(
                TransactionState.PROTECTED, LocalDateTime.now())) {
            int changed = transactionDao.settleProtected(transaction.getTransactionId(), transaction.getVersion(), LocalDateTime.now());
            if (changed == 1) {
                Account account = transaction.getFromAccount();
                account.setBalance(account.getBalance().subtract(transaction.getAmount()));
                accountDao.save(account);
                writeAudit(transaction, account.getUser().getUserId(), "TRANSACTION_AUTO_SETTLED",
                        TransactionState.PROTECTED.name(), TransactionState.SETTLED.name());
            }
        }
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
