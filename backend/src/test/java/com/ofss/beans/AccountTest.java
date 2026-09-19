package com.ofss.beans;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class AccountTest {

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
    void createsCanonicalCustomerAccount() {
        User owner = persistedOwner();

        Account account = Account.createCustomerAccount(
                owner,
                "  123456789012  ",
                AccountType.SAVINGS,
                "  SafePay Demo Bank  ",
                " abcd0123456 ",
                new BigDecimal("25000.00"),
                CREATED_AT);

        assertThat(account.getOwner()).isSameAs(owner);
        assertThat(account.getAccountNumber())
                .isEqualTo("123456789012");
        assertThat(account.getAccountType())
                .isEqualTo(AccountType.SAVINGS);
        assertThat(account.getBankName())
                .isEqualTo("SafePay Demo Bank");
        assertThat(account.getIfscCode())
                .isEqualTo("ABCD0123456");
        assertThat(account.getCurrencyCode())
                .isEqualTo(CurrencyCode.INR);
        assertThat(account.getStatus())
                .isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.getCurrentBalance())
                .isEqualByComparingTo("25000.00");
        assertThat(account.getReservedAmount())
                .isEqualByComparingTo("0.00");
        assertThat(account.getAvailableBalance())
                .isEqualByComparingTo("25000.00");
    }

    @Test
    void createsCanonicalSystemClearingAccount() {
        Account account = Account.createSystemAccount(
                "SAFEPAY_OUTBOUND_CLEARING",
                AccountType.OUTBOUND_CLEARING,
                "SafePay Internal Clearing",
                BigDecimal.ZERO,
                CREATED_AT);

        assertThat(account.getOwner()).isNull();
        assertThat(account.getIfscCode()).isNull();
        assertThat(account.isSystemAccount()).isTrue();
        assertThat(account.getCurrencyCode())
                .isEqualTo(CurrencyCode.INR);
        assertThat(account.getAvailableBalance())
                .isEqualByComparingTo("0.00");
    }

    @Test
    void rejectsSystemTypeForCustomerAccount() {
        User owner = mock(User.class);

        assertThatThrownBy(
                () -> Account.createCustomerAccount(
                        owner,
                        "123456789012",
                        AccountType.OUTBOUND_CLEARING,
                        "SafePay Bank",
                        "ABCD0123456",
                        new BigDecimal("100.00"),
                        CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "Customer account must use SAVINGS or CURRENT");
    }

    @Test
    void rejectsCustomerTypeForSystemAccount() {
        assertThatThrownBy(
                () -> Account.createSystemAccount(
                        "SYSTEM_ACCOUNT",
                        AccountType.SAVINGS,
                        "SafePay Internal",
                        BigDecimal.ZERO,
                        CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "System account must use a system account type");
    }

    @Test
    void rejectsUnpersistedOwner() {
        User unpersistedOwner = User.createActiveUser(
                "Unpersisted Customer",
                "unpersisted@example.com",
                null,
                "stored-password-hash",
                CREATED_AT);

        assertThat(unpersistedOwner.getUserId()).isNull();

        assertThatThrownBy(
                () -> Account.createCustomerAccount(
                        unpersistedOwner,
                        "123456789012",
                        AccountType.CURRENT,
                        "SafePay Bank",
                        "ABCD0123456",
                        new BigDecimal("100.00"),
                        CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("owner must already be persisted");
    }

    @Test
    void rejectsInvalidIfsc() {
        User owner = persistedOwner();

        assertThatThrownBy(
                () -> Account.createCustomerAccount(
                        owner,
                        "123456789012",
                        AccountType.SAVINGS,
                        "SafePay Bank",
                        "INVALID",
                        new BigDecimal("100.00"),
                        CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ifscCode has an invalid format");
    }

    @Test
    void rejectsNegativeOpeningBalance() {
        User owner = persistedOwner();

        assertThatThrownBy(
                () -> Account.createCustomerAccount(
                        owner,
                        "123456789012",
                        AccountType.SAVINGS,
                        "SafePay Bank",
                        "ABCD0123456",
                        new BigDecimal("-0.01"),
                        CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("openingBalance cannot be negative");
    }

    @Test
    void rejectsOpeningBalanceRequiringRounding() {
        User owner = persistedOwner();

        assertThatThrownBy(
                () -> Account.createCustomerAccount(
                        owner,
                        "123456789012",
                        AccountType.SAVINGS,
                        "SafePay Bank",
                        "ABCD0123456",
                        new BigDecimal("100.001"),
                        CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "openingBalance must not contain more than two decimal places");
    }

    private User persistedOwner() {
        User owner = mock(User.class);
        when(owner.getUserId()).thenReturn(101L);
        return owner;
    }
}