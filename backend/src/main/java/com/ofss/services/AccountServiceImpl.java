package com.ofss.services;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ofss.beans.Account;
import com.ofss.excp.BusinessRuleException;
import com.ofss.excp.ResourceNotFoundExcp;
import com.ofss.dto.account.AccountBalanceResponse;
import com.ofss.dto.account.AccountSummaryResponse;
import com.ofss.repository.AccountDao;

@Service
@Transactional(readOnly = true)
public class AccountServiceImpl
        implements AccountService {

    private final AccountDao accountDao;

    public AccountServiceImpl(AccountDao accountDao) {
        this.accountDao = accountDao;
    }

    @Override
    public List<AccountSummaryResponse> listOwnedAccounts(
            Long ownerUserId) {

        requirePositiveId(
                ownerUserId,
                "ownerUserId");

        return accountDao
                .findAllByOwner_UserIdOrderByAccountIdAsc(
                        ownerUserId)
                .stream()
                .map(AccountSummaryResponse::from)
                .toList();
    }

    @Override
    public AccountSummaryResponse getOwnedAccount(
            Long ownerUserId,
            Long accountId) {

        return AccountSummaryResponse.from(
                getRequiredOwnedAccount(
                        ownerUserId,
                        accountId));
    }

    @Override
    public AccountBalanceResponse getOwnedBalance(
            Long ownerUserId,
            Long accountId) {

        return AccountBalanceResponse.from(
                getRequiredOwnedAccount(
                        ownerUserId,
                        accountId));
    }

    @Override
    public Account getRequiredActiveOwnedAccount(
            Long ownerUserId,
            Long accountId) {

        Account account = getRequiredOwnedAccount(
                ownerUserId,
                accountId);

        if (!account.isActive()) {
            throw new BusinessRuleException(
                    "ACCOUNT_INACTIVE",
                    "Account is inactive");
        }

        return account;
    }

    private Account getRequiredOwnedAccount(
            Long ownerUserId,
            Long accountId) {

        requirePositiveId(
                ownerUserId,
                "ownerUserId");

        requirePositiveId(
                accountId,
                "accountId");

        return accountDao
                .findByAccountIdAndOwner_UserId(
                        accountId,
                        ownerUserId)
                .orElseThrow(() ->
                        new ResourceNotFoundExcp(
                                "ACCOUNT_NOT_FOUND",
                                "Account was not found"));
    }

    private void requirePositiveId(
            Long value,
            String fieldName) {

        if (value == null || value <= 0) {
            throw new IllegalArgumentException(
                    fieldName + " must be positive");
        }
    }
}