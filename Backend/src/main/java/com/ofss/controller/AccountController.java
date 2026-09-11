package com.ofss.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ofss.beans.Account;
import com.ofss.services.AccountService;

@RestController
@RequestMapping("accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    public List<Account> getAccounts(@RequestParam(required = false) String userEmail) {
        return userEmail == null ? accountService.getAllAccounts() : accountService.getAccounts(userEmail);
    }

    @GetMapping("/{accountId}")
    public Account getAccount(@PathVariable Long accountId) {
        return accountService.getAccount(accountId);
    }

    @PostMapping
    public Account createAccount(@RequestParam Long userId, @RequestBody Account account) {
        return accountService.createAccount(userId, account);
    }

    @PutMapping("/{accountId}")
    public Account updateAccount(@PathVariable Long accountId, @RequestBody Account account) {
        return accountService.updateAccount(accountId, account);
    }

    @DeleteMapping("/{accountId}")
    public void deleteAccount(@PathVariable Long accountId) {
        accountService.deleteAccount(accountId);
    }
}
