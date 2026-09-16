package com.ofss.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import com.ofss.beans.*;
import com.ofss.beans.AdminCreditDtos.*;
import com.ofss.excp.ResourceNotFoundExcp;
import com.ofss.excp.TransactionValidationException;
import com.ofss.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminCreditService {
    private static final BigDecimal MAX = new BigDecimal("9999999999999999.99");
    private static final String DESCRIPTION = "Simulated bank interest";
    private final AdminAccountRepository accounts;
    private final AccountDao customerAccounts;
    private final UserDao users;
    private final AuditLogDao audits;
    public AdminCreditService(AdminAccountRepository accounts, AccountDao customerAccounts, UserDao users, AuditLogDao audits) {
        this.accounts = accounts; this.customerAccounts = customerAccounts; this.users = users; this.audits = audits;
    }

    @Transactional(readOnly = true)
    public List<AccountView> accounts(Long userId) {
        if (userId == null || userId <= 0) throw new IllegalArgumentException("User ID must be positive");
        if (!users.existsById(userId)) throw new ResourceNotFoundExcp("User not found");
        return customerAccounts.findByUserUserIdOrderByAccountId(userId).stream()
                .map(a -> new AccountView(a.getAccountId(), a.getAccountNumber(), a.getAccountType().name(),
                        a.getStatus().name(), a.getBalance().setScale(2).toPlainString())).toList();
    }

    @Transactional
    public Receipt credit(Long accountId, Long actorId, BigDecimal amount, String requestKey) {
        if (accountId == null || accountId <= 0 || actorId == null || actorId <= 0) {
            throw new IllegalArgumentException("Valid account and authenticated administrator are required");
        }
        if (requestKey == null || requestKey.isBlank() || requestKey.getBytes(StandardCharsets.UTF_8).length > 100) {
            throw new IllegalArgumentException("Idempotency-Key must contain 1 to 100 UTF-8 bytes");
        }
        if (amount == null || amount.signum() <= 0 || amount.scale() > 2 || amount.compareTo(MAX) > 0) {
            throw new IllegalArgumentException("Enter a positive amount with at most two decimal places");
        }
        amount = amount.setScale(2, RoundingMode.UNNECESSARY);
        // All credits and payments lock the same account row before reading/changing its balance.
        Account account = accounts.lockAccount(accountId).orElseThrow(() -> new ResourceNotFoundExcp("Account not found"));
        AuditLog replay = audits.findByRequestKey(requestKey).orElse(null);
        if (replay != null) return replay(replay, accountId, actorId, amount);
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new TransactionValidationException(409, "Only ACTIVE accounts can receive credits");
        }
        BigDecimal before = account.getBalance();
        if (before == null || before.signum() < 0 || before.scale() > 2) {
            throw new TransactionValidationException(409, "Account balance could not be established");
        }
        before = before.setScale(2, RoundingMode.UNNECESSARY);
        BigDecimal after = before.add(amount);
        if (after.compareTo(MAX) > 0) throw new TransactionValidationException(409, "Credit exceeds the supported balance limit");
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
        if (accounts.updateCreditedBalance(accountId, after, now) != 1) throw new IllegalStateException("Account update failed");
        AuditLog audit = new AuditLog(); audit.setUserId(actorId); audit.setRequestKey(requestKey);
        audit.setAction("SIMULATED_CREDIT:" + accountId);
        audit.setOldState(before.toPlainString()); audit.setNewState(after.toPlainString()); audit.setCreatedAt(now);
        // Unique durable request key and balance update commit or roll back together.
        audits.saveAndFlush(audit);
        return receipt(accountId, amount, audit);
    }

    private Receipt replay(AuditLog audit, Long accountId, Long actorId, BigDecimal amount) {
        if (!Objects.equals(audit.getUserId(), actorId)) throw new ResourceNotFoundExcp("Credit request not found");
        if (!Objects.equals(audit.getAction(), "SIMULATED_CREDIT:" + accountId)) {
            throw new TransactionValidationException(409, "Idempotency-Key was already used for a different credit");
        }
        BigDecimal originalAmount = new BigDecimal(audit.getNewState()).subtract(new BigDecimal(audit.getOldState()));
        if (originalAmount.compareTo(amount) != 0) {
            throw new TransactionValidationException(409, "Idempotency-Key was already used for a different credit");
        }
        return receipt(accountId, originalAmount.setScale(2), audit);
    }

    private Receipt receipt(Long accountId, BigDecimal amount, AuditLog audit) {
        return new Receipt(accountId, amount.toPlainString(), audit.getOldState(), audit.getNewState(), audit.getCreatedAt(), DESCRIPTION);
    }
}