package com.ofss.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ofss.dto.account.AccountBalanceResponse;
import com.ofss.dto.account.AccountSummaryResponse;
import com.ofss.security.AuthenticatedUser;
import com.ofss.services.AccountService;

@RestController
@RequestMapping("/api/v1/accounts")
@PreAuthorize("hasAuthority('CUSTOMER')")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    public ResponseEntity<List<AccountSummaryResponse>> list(
            Authentication authentication) {

        return ResponseEntity.ok(
                accountService.listOwnedAccounts(
                        AuthenticatedUser.userId(authentication)));
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<AccountSummaryResponse> get(
            Authentication authentication,
            @PathVariable("accountId") Long accountId) {

        return ResponseEntity.ok(
                accountService.getOwnedAccount(
                        AuthenticatedUser.userId(authentication),
                        accountId));
    }

    @GetMapping("/{accountId}/balance")
    public ResponseEntity<AccountBalanceResponse> getBalance(
            Authentication authentication,
            @PathVariable("accountId") Long accountId) {

        return ResponseEntity.ok(
                accountService.getOwnedBalance(
                        AuthenticatedUser.userId(authentication),
                        accountId));
    }
}
