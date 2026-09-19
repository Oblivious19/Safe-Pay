package com.ofss.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ofss.beans.Account;
import com.ofss.beans.AccountStatus;
import com.ofss.beans.AccountType;
import com.ofss.beans.CurrencyCode;
import com.ofss.dto.account.AccountBalanceResponse;
import com.ofss.dto.account.AccountSummaryResponse;
import com.ofss.excp.BusinessRuleException;
import com.ofss.excp.ResourceNotFoundExcp;
import com.ofss.repository.AccountDao;

@ExtendWith(MockitoExtension.class)
class AccountServiceImplTest {

    @Mock
    private AccountDao accountDao;

    private AccountService accountService;

    @BeforeEach
    void setUp() {
        accountService = new AccountServiceImpl(accountDao);
    }

    @Test
    void listsOnlyAccountsReturnedByOwnershipQuery() {
        Account account = summaryAccount();

        when(accountDao
                .findAllByOwner_UserIdOrderByAccountIdAsc(101L))
                .thenReturn(List.of(account));

        List<AccountSummaryResponse> responses =
                accountService.listOwnedAccounts(101L);

        assertThat(responses).hasSize(1);

        AccountSummaryResponse response = responses.getFirst();

        assertThat(response.accountId()).isEqualTo("501");
        assertThat(response.maskedAccountNumber())
                .isEqualTo("********9012");
        assertThat(response.accountType())
                .isEqualTo(AccountType.SAVINGS);
        assertThat(response.currency())
                .isEqualTo(CurrencyCode.INR);
    }

    @Test
    void hidesMissingOrUnownedAccountBehindNotFound() {
        when(accountDao
                .findByAccountIdAndOwner_UserId(
                        501L,
                        101L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(
                () -> accountService.getOwnedAccount(
                        101L,
                        501L))
                .isInstanceOf(ResourceNotFoundExcp.class)
                .satisfies(exception -> {
                    ResourceNotFoundExcp notFound =
                            (ResourceNotFoundExcp) exception;

                    assertThat(notFound.getErrorCode())
                            .isEqualTo("ACCOUNT_NOT_FOUND");
                });
    }

    @Test
    void returnsBalancesAsExactDecimalStrings() {
        Account account = balanceAccount();

        when(accountDao
                .findByAccountIdAndOwner_UserId(
                        501L,
                        101L))
                .thenReturn(Optional.of(account));

        AccountBalanceResponse response =
                accountService.getOwnedBalance(
                        101L,
                        501L);

        assertThat(response.currentBalance())
                .isEqualTo("10000.00");
        assertThat(response.reservedAmount())
                .isEqualTo("2500.00");
        assertThat(response.availableBalance())
                .isEqualTo("7500.00");
    }

    @Test
    void rejectsInactiveAccountForPaymentUse() {
        Account account = mock(Account.class);

        when(account.isActive()).thenReturn(false);

        when(accountDao
                .findByAccountIdAndOwner_UserId(
                        501L,
                        101L))
                .thenReturn(Optional.of(account));

        assertThatThrownBy(
                () -> accountService
                        .getRequiredActiveOwnedAccount(
                                101L,
                                501L))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(exception -> {
                    BusinessRuleException ruleFailure =
                            (BusinessRuleException) exception;

                    assertThat(ruleFailure.getErrorCode())
                            .isEqualTo("ACCOUNT_INACTIVE");
                });
    }

    @Test
    void rejectsInvalidOwnerIdBeforeRepositoryAccess() {
        assertThatThrownBy(
                () -> accountService.listOwnedAccounts(0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ownerUserId must be positive");

        verifyNoInteractions(accountDao);
    }

    @Test
    void returnsEmptyListWhenCustomerHasNoAccounts() {
        when(accountDao.findAllByOwner_UserIdOrderByAccountIdAsc(101L))
                .thenReturn(List.of());

        assertThat(accountService.listOwnedAccounts(101L)).isEmpty();
    }

    @Test
    void allowsOwnedInactiveAccountDetailsToBeRead() {
        Account account = summaryAccount();
        when(account.getStatus()).thenReturn(AccountStatus.INACTIVE);
        when(accountDao.findByAccountIdAndOwner_UserId(501L, 101L))
                .thenReturn(Optional.of(account));

        assertThat(accountService.getOwnedAccount(101L, 501L).status())
                .isEqualTo(AccountStatus.INACTIVE);
    }

    @Test
    void hidesMissingOrForeignAccountBalanceBehindNotFound() {
        when(accountDao.findByAccountIdAndOwner_UserId(501L, 101L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.getOwnedBalance(101L, 501L))
                .isInstanceOf(ResourceNotFoundExcp.class)
                .hasMessage("Account was not found")
                .extracting("errorCode").isEqualTo("ACCOUNT_NOT_FOUND");
    }

    @Test
    void rejectsNonpositiveAccountIdsBeforeRepositoryAccess() {
        for (Long accountId : List.of(0L, -1L)) {
            assertThatThrownBy(() -> accountService.getOwnedAccount(101L, accountId))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> accountService.getOwnedBalance(101L, accountId))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        verifyNoInteractions(accountDao);
    }

    @Test
    void preservesMaximumOracleMoneyPrecisionInBalanceResponse() {
        Account account = balanceAccount();
        BigDecimal maximum = new BigDecimal("9999999999999999.99");
        when(account.getCurrentBalance()).thenReturn(maximum);
        when(account.getReservedAmount()).thenReturn(new BigDecimal("0.00"));
        when(account.getAvailableBalance()).thenReturn(maximum);
        when(accountDao.findByAccountIdAndOwner_UserId(501L, 101L))
                .thenReturn(Optional.of(account));

        AccountBalanceResponse response = accountService.getOwnedBalance(101L, 501L);
        assertThat(response.currentBalance()).isEqualTo("9999999999999999.99");
        assertThat(response.reservedAmount()).isEqualTo("0.00");
        assertThat(response.availableBalance()).isEqualTo("9999999999999999.99");
    }

    private Account summaryAccount() {
        Account account = mock(Account.class);

        when(account.getAccountId()).thenReturn(501L);
        when(account.getAccountNumber())
                .thenReturn("123456789012");
        when(account.getAccountType())
                .thenReturn(AccountType.SAVINGS);
        when(account.getBankName())
                .thenReturn("SafePay Demo Bank");
        when(account.getIfscCode())
                .thenReturn("ABCD0123456");
        when(account.getCurrencyCode())
                .thenReturn(CurrencyCode.INR);
        when(account.getStatus())
                .thenReturn(AccountStatus.ACTIVE);

        return account;
    }

    private Account balanceAccount() {
        Account account = mock(Account.class);

        when(account.getAccountId()).thenReturn(501L);
        when(account.getAccountNumber())
                .thenReturn("123456789012");
        when(account.getCurrencyCode())
                .thenReturn(CurrencyCode.INR);
        when(account.getCurrentBalance())
                .thenReturn(new BigDecimal("10000.00"));
        when(account.getReservedAmount())
                .thenReturn(new BigDecimal("2500.00"));
        when(account.getAvailableBalance())
                .thenReturn(new BigDecimal("7500.00"));

        return account;
    }
}
