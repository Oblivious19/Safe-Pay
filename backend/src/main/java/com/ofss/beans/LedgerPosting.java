package com.ofss.beans;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Objects;

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
import jakarta.persistence.Version;

@Entity
@Table(
        name = "LEDGER_POSTING",
        schema = "SAFEPAY_OWNER",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "UK_LEDGER_POSTING_REFERENCE",
                        columnNames = "POSTING_REFERENCE"),
                @UniqueConstraint(
                        name = "UK_LEDGER_POSTING_IDEMPOTENCY",
                        columnNames = {
                                "SOURCE_SYSTEM",
                                "IDEMPOTENCY_KEY"
                        }),
                @UniqueConstraint(
                        name = "UK_LEDGER_POSTING_TRANSACTION",
                        columnNames = "TRANSACTION_ID")
        })
@SequenceGenerator(
        name = "ledgerPostingSequence",
        sequenceName = "SAFEPAY_OWNER.SEQ_LEDGER_POSTING_ID",
        allocationSize = 1)
public class LedgerPosting {

    public static final String SOURCE_SYSTEM = "SAFEPAY";
    public static final int EXPECTED_ENTRY_COUNT = 2;

    @Id
    @GeneratedValue(
            strategy = GenerationType.SEQUENCE,
            generator = "ledgerPostingSequence")
    @Column(
            name = "POSTING_ID",
            nullable = false,
            updatable = false)
    private Long postingId;

    @Column(
            name = "POSTING_REFERENCE",
            nullable = false,
            updatable = false,
            length = 64)
    private String postingReference;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "POSTING_TYPE",
            nullable = false,
            updatable = false,
            length = 30)
    private LedgerPostingType postingType;

    @ManyToOne(
            fetch = FetchType.LAZY)
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

    @Column(
            name = "EXPECTED_ENTRY_COUNT",
            nullable = false,
            updatable = false,
            precision = 3,
            scale = 0)
    private int expectedEntryCount;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "STATUS",
            nullable = false,
            length = 20)
    private LedgerPostingStatus status;

    @Column(
            name = "CREATED_AT",
            nullable = false,
            updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "POSTED_AT")
    private OffsetDateTime postedAt;

    @Column(name = "FAILED_AT")
    private OffsetDateTime failedAt;

    @Column(
            name = "FAILURE_CODE",
            length = 100)
    private String failureCode;

    @Column(
            name = "UPDATED_AT",
            nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(
            name = "VERSION_NO",
            nullable = false,
            precision = 10,
            scale = 0)
    private long versionNo;

    protected LedgerPosting() {
        // Required by JPA.
    }

    public static LedgerPosting createPaymentSettlement(
            TransactionDb transaction,
            String postingReference,
            String idempotencyKey,
            OffsetDateTime createdAt) {

        requireReleasedPersistedTransaction(transaction);

        if (transaction.getCurrencyCode() != CurrencyCode.INR) {
            throw new IllegalArgumentException(
                    "transaction must use INR");
        }

        LedgerPosting posting = new LedgerPosting();

        posting.postingReference = requireText(
                postingReference,
                "postingReference",
                64);
        posting.postingType = LedgerPostingType.PAYMENT_SETTLEMENT;
        posting.transaction = transaction;
        posting.sourceSystem = SOURCE_SYSTEM;
        posting.idempotencyKey = requireText(
                idempotencyKey,
                "idempotencyKey",
                128);
        posting.amount = MoneyUtility.requireValidTransactionAmount(
                transaction.getAmount());
        posting.currencyCode = transaction.getCurrencyCode();
        posting.expectedEntryCount = EXPECTED_ENTRY_COUNT;
        posting.status = LedgerPostingStatus.PENDING;
        posting.createdAt = requireUtcTimestamp(
                createdAt,
                "createdAt");
        posting.updatedAt = posting.createdAt;

        return posting;
    }

    public void markPosted(OffsetDateTime postedAt) {
        requirePending();

        OffsetDateTime timestamp = requireNotBeforeCreated(
                postedAt,
                "postedAt");

        status = LedgerPostingStatus.POSTED;
        this.postedAt = timestamp;
        updatedAt = timestamp;
    }

    public void markFailed(
            String failureCode,
            OffsetDateTime failedAt) {

        requirePending();

        OffsetDateTime timestamp = requireNotBeforeCreated(
                failedAt,
                "failedAt");

        status = LedgerPostingStatus.FAILED;
        this.failureCode = requireUppercaseText(
                failureCode,
                "failureCode",
                100);
        this.failedAt = timestamp;
        updatedAt = timestamp;
    }

    private void requirePending() {
        if (status != LedgerPostingStatus.PENDING) {
            throw new IllegalStateException(
                    "Only a pending ledger posting can change");
        }
    }

    private OffsetDateTime requireNotBeforeCreated(
            OffsetDateTime value,
            String fieldName) {

        OffsetDateTime timestamp = requireUtcTimestamp(
                value,
                fieldName);

        if (timestamp.isBefore(createdAt)) {
            throw new IllegalArgumentException(
                    fieldName + " cannot be before createdAt");
        }

        return timestamp;
    }

    private static void requireReleasedPersistedTransaction(
            TransactionDb transaction) {

        Objects.requireNonNull(
                transaction,
                "transaction is required");

        if (transaction.getTransactionId() == null
                || transaction.getTransactionId() <= 0L) {
            throw new IllegalArgumentException(
                    "transaction must already be persisted");
        }

        if (transaction.getState() != TransactionState.RELEASED) {
            throw new IllegalArgumentException(
                    "transaction must be RELEASED");
        }
    }

    private static String requireText(
            String value,
            String fieldName,
            int maximumLength) {

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + " is required");
        }

        String normalizedValue = value.trim();

        if (normalizedValue.length() > maximumLength) {
            throw new IllegalArgumentException(
                    fieldName + " has an invalid length");
        }

        return normalizedValue;
    }

    private static String requireUppercaseText(
            String value,
            String fieldName,
            int maximumLength) {

        return requireText(value, fieldName, maximumLength)
                .toUpperCase(Locale.ROOT);
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

    public Long getPostingId() {
        return postingId;
    }

    public String getPostingReference() {
        return postingReference;
    }

    public LedgerPostingType getPostingType() {
        return postingType;
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

    public BigDecimal getAmount() {
        return amount;
    }

    public CurrencyCode getCurrencyCode() {
        return currencyCode;
    }

    public int getExpectedEntryCount() {
        return expectedEntryCount;
    }

    public LedgerPostingStatus getStatus() {
        return status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getPostedAt() {
        return postedAt;
    }

    public OffsetDateTime getFailedAt() {
        return failedAt;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public long getVersionNo() {
        return versionNo;
    }
}
