package com.ofss.beans;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

import org.hibernate.annotations.Immutable;
import org.junit.jupiter.api.Test;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

class LedgerEntryTest {

    private static final OffsetDateTime INPUT_TIME =
            OffsetDateTime.parse(
                    "2026-09-16T16:30:15.123456789+05:30");

    private static final OffsetDateTime UTC_TIME =
            OffsetDateTime.parse(
                    "2026-09-16T11:00:15.123456Z");

    @Test
    void mapsImmutableCanonicalTableAndSequence() {
        assertThat(LedgerEntry.class.getAnnotation(Entity.class))
                .isNotNull();
        assertThat(LedgerEntry.class.getAnnotation(Immutable.class))
                .isNotNull();

        Table table = LedgerEntry.class.getAnnotation(Table.class);
        assertThat(table.name()).isEqualTo("LEDGER_ENTRY");
        assertThat(table.schema()).isEqualTo("SAFEPAY_OWNER");
        assertThat(table.uniqueConstraints())
                .extracting(constraint -> constraint.name())
                .containsExactly(
                        "UK_LEDGER_ENTRY_POSTING_LINE",
                        "UK_LEDGER_ENTRY_POSTING_SIDE",
                        "UK_LEDGER_ENTRY_IDEMP_SIDE");

        SequenceGenerator sequence = LedgerEntry.class
                .getAnnotation(SequenceGenerator.class);
        assertThat(sequence.name())
                .isEqualTo("ledgerEntrySequence");
        assertThat(sequence.sequenceName())
                .isEqualTo(
                        "SAFEPAY_OWNER.SEQ_LEDGER_ENTRY_ID");
        assertThat(sequence.allocationSize()).isEqualTo(1);
    }

    @Test
    void mapsPostingTransactionAccountEnumsAndMoney()
            throws Exception {

        assertRelationship(
                "posting",
                "POSTING_ID",
                false);
        assertRelationship(
                "transaction",
                "TRANSACTION_ID",
                true);
        assertRelationship(
                "account",
                "ACCOUNT_ID",
                false);

        assertEnumColumn("entryType", "ENTRY_TYPE", 10);
        assertEnumColumn("currencyCode", "CURRENCY_CODE", 3);
        assertEnumColumn("status", "STATUS", 20);

        Column amount = LedgerEntry.class
                .getDeclaredField("amount")
                .getAnnotation(Column.class);
        assertThat(amount.precision()).isEqualTo(18);
        assertThat(amount.scale()).isEqualTo(2);
        assertThat(amount.updatable()).isFalse();
    }

    @Test
    void derivesDebitIdentityEntirelyFromPosting() {
        TestContext context = context();

        LedgerEntry debit =
                LedgerEntry.createPaymentSettlementDebit(
                        context.posting(),
                        context.sourceAccount(),
                        INPUT_TIME);

        assertThat(debit.getPosting())
                .isSameAs(context.posting());
        assertThat(debit.getTransaction())
                .isSameAs(context.transaction());
        assertThat(debit.getSourceSystem())
                .isEqualTo(context.posting().getSourceSystem());
        assertThat(debit.getIdempotencyKey())
                .isEqualTo(context.posting().getIdempotencyKey());
        assertThat(debit.getLineNumber()).isEqualTo(1);
        assertThat(debit.getEntryType())
                .isEqualTo(LedgerEntryType.DEBIT);
        assertThat(debit.getAccount())
                .isSameAs(context.sourceAccount());
        assertThat(debit.getAmount())
                .isEqualByComparingTo(
                        context.posting().getAmount());
        assertThat(debit.getCurrencyCode())
                .isEqualTo(CurrencyCode.INR);
        assertThat(debit.getStatus())
                .isEqualTo(LedgerEntryStatus.POSTED);
        assertThat(debit.getCreatedAt()).isEqualTo(UTC_TIME);
    }

    @Test
    void derivesCreditIdentityEntirelyFromPosting() {
        TestContext context = context();

        LedgerEntry credit =
                LedgerEntry.createPaymentSettlementCredit(
                        context.posting(),
                        context.clearingAccount(),
                        INPUT_TIME);

        assertThat(credit.getPosting())
                .isSameAs(context.posting());
        assertThat(credit.getLineNumber()).isEqualTo(2);
        assertThat(credit.getEntryType())
                .isEqualTo(LedgerEntryType.CREDIT);
        assertThat(credit.getAccount())
                .isSameAs(context.clearingAccount());
        assertThat(credit.getAmount())
                .isEqualByComparingTo(
                        context.posting().getAmount());
        assertThat(credit.getIdempotencyKey())
                .isEqualTo(context.posting().getIdempotencyKey());
    }

