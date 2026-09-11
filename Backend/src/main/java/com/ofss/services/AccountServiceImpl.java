package com.ofss.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ofss.beans.Account;
import com.ofss.beans.User;
import com.ofss.excp.ResourceNotFoundExcp;
import com.ofss.repository.AccountDao;
import com.ofss.repository.BeneficiaryDao;
import com.ofss.repository.TransactionDao;
import com.ofss.repository.UserDao;

@Service
public class AccountServiceImpl implements AccountService {

    private static final BigDecimal MINIMUM_BALANCE = new BigDecimal("5000.00");

    private final AccountDao accountDao;
    private final UserDao userDao;
    private final BeneficiaryDao beneficiaryDao;
    private final TransactionDao transactionDao;

    public AccountServiceImpl(AccountDao accountDao, UserDao userDao, BeneficiaryDao beneficiaryDao,
            TransactionDao transactionDao) {
        this.accountDao = accountDao;
        this.userDao = userDao;
        this.beneficiaryDao = beneficiaryDao;
        this.transactionDao = transactionDao;
    }

    @Override
    public List<Account> getAccounts(String userEmail) {
        return accountDao.findByUserEmail(userEmail);
    }

    @Override
    public List<Account> getAllAccounts() {
        return accountDao.findAll();
    }

    @Override
    public Account getAccount(Long accountId) {
        return accountDao.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundExcp("Account not found"));
    }

    @Override
    @Transactional
    public Account createAccount(Long userId, Account account) {
        User user = userDao.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundExcp("User not found"));
        account.setBalance(validateBalance(account.getBalance()));
        account.setAccountId(null);
        account.setUser(user);
        account.setCreatedAt(LocalDateTime.now());
        return accountDao.save(account);
    }

    @Override
    @Transactional
    public Account updateAccount(Long accountId, Account account) {
        BigDecimal balance = validateBalance(account.getBalance());
        Account savedAccount = getAccountForUpdate(accountId);
        BigDecimal heldAmount = transactionDao.sumHeldAmount(accountId);
        if (balance.compareTo(MINIMUM_BALANCE.add(heldAmount)) < 0) {
            throw new IllegalArgumentException(
                    "Account balance must cover pending payments and the minimum balance of 5000");
        }
        savedAccount.setBalance(balance);
        return accountDao.save(savedAccount);
    }

    @Override
    @Transactional
    public void deleteAccount(Long accountId) {
        Account account = getAccountForUpdate(accountId);
        if (beneficiaryDao.existsByAccountAccountId(accountId)
                || transactionDao.existsByFromAccountAccountId(accountId)) {
            throw new IllegalArgumentException("Account cannot be deleted because it has beneficiaries or transactions");
        }
        accountDao.delete(account);
    }

    private Account getAccountForUpdate(Long accountId) {
        return accountDao.findByAccountIdForUpdate(accountId)
                .orElseThrow(() -> new ResourceNotFoundExcp("Account not found"));
    }

    private BigDecimal validateBalance(BigDecimal balance) {
        if (balance == null) {
            throw new IllegalArgumentException("Account balance is required");
        }
        BigDecimal normalized;
        try {
            normalized = balance.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Account balance must have at most 2 decimal places");
        }
        if (normalized.precision() > 18) {
            throw new IllegalArgumentException("Account balance must have at most 16 integer digits");
        }
        if (normalized.compareTo(MINIMUM_BALANCE) < 0) {
            throw new IllegalArgumentException("Account balance must be at least 5000");
        }
        return normalized;
    }
}
