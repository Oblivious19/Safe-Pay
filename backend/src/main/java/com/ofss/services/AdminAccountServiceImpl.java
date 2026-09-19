package com.ofss.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ofss.beans.Account;
import com.ofss.beans.AccountType;
import com.ofss.beans.RoleName;
import com.ofss.common.api.PagedResponse;
import com.ofss.dto.account.AccountBalanceResponse;
import com.ofss.dto.admin.AdminAccountDetailResponse;
import com.ofss.dto.admin.AdminAccountSummaryResponse;
import com.ofss.excp.ResourceNotFoundExcp;
import com.ofss.repository.AccountDao;
import com.ofss.security.StaffReadAccess;

@Service
@Transactional(readOnly = true)
public class AdminAccountServiceImpl implements AdminAccountService {

    private final AccountDao accountDao;
    private final StaffReadAccess staffReadAccess;

    public AdminAccountServiceImpl(AccountDao accountDao, StaffReadAccess staffReadAccess) {
        this.accountDao = Objects.requireNonNull(accountDao, "accountDao is required");
        this.staffReadAccess = Objects.requireNonNull(staffReadAccess, "staffReadAccess is required");
    }

    @Override
    public PagedResponse<AdminAccountSummaryResponse> searchAccounts(
            Long administratorId, Long customerId, BigDecimal minCurrentBalance,
            AccountType accountType, int page, int size) {
        staffReadAccess.requireActiveRole(administratorId, RoleName.SYSTEM_ADMIN);
        if (customerId != null) {
            requirePositive(customerId, "customerId");
        }
        if (accountType != null && !accountType.isCustomerOwnedType()) {
            throw new IllegalArgumentException("accountType must be SAVINGS or CURRENT");
        }
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("page must be non-negative and size between 1 and 100");
        }
        BigDecimal minimum = minCurrentBalance;
        if (minimum != null) {
            if (minimum.signum() < 0 || minimum.compareTo(new BigDecimal("9999999999999999.99")) > 0) {
                throw new IllegalArgumentException("minCurrentBalance must fit non-negative NUMBER(18,2)");
            }
            try {
                minimum = minimum.setScale(2, RoundingMode.UNNECESSARY);
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException("minCurrentBalance must have at most two decimal places");
            }
        }
        return PagedResponse.from(accountDao.searchCustomerAccounts(
                customerId, minimum, accountType, PageRequest.of(page, size)),
                AdminAccountSummaryResponse::from);
    }

    @Override
    public AdminAccountDetailResponse getAccount(Long administratorId, Long accountId) {
        staffReadAccess.requireActiveRole(administratorId, RoleName.SYSTEM_ADMIN);
        return AdminAccountDetailResponse.from(requiredAccount(accountId));
    }

    @Override
    public AccountBalanceResponse getBalance(Long administratorId, Long accountId) {
        staffReadAccess.requireActiveRole(administratorId, RoleName.SYSTEM_ADMIN);
        return AccountBalanceResponse.from(requiredAccount(accountId));
    }

    private Account requiredAccount(Long accountId) {
        requirePositive(accountId, "accountId");
        return accountDao.findCustomerAccountById(accountId).orElseThrow(() ->
                new ResourceNotFoundExcp("ACCOUNT_NOT_FOUND", "Account was not found"));
    }

    private static void requirePositive(Long value, String field) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
    }
}
