package com.ofss.controller;

import java.util.List;
import com.ofss.beans.LoginPrincipal;
import com.ofss.repository.AccountDao;
import com.ofss.excp.ResourceNotFoundExcp;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ofss.beans.Account;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountDao accounts;

    public AccountController(AccountDao accounts) {
        this.accounts = accounts;
    }

    @GetMapping("/current")
    public Account current(@AuthenticationPrincipal LoginPrincipal caller,
            @RequestParam(required = false) Long accountId) {
        if (accountId != null && accountId <= 0) {
            throw new IllegalArgumentException("Account ID must be positive");
        }
        return (accountId == null ? accounts.findFirstByUserUserIdOrderByAccountId(caller.userId())
                : accounts.findByAccountIdAndUserUserId(accountId, caller.userId()))
                .orElseThrow(() -> new ResourceNotFoundExcp("Account not found"));
    }

    @GetMapping
    public List<Account> ownedAccounts(@AuthenticationPrincipal LoginPrincipal caller) {
        return accounts.findByUserUserIdOrderByAccountId(caller.userId());
    }
}
