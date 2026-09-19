package com.ofss.beans;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import org.hibernate.annotations.Immutable;

import com.ofss.common.MoneyUtility;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Immutable
@Table(
        name = "LEDGER_ENTRY",
        schema = "SAFEPAY_OWNER",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "UK_LEDGER_ENTRY_POSTING_LINE",
                        columnNames = {
                                "POSTING_ID",
                                "LINE_NUMBER"
                        }),
                @UniqueConstraint(
                        name = "UK_LEDGER_ENTRY_POSTING_SIDE",
                        columnNames = {
                                "POSTING_ID",
                                "ENTRY_TYPE"
                        }),
                @UniqueConstraint(
                        name = "UK_LEDGER_ENTRY_IDEMP_SIDE",
                        columnNames = {
                                "SOURCE_SYSTEM",
                                "IDEMPOTENCY_KEY",
                                "ENTRY_TYPE"
                        })
        })
@SequenceGenerator(
        name = "ledgerEntrySequence",
        sequenceName = "SAFEPAY_OWNER.SEQ_LEDGER_ENTRY_ID",
        allocationSize = 1)
public class LedgerEntry {

    @Id
    @GeneratedValue(
            strategy = GenerationType.SEQUENCE,
            generator = "ledgerEntrySequence")
    @Column(
            name = "LEDGER_ENTRY_ID",
            nullable = false,
            updatable = false)
    private Long ledgerEntryId;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false)
    @JoinColumn(
            name = "POSTING_ID",
            nullable = false,
            updatable = false)
    private LedgerPosting posting;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "TRANSACTION_ID",
            updatable = false)
    private TransactionDb transaction;

    @Column(
            name = "SOURCE_SYSTEM",
            nullable = false,
            updatable = false,
            length = 30)
    private String sourceSystem;

    @Column(
            name = "IDEMPOTENCY_KEY",
            nullable = false,
            updatable = false,
            length = 128)
    private String idempotencyKey;

    @Column(
            name = "LINE_NUMBER",
            nullable = false,
            updatable = false,
            precision = 2,
            scale = 0)
    private int lineNumber;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false)
    @JoinColumn(
            name = "ACCOUNT_ID",
            nullable = false,
            updatable = false)
    private Account account;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "ENTRY_TYPE",
            nullable = false,
            updatable = false,
            length = 10)
    private LedgerEntryType entryType;

    @Column(
            name = "AMOUNT",
            nullable = false,
            updatable = false,
            precision = MoneyUtility.MONEY_PRECISION,
            scale = MoneyUtility.MONEY_SCALE)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "CURRENCY_CODE",
            nullable = false,
            updatable = false,
            length = 3)
    private CurrencyCode currencyCode;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "STATUS",
            nullable = false,
            updatable = false,
            length = 20)
    private LedgerEntryStatus status;

    @Column(
            name = "DESCRIPTION",
            updatable = false,
            length = 500)
    private String description;

    @Column(
            name = "CREATED_AT",
            nullable = false,
            updatable = false)
    private OffsetDateTime createdAt;

    protected LedgerEntry() {
        // Required by JPA.
    }

    public static LedgerEntry createPaymentSettlementDebit(
            LedgerPosting posting,
            Account sourceAccount,
            OffsetDateTime createdAt) {

        requirePaymentSettlement(posting);
        requirePersistedAccount(sourceAccount, "sourceAccount");

        TransactionDb transaction = posting.getTransaction();
        Account transactionSource = Objects.requireNonNull(
                transaction.getSourceAccount(),
                "transaction sourceAccount is required");

        if (!Objects.equals(
                sourceAccount.getAccountId(),
                transactionSource.getAccountId())) {
            throw new IllegalArgumentException(
                    "sourceAccount must match transaction source account");
        }

        if (!sourceAccount.isCustomerOwnedAccount()
                || !sourceAccount.isActive()
                || sourceAccount.getCurrencyCode() != CurrencyCode.INR) {
            throw new IllegalArgumentException(
                    "sourceAccount must be an active INR customer account");
        }

        return create(
                posting,
                sourceAccount,
                LedgerEntryType.DEBIT,
                "Simulated payment settlement debit",
                createdAt);
    }

    public static LedgerEntry createPaymentSettlementCredit(
            LedgerPosting posting,
            Account clearingAccount,
            OffsetDateTime createdAt) {

        requirePaymentSettlement(posting);
        requirePersistedAccount(clearingAccount, "clearingAccount");

        if (clearingAccount.getAccountType()
                        != AccountType.OUTBOUND_CLEARING
                || !clearingAccount.isActive()
                || clearingAccount.getCurrencyCode()
                        != CurrencyCode.INR
                || clearingAccount.getOwner() != null) {
            throw new IllegalArgumentException(
                    "clearingAccount must be an active INR OUTBOUND_CLEARING account");
        }

        if (Objects.equals(
                posting.getTransaction()
                        .getSourceAccount()
                        .getAccountId(),
                clearingAccount.getAccountId())) {
            throw new IllegalArgumentException(
                    "clearingAccount must differ from sourceAccount");
        }

        return create(
                posting,
                clearingAccount,
                LedgerEntryType.CREDIT,
                "Simulated outbound clearing credit",
                createdAt);
    }

    private static LedgerEntry create(
            LedgerPosting posting,
            Account account,
            LedgerEntryType entryType,
            String description,
            OffsetDateTime createdAt) {

        LedgerEntry entry = new LedgerEntry();

        entry.posting = posting;
        entry.transaction = posting.getTransaction();
        entry.sourceSystem = posting.getSourceSystem();
        entry.idempotencyKey = posting.getIdempotencyKey();
        entry.lineNumber = entryType.lineNumber();
        entry.account = account;
        entry.entryType = entryType;
        entry.amount = posting.getAmount();
        entry.currencyCode = posting.getCurrencyCode();
        entry.status = LedgerEntryStatus.POSTED;
        entry.description = requireOptionalText(
                description,
                "description",
                500);
        entry.createdAt = requireUtcTimestamp(
                createdAt,
                "createdAt");

        return entry;
    }

    private static void requirePaymentSettlement(
            LedgerPosting posting) {

        Objects.requireNonNull(posting, "posting is required");

        if (posting.getPostingType()
                        != LedgerPostingType.PAYMENT_SETTLEMENT
                || posting.getStatus()
                        != LedgerPostingStatus.PENDING
                || posting.getTransaction() == null) {
            throw new IllegalArgumentException(
                    "posting must be a pending payment settlement");
        }
    }

    private static void requirePersistedAccount(
            Account account,
            String fieldName) {

        Objects.requireNonNull(
                account,
                fieldName + " is required");

        if (account.getAccountId() == null
                || account.getAccountId() <= 0L) {
            throw new IllegalArgumentException(
                    fieldName + " must already be persisted");
        }
    }

    private static String requireOptionalText(
            String value,
            String fieldName,
            int maximumLength) {

        if (value == null) {
            return null;
        }

        String normalizedValue = value.trim();

        if (normalizedValue.isEmpty()) {
            throw new IllegalArgumentException(
                    fieldName + " cannot be blank");
        }

        if (normalizedValue.length() > maximumLength) {
            throw new IllegalArgumentException(
                    fieldName + " has an invalid length");
        }

        return normalizedValue;
    }

    private static OffsetDateTime requireUtcTimestamp(
            OffsetDateTime value,
            String fieldName) {

        return Objects.requireNonNull(
                        value,
                        fieldName + " is required")
                .withOffsetSameInstant(ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.MICROS);
    }

    public Long getLedgerEntryId() {
        return ledgerEntryId;
    }

    public LedgerPosting getPosting() {
        return posting;
    }

    public TransactionDb getTransaction() {
        return transaction;
    }

    public String getSourceSystem() {
        return sourceSystem;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public Account getAccount() {
        return account;
    }

    public LedgerEntryType getEntryType() {
        return entryType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public CurrencyCode getCurrencyCode() {
        return currencyCode;
    }

    public LedgerEntryStatus getStatus() {
        return status;
    }

    public String getDescription() {
        return description;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
