package com.ofss.services;

import java.util.List;

import com.ofss.beans.Account;

public interface AccountService {
    List<Account> getAccounts(String userEmail);
    List<Account> getAllAccounts();
    Account getAccount(Long accountId);
    Account createAccount(Long userId, Account account);
    Account updateAccount(Long accountId, Account account);
    void deleteAccount(Long accountId);
}
