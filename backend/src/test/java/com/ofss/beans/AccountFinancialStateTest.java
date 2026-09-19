package com.ofss.beans;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.ofss.excp.BusinessRuleException;

class AccountFinancialStateTest {

    private static final OffsetDateTime CREATED_AT =
            OffsetDateTime.of(
                    2026,
                    9,
                    14,
                    12,
                    0,
                    0,
                    0,
                    ZoneOffset.UTC);

    @Test
    void reservesFundsAndReducesAvailableBalance() {
        Account account = customerAccount("1000.00");

        OffsetDateTime reservedAt =
                CREATED_AT.plusMinutes(1);

        account.reserveFunds(
                new BigDecimal("250.00"),
                reservedAt);

        assertThat(account.getCurrentBalance())
                .isEqualByComparingTo("1000.00");
        assertThat(account.getReservedAmount())
                .isEqualByComparingTo("250.00");
        assertThat(account.getAvailableBalance())
                .isEqualByComparingTo("750.00");
        assertThat(account.getUpdatedAt())
                .isEqualTo(reservedAt);
    }

    @Test
    void accumulatesMultipleReservationsExactly() {
        Account account = customerAccount("1000.00");

        account.reserveFunds(
                new BigDecimal("100.25"),
                CREATED_AT.plusMinutes(1));

        account.reserveFunds(
                new BigDecimal("200.50"),
                CREATED_AT.plusMinutes(2));

        assertThat(account.getReservedAmount())
                .isEqualByComparingTo("300.75");
        assertThat(account.getAvailableBalance())
                .isEqualByComparingTo("699.25");
    }

    @Test
    void insufficientBalanceDoesNotChangeAccount() {
        Account account = customerAccount("100.00");

        assertThatThrownBy(
                () -> account.reserveFunds(
                        new BigDecimal("100.01"),
                        CREATED_AT.plusMinutes(1)))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(exception -> {
                    BusinessRuleException failure =
                            (BusinessRuleException) exception;

                    assertThat(failure.getErrorCode())
                            .isEqualTo(
                                    "INSUFFICIENT_AVAILABLE_BALANCE");
                });

        assertThat(account.getCurrentBalance())
                .isEqualByComparingTo("100.00");
        assertThat(account.getReservedAmount())
                .isEqualByComparingTo("0.00");
        assertThat(account.getAvailableBalance())
                .isEqualByComparingTo("100.00");
        assertThat(account.getUpdatedAt())
                .isEqualTo(CREATED_AT);
    }

    @Test
    void releasesReservationWithoutDebitingCurrentBalance() {
        Account account = customerAccount("1000.00");

        account.reserveFunds(
                new BigDecimal("300.00"),
                CREATED_AT.plusMinutes(1));

        account.releaseReservedFunds(
                new BigDecimal("300.00"),
                CREATED_AT.plusMinutes(2));

        assertThat(account.getCurrentBalance())
                .isEqualByComparingTo("1000.00");
        assertThat(account.getReservedAmount())
                .isEqualByComparingTo("0.00");
        assertThat(account.getAvailableBalance())
                .isEqualByComparingTo("1000.00");
    }

    @Test
    void consumesReservationDuringSettlement() {
        Account account = customerAccount("1000.00");

        account.reserveFunds(
                new BigDecimal("300.00"),
                CREATED_AT.plusMinutes(1));

        account.consumeReservedFunds(
                new BigDecimal("300.00"),
                CREATED_AT.plusMinutes(2));

        assertThat(account.getCurrentBalance())
                .isEqualByComparingTo("700.00");
        assertThat(account.getReservedAmount())
                .isEqualByComparingTo("0.00");
        assertThat(account.getAvailableBalance())
                .isEqualByComparingTo("700.00");
    }

    @Test
    void creditsSystemClearingAccount() {
        Account clearingAccount =
                Account.createSystemAccount(
                        "SAFEPAY_OUTBOUND_CLEARING",
                        AccountType.OUTBOUND_CLEARING,
                        "SafePay Internal Clearing",
                        BigDecimal.ZERO,
                        CREATED_AT);

        clearingAccount.creditSettlementFunds(
                new BigDecimal("300.00"),
                CREATED_AT.plusMinutes(1));

        assertThat(clearingAccount.getCurrentBalance())
                .isEqualByComparingTo("300.00");
        assertThat(clearingAccount.getReservedAmount())
                .isEqualByComparingTo("0.00");
        assertThat(clearingAccount.getAvailableBalance())
                .isEqualByComparingTo("300.00");
    }

    @Test
    void rejectsFinancialOperationOnWrongAccountType() {
        Account customerAccount =
                customerAccount("1000.00");

        Account clearingAccount =
                Account.createSystemAccount(
                        "SAFEPAY_OUTBOUND_CLEARING",
                        AccountType.OUTBOUND_CLEARING,
                        "SafePay Internal Clearing",
                        BigDecimal.ZERO,
                        CREATED_AT);

        assertThatThrownBy(
                () -> clearingAccount.reserveFunds(
                        new BigDecimal("10.00"),
                        CREATED_AT.plusMinutes(1)))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(exception -> {
                    BusinessRuleException failure =
                            (BusinessRuleException) exception;

                    assertThat(failure.getErrorCode())
                            .isEqualTo(
                                    "CUSTOMER_ACCOUNT_REQUIRED");
                });

        assertThatThrownBy(
                () -> customerAccount.creditSettlementFunds(
                        new BigDecimal("10.00"),
                        CREATED_AT.plusMinutes(1)))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(exception -> {
                    BusinessRuleException failure =
                            (BusinessRuleException) exception;

                    assertThat(failure.getErrorCode())
                            .isEqualTo(
                                    "SYSTEM_ACCOUNT_REQUIRED");
                });
    }

    @Test
    void rejectsAmountThatWouldRequireRounding() {
        Account account = customerAccount("1000.00");

        assertThatThrownBy(
                () -> account.reserveFunds(
                        new BigDecimal("10.001"),
                        CREATED_AT.plusMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "amount must not contain more than two decimal places");

        assertThat(account.getReservedAmount())
                .isEqualByComparingTo("0.00");
    }

    private Account customerAccount(
            String openingBalance) {

        User owner = mock(User.class);
        when(owner.getUserId()).thenReturn(101L);

        return Account.createCustomerAccount(
                owner,
                "123456789012",
                AccountType.SAVINGS,
                "SafePay Demo Bank",
                "ABCD0123456",
                new BigDecimal(openingBalance),
                CREATED_AT);
    }
}