package com.ofss.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ofss.beans.Account;
import com.ofss.beans.AccountType;
import com.ofss.beans.CurrencyCode;
import com.ofss.beans.LedgerEntryType;
import com.ofss.beans.LedgerPostingStatus;
import com.ofss.beans.TransactionDb;
import com.ofss.beans.TransactionState;
import com.ofss.scheduler.SettlementProcessorProperties;

class SettlementPostingFactoryTest {

    private static final OffsetDateTime CREATED_AT =
            OffsetDateTime.parse("2026-09-16T11:00:00Z");

    @Test
    void createsCanonicalBalancedSettlementPair() {
        TestContext context = context();

        SettlementPostingPair pair = context.factory().create(
                context.transaction(),
                context.clearingAccount(),
                CREATED_AT);

        assertThat(pair.posting().getPostingReference())
                .isEqualTo("PAYMENT-SETTLEMENT-101");
        assertThat(pair.posting().getIdempotencyKey())
                .isEqualTo("TRANSACTION_SETTLE:TX-101");
        assertThat(pair.posting().getStatus())
                .isEqualTo(LedgerPostingStatus.PENDING);
        assertThat(pair.posting().getAmount())
                .isEqualByComparingTo("250.00");

        assertThat(pair.debitEntry().getEntryType())
                .isEqualTo(LedgerEntryType.DEBIT);
        assertThat(pair.debitEntry().getLineNumber()).isEqualTo(1);
        assertThat(pair.debitEntry().getAccount())
                .isSameAs(context.sourceAccount());

        assertThat(pair.creditEntry().getEntryType())
                .isEqualTo(LedgerEntryType.CREDIT);
        assertThat(pair.creditEntry().getLineNumber()).isEqualTo(2);
        assertThat(pair.creditEntry().getAccount())
                .isSameAs(context.clearingAccount());

        assertThat(pair.entriesInPostingOrder())
                .containsExactly(
                        pair.debitEntry(),
                        pair.creditEntry());
    }

    @Test
    void derivesEveryFinancialValueFromOneTransactionSnapshot() {
        TestContext context = context();

        SettlementPostingPair pair = context.factory().create(
                context.transaction(),
                context.clearingAccount(),
                CREATED_AT);

        assertThat(pair.debitEntry().getTransaction())
                .isSameAs(context.transaction());
        assertThat(pair.creditEntry().getTransaction())
                .isSameAs(context.transaction());
        assertThat(pair.debitEntry().getAmount())
                .isEqualByComparingTo(
                        pair.posting().getAmount());
        assertThat(pair.creditEntry().getAmount())
                .isEqualByComparingTo(
                        pair.posting().getAmount());
        assertThat(pair.debitEntry().getCurrencyCode())
                .isEqualTo(pair.posting().getCurrencyCode());
        assertThat(pair.creditEntry().getCurrencyCode())
                .isEqualTo(pair.posting().getCurrencyCode());
        assertThat(pair.debitEntry().getIdempotencyKey())
                .isEqualTo(pair.posting().getIdempotencyKey());
        assertThat(pair.creditEntry().getIdempotencyKey())
                .isEqualTo(pair.posting().getIdempotencyKey());
    }

    @Test
    void createsDeterministicIdentityAcrossReconstruction() {
        TestContext context = context();

        SettlementPostingPair first = context.factory().create(
                context.transaction(),
                context.clearingAccount(),
                CREATED_AT);

        SettlementPostingPair second = context.factory().create(
                context.transaction(),
                context.clearingAccount(),
                CREATED_AT.plusSeconds(5));

        assertThat(second.posting().getPostingReference())
                .isEqualTo(first.posting().getPostingReference());
        assertThat(second.posting().getIdempotencyKey())
                .isEqualTo(first.posting().getIdempotencyKey());
    }

    @Test
    void rejectsAccountThatDoesNotMatchConfiguredClearingId() {
        TestContext context = context();
        Account otherClearing = clearingAccount(404L);

        assertThatThrownBy(() -> context.factory().create(
                context.transaction(),
                otherClearing,
                CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "clearingAccount does not match configured outbound account");
    }

    @Test
    void rejectsConfiguredAccountWithWrongContract() {
        TestContext context = context();
        when(context.clearingAccount().getAccountType())
                .thenReturn(AccountType.OPENING_BALANCE_CONTROL);

        assertThatThrownBy(() -> context.factory().create(
                context.transaction(),
                context.clearingAccount(),
                CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "clearingAccount must be an active INR OUTBOUND_CLEARING account");
    }

    @Test
    void rejectsTransactionThatIsNotReleased() {
        TestContext context = context();
        when(context.transaction().getState())
                .thenReturn(TransactionState.PROTECTED);

        assertThatThrownBy(() -> context.factory().create(
                context.transaction(),
                context.clearingAccount(),
                CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("transaction must be RELEASED");
    }

    @Test
    void requiresConfiguredClearingAccountBeforeConstruction() {
        SettlementProcessorProperties disabledProperties =
                new SettlementProcessorProperties(
                        false,
                        Duration.ofSeconds(1),
                        25,
                        null,
                        approvedDelays());

        SettlementPostingFactory factory =
                new SettlementPostingFactory(disabledProperties);

        TestContext context = context();

        assertThatThrownBy(() -> factory.create(
                context.transaction(),
                context.clearingAccount(),
                CREATED_AT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "outboundClearingAccountId is not configured");
    }

    private static TestContext context() {
        Account sourceAccount = customerAccount(202L);
        Account clearingAccount = clearingAccount(303L);

        TransactionDb transaction = mock(TransactionDb.class);
        when(transaction.getTransactionId()).thenReturn(101L);
        when(transaction.getTransactionReference())
                .thenReturn("TX-101");
        when(transaction.getState())
                .thenReturn(TransactionState.RELEASED);
        when(transaction.getAmount())
                .thenReturn(new BigDecimal("250.00"));
        when(transaction.getCurrencyCode())
                .thenReturn(CurrencyCode.INR);
        when(transaction.getSourceAccount())
                .thenReturn(sourceAccount);

        SettlementProcessorProperties properties =
                new SettlementProcessorProperties(
                        true,
                        Duration.ofSeconds(1),
                        25,
                        303L,
                        approvedDelays());

        return new TestContext(
                new SettlementPostingFactory(properties),
                transaction,
                sourceAccount,
                clearingAccount);
    }

    private static Account customerAccount(Long accountId) {
        Account account = mock(Account.class);
        when(account.getAccountId()).thenReturn(accountId);
        when(account.isCustomerOwnedAccount()).thenReturn(true);
        when(account.isActive()).thenReturn(true);
        when(account.getCurrencyCode()).thenReturn(CurrencyCode.INR);
        return account;
    }

    private static Account clearingAccount(Long accountId) {
        Account account = mock(Account.class);
        when(account.getAccountId()).thenReturn(accountId);
        when(account.getAccountType())
                .thenReturn(AccountType.OUTBOUND_CLEARING);
        when(account.isActive()).thenReturn(true);
        when(account.getCurrencyCode())
                .thenReturn(CurrencyCode.INR);
        return account;
    }

    private static List<Duration> approvedDelays() {
        return List.of(
                Duration.ofSeconds(5),
                Duration.ofSeconds(30),
                Duration.ofMinutes(1));
    }

    private record TestContext(
            SettlementPostingFactory factory,
            TransactionDb transaction,
            Account sourceAccount,
            Account clearingAccount) {
    }
}
