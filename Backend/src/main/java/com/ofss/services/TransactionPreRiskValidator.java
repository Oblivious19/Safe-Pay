package com.ofss.services;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import com.ofss.beans.*;
import com.ofss.excp.InsufficientBalanceException;
import com.ofss.excp.ResourceNotFoundExcp;
import com.ofss.excp.TransactionValidationException;
import com.ofss.repository.*;
import org.springframework.stereotype.Component;

/** Checks only: never scores risk, changes balances, or creates transactions. */
@Component
public class TransactionPreRiskValidator {
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999999999.99");
    private final UserDao users;
    private final AccountDao accounts;
    private final BeneficiaryDao beneficiaries;
    private final TransactionDao transactions;

    public TransactionPreRiskValidator(UserDao users, AccountDao accounts, BeneficiaryDao beneficiaries,
            TransactionDao transactions) {
        this.users = users;
        this.accounts = accounts;
        this.beneficiaries = beneficiaries;
        this.transactions = transactions;
    }

    public void validateRequest(Long accountId, Long beneficiaryId, BigDecimal amount, String purpose, String key) {
        if (accountId == null || accountId <= 0 || beneficiaryId == null || beneficiaryId <= 0) {
            throw new IllegalArgumentException("Source account and beneficiary IDs must be positive");
        }
        if (amount == null || amount.signum() <= 0) throw new IllegalArgumentException("Amount must be greater than zero");
        if (amount.scale() > 2 || amount.compareTo(MAX_AMOUNT) > 0) {
            throw new IllegalArgumentException("Amount must have at most 16 integer digits and 2 decimal places");
        }
        // Oracle columns may use BYTE semantics; do not let multibyte text overflow them.
        if (purpose != null && purpose.getBytes(StandardCharsets.UTF_8).length > 255) {
            throw new IllegalArgumentException("Purpose must not exceed 255 UTF-8 bytes");
        }
        if (key == null || key.isBlank() || key.getBytes(StandardCharsets.UTF_8).length > 100) {
            throw new IllegalArgumentException("Idempotency-Key is required and must not exceed 100 UTF-8 bytes");
        }
    }

    public void requireCustomer(Long callerId) {
        if (callerId == null) throw new TransactionValidationException(401, "Login is required");
        User user = users.findById(callerId)
                .orElseThrow(() -> new TransactionValidationException(401, "Authenticated user no longer exists; log in again"));
        if (user.getRole() == null || !"CUSTOMER".equals(user.getRole().getRoleName())
                || user.getStatus() != UserStatus.ACTIVE) {
            throw new TransactionValidationException(403, "An active CUSTOMER is required to create a transaction");
        }
    }

    public Account requireOwnedAccount(Long accountId, Long callerId) {
        return accounts.findForTransaction(accountId, callerId)
                .orElseThrow(() -> new ResourceNotFoundExcp("Account not found"));
    }

    public Beneficiary validateNewTransfer(Account account, Long beneficiaryId, Long callerId, BigDecimal amount) {
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new TransactionValidationException(409, "Source account must be ACTIVE");
        }
        Beneficiary beneficiary = beneficiaries.findByBeneficiaryIdAndAccountUserUserId(beneficiaryId, callerId)
                .orElseThrow(() -> new ResourceNotFoundExcp("Beneficiary not found"));
        if (beneficiary.getAccount() == null
                || !account.getAccountId().equals(beneficiary.getAccount().getAccountId())) {
            throw new TransactionValidationException(409, "Beneficiary must belong to the selected source account");
        }
        if (!"ACTIVE".equals(beneficiary.getStatus())) {
            throw new TransactionValidationException(409, "Beneficiary must be ACTIVE");
        }
        BigDecimal pending = transactions.pendingAmount(account.getAccountId(),
                List.of(TransactionState.PROTECTED, TransactionState.HARD_HOLD));
        if (account.getBalance() == null || pending == null || pending.signum() < 0) {
            throw new TransactionValidationException(409, "Available balance could not be established");
        }
        BigDecimal available = account.getBalance().subtract(pending);
        if (available.compareTo(amount) < 0) throw new InsufficientBalanceException("Insufficient available balance. Account balance: INR " + account.getBalance().toPlainString() + "; reserved by pending payments: INR " + pending.toPlainString() + "; available to transfer: INR " + available.max(BigDecimal.ZERO).toPlainString());
        return beneficiary;
    }
}
