package com.ofss.services;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ofss.beans.Account;
import com.ofss.beans.AccountType;
import com.ofss.excp.BusinessRuleException;
import com.ofss.excp.ResourceNotFoundExcp;
import com.ofss.repository.AccountDao;

@Service
@Transactional
public class AccountFundsServiceImpl
        implements AccountFundsService {

    private final AccountDao accountDao;

    public AccountFundsServiceImpl(AccountDao accountDao) {
        this.accountDao = accountDao;
    }

    @Override
    public void reserveOwnedFunds(
            Long ownerUserId,
            Long sourceAccountId,
            BigDecimal amount,
            OffsetDateTime reservedAt) {

        requirePositiveId(ownerUserId, "ownerUserId");
        requirePositiveId(sourceAccountId, "sourceAccountId");
        requireFinancialArguments(amount, reservedAt);

        Account sourceAccount =
                getRequiredAccountForUpdate(sourceAccountId);

        if (sourceAccount.getOwner() == null
                || !Objects.equals(
                        sourceAccount.getOwner().getUserId(),
                        ownerUserId)) {

            /*
             * Return the same error as a missing account so callers
             * cannot use this operation to discover account ownership.
             */
            throw accountNotFound(sourceAccountId);
        }

        sourceAccount.reserveFunds(amount, reservedAt);
    }

    @Override
    public void releaseReservedFunds(
            Long sourceAccountId,
            BigDecimal amount,
            OffsetDateTime releasedAt) {

        requirePositiveId(sourceAccountId, "sourceAccountId");
        requireFinancialArguments(amount, releasedAt);

        Account sourceAccount =
                getRequiredAccountForUpdate(sourceAccountId);

        if (!sourceAccount.isCustomerOwnedAccount()) {
            throw new BusinessRuleException(
                    "CUSTOMER_ACCOUNT_REQUIRED",
                    "A customer-owned account is required");
        }

        /*
         * Reservation release remains possible when an account has
         * become inactive. This prevents funds from remaining trapped.
         */
        sourceAccount.releaseReservedFunds(
                amount,
                releasedAt);
    }

    @Override
    public void settleReservedFunds(
            Long sourceAccountId,
            Long clearingAccountId,
            BigDecimal amount,
            OffsetDateTime settledAt) {

        requirePositiveId(sourceAccountId, "sourceAccountId");
        requirePositiveId(clearingAccountId, "clearingAccountId");
        requireFinancialArguments(amount, settledAt);

        if (sourceAccountId.equals(clearingAccountId)) {
            throw new IllegalArgumentException(
                    "Source and clearing accounts must be different");
        }

        /*
         * Always lock the smaller ID first. Consistent lock ordering
         * reduces deadlock risk when transactions overlap.
         */
        Long firstAccountId =
                Math.min(sourceAccountId, clearingAccountId);

        Long secondAccountId =
                Math.max(sourceAccountId, clearingAccountId);

        Account firstAccount =
                getRequiredAccountForUpdate(firstAccountId);

        Account secondAccount =
                getRequiredAccountForUpdate(secondAccountId);

        Account sourceAccount =
                sourceAccountId.equals(firstAccountId)
                        ? firstAccount
                        : secondAccount;

        Account clearingAccount =
                clearingAccountId.equals(firstAccountId)
                        ? firstAccount
                        : secondAccount;

        validateSettlementAccounts(
                sourceAccount,
                clearingAccount);

        /*
         * Both mutations belong to this single transaction.
         * If either operation fails, Spring rolls back both updates.
         */
        sourceAccount.consumeReservedFunds(
                amount,
                settledAt);

        clearingAccount.creditSettlementFunds(
                amount,
                settledAt);
    }

    private void validateSettlementAccounts(
            Account sourceAccount,
            Account clearingAccount) {

        if (!sourceAccount.isCustomerOwnedAccount()) {
            throw new BusinessRuleException(
                    "CUSTOMER_ACCOUNT_REQUIRED",
                    "A customer-owned source account is required");
        }

        if (!sourceAccount.isActive()) {
            throw new BusinessRuleException(
                    "ACCOUNT_INACTIVE",
                    "Source account is inactive");
        }

        if (clearingAccount.getAccountType()
                != AccountType.OUTBOUND_CLEARING) {

            throw new BusinessRuleException(
                    "OUTBOUND_CLEARING_ACCOUNT_REQUIRED",
                    "An outbound clearing account is required");
        }

        if (!clearingAccount.isActive()) {
            throw new BusinessRuleException(
                    "ACCOUNT_INACTIVE",
                    "Outbound clearing account is inactive");
        }
    }

    private Account getRequiredAccountForUpdate(
            Long accountId) {

        return accountDao
                .findByIdForUpdate(accountId)
                .orElseThrow(
                        () -> accountNotFound(accountId));
    }

    private static ResourceNotFoundExcp accountNotFound(
            Long accountId) {

        return new ResourceNotFoundExcp(
                "ACCOUNT_NOT_FOUND",
                "Account " + accountId + " was not found");
    }

    private static void requirePositiveId(
            Long value,
            String fieldName) {

        if (value == null || value <= 0) {
            throw new IllegalArgumentException(
                    fieldName + " must be positive");
        }
    }

    private static void requireFinancialArguments(
            BigDecimal amount,
            OffsetDateTime occurredAt) {

        Objects.requireNonNull(amount, "amount is required");
        Objects.requireNonNull(
                occurredAt,
                "financial timestamp is required");

        if (amount.signum() <= 0) {
            throw new IllegalArgumentException(
                    "amount must be positive");
        }
    }
}