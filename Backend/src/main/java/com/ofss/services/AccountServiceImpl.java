package com.ofss.services;

import java.util.List;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ofss.beans.Account;
import com.ofss.beans.User;
import com.ofss.beans.AccountStatus;
import com.ofss.beans.AccountType;
import com.ofss.excp.AccountAlreadyExistsException;
import com.ofss.excp.ResourceNotFoundExcp;
import com.ofss.repository.AccountDao;
import com.ofss.repository.BeneficiaryDao;
import com.ofss.repository.TransactionDao;
import com.ofss.repository.UserDao;

@Service
public class AccountServiceImpl implements AccountService {

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
        if (accountDao.existsByUserUserId(userId)) {
            throw new AccountAlreadyExistsException("An account already exists for this user");
        }
        if (account.getBalance() == null || account.getBalance().signum() < 0) {
            throw new IllegalArgumentException("Account balance must not be negative");
        }
        account.setAccountId(null);
        account.setUser(user);
        account.setAccountNumber(accountDao.nextAccountNumber());
        account.setAccountType(AccountType.SAVINGS);
        account.setStatus(AccountStatus.ACTIVE);
        LocalDateTime now = LocalDateTime.now();
        account.setCreatedAt(now);
        account.setUpdatedAt(now);
        return accountDao.save(account);
    }

    @Override
    @Transactional
    public Account updateAccount(Long accountId, Account account) {
        Account savedAccount = getAccount(accountId);
        if (account.getBalance() == null || account.getBalance().signum() < 0) {
            throw new IllegalArgumentException("Account balance must not be negative");
        }
        savedAccount.setBalance(account.getBalance());
        return accountDao.save(savedAccount);
    }

    @Override
    @Transactional
    public void deleteAccount(Long accountId) {
        getAccount(accountId);
        if (beneficiaryDao.existsByAccountAccountId(accountId)
                || transactionDao.existsByFromAccountAccountId(accountId)) {
            throw new IllegalArgumentException("Account cannot be deleted because it has beneficiaries or transactions");
        }
        accountDao.deleteById(accountId);
    }
}