    @Test
    void rejectsDebitForDifferentOrIneligibleSourceAccount() {
        TestContext context = context();
        Account different = customerAccount(303L);

        assertThatThrownBy(() ->
                LedgerEntry.createPaymentSettlementDebit(
                        context.posting(),
                        different,
                        INPUT_TIME))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "sourceAccount must match transaction source account");

        when(context.sourceAccount().isActive())
                .thenReturn(false);

        assertThatThrownBy(() ->
                LedgerEntry.createPaymentSettlementDebit(
                        context.posting(),
                        context.sourceAccount(),
                        INPUT_TIME))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "sourceAccount must be an active INR customer account");
    }

    @Test
    void rejectsCreditForNonClearingOrSameAccount() {
        TestContext context = context();
        Account wrongType = mock(Account.class);
        when(wrongType.getAccountId()).thenReturn(404L);
        when(wrongType.getAccountType())
                .thenReturn(AccountType.OPENING_BALANCE_CONTROL);
        when(wrongType.isActive()).thenReturn(true);
        when(wrongType.getCurrencyCode())
                .thenReturn(CurrencyCode.INR);

        assertThatThrownBy(() ->
                LedgerEntry.createPaymentSettlementCredit(
                        context.posting(),
                        wrongType,
                        INPUT_TIME))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "clearingAccount must be an active INR OUTBOUND_CLEARING account");

        Account sameIdClearing = clearingAccount(202L);

        assertThatThrownBy(() ->
                LedgerEntry.createPaymentSettlementCredit(
                        context.posting(),
                        sameIdClearing,
                        INPUT_TIME))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "clearingAccount must differ from sourceAccount");
    }

    @Test
    void acceptsOnlyPendingPaymentPosting() {
        TestContext context = context();
        context.posting().markPosted(INPUT_TIME.plusSeconds(1));

        assertThatThrownBy(() ->
                LedgerEntry.createPaymentSettlementDebit(
                        context.posting(),
                        context.sourceAccount(),
                        INPUT_TIME))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "posting must be a pending payment settlement");
    }

    @Test
    void entryTypeOwnsCanonicalLineNumber() {
        assertThat(LedgerEntryType.DEBIT.lineNumber())
                .isEqualTo(1);
        assertThat(LedgerEntryType.CREDIT.lineNumber())
                .isEqualTo(2);
    }

    private static TestContext context() {
        Account source = customerAccount(202L);
        Account clearing = clearingAccount(303L);

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
        when(transaction.getSourceAccount()).thenReturn(source);

        LedgerPosting posting =
                LedgerPosting.createPaymentSettlement(
                        transaction,
                        "PAYMENT-SETTLEMENT-101",
                        "TRANSACTION_SETTLE:TX-101",
                        INPUT_TIME);

        return new TestContext(
                transaction,
                source,
                clearing,
                posting);
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
        when(account.getCurrencyCode()).thenReturn(CurrencyCode.INR);
        return account;
    }

    private static void assertRelationship(
            String fieldName,
            String columnName,
            boolean optional) throws Exception {

        Field field = LedgerEntry.class.getDeclaredField(fieldName);
        ManyToOne relationship = field.getAnnotation(ManyToOne.class);
        JoinColumn joinColumn = field.getAnnotation(JoinColumn.class);

        assertThat(relationship.fetch()).isEqualTo(FetchType.LAZY);
        assertThat(relationship.optional()).isEqualTo(optional);
        assertThat(joinColumn.name()).isEqualTo(columnName);
        assertThat(joinColumn.nullable()).isEqualTo(optional);
        assertThat(joinColumn.updatable()).isFalse();
    }

    private static void assertEnumColumn(
            String fieldName,
            String columnName,
            int length) throws Exception {

        Field field = LedgerEntry.class.getDeclaredField(fieldName);
        assertThat(field.getAnnotation(Enumerated.class).value())
                .isEqualTo(EnumType.STRING);

        Column column = field.getAnnotation(Column.class);
        assertThat(column.name()).isEqualTo(columnName);
        assertThat(column.length()).isEqualTo(length);
        assertThat(column.updatable()).isFalse();
    }

    private record TestContext(
            TransactionDb transaction,
            Account sourceAccount,
            Account clearingAccount,
            LedgerPosting posting) {
    }
}
