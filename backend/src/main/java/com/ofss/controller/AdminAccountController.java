package com.ofss.controller;

import java.math.BigDecimal;
import java.util.Objects;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ofss.beans.AccountType;
import com.ofss.common.api.PagedResponse;
import com.ofss.dto.account.AccountBalanceResponse;
import com.ofss.dto.admin.AdminAccountDetailResponse;
import com.ofss.dto.admin.AdminAccountSummaryResponse;
import com.ofss.security.AuthenticatedUser;
import com.ofss.services.AdminAccountService;

@RestController
@RequestMapping("/api/v1/admin/accounts")
@PreAuthorize("hasAuthority('SYSTEM_ADMIN')")
public class AdminAccountController {

    private final AdminAccountService service;

    public AdminAccountController(AdminAccountService service) {
        this.service = Objects.requireNonNull(service, "service is required");
    }

    @GetMapping
    public PagedResponse<AdminAccountSummaryResponse> search(
            Authentication authentication,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) BigDecimal minCurrentBalance,
            @RequestParam(required = false) AccountType accountType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.searchAccounts(AuthenticatedUser.userId(authentication),
                customerId, minCurrentBalance, accountType, page, size);
    }

    @GetMapping("/{accountId}")
    public AdminAccountDetailResponse getAccount(Authentication authentication,
            @PathVariable("accountId") Long accountId) {
        return service.getAccount(AuthenticatedUser.userId(authentication), accountId);
    }

    @GetMapping("/{accountId}/balance")
    public AccountBalanceResponse getBalance(Authentication authentication,
            @PathVariable("accountId") Long accountId) {
        return service.getBalance(AuthenticatedUser.userId(authentication), accountId);
    }
}
