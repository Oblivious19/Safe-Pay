package com.ofss.beans;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Objects;

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
        name = "TRANSACTION_EXCEPTION",
        schema = "SAFEPAY_OWNER",
        uniqueConstraints = @UniqueConstraint(
                name = "UK_TRANSACTION_EXCEPTION_REF",
                columnNames = "EXCEPTION_REFERENCE"))
@SequenceGenerator(
        name = "transactionExceptionSequence",
        sequenceName =
                "SAFEPAY_OWNER.SEQ_TRANSACTION_EXCEPTION_ID",
        allocationSize = 1)
public class TransactionExceptionLog {

    private static final String YES = "Y";
    private static final String NO = "N";
    private static final int MAX_AUTOMATIC_RETRIES = 3;

    @Id
    @GeneratedValue(
            strategy = GenerationType.SEQUENCE,
            generator = "transactionExceptionSequence")
    @Column(
            name = "TRANSACTION_EXCEPTION_ID",
            nullable = false,
            updatable = false)
    private Long transactionExceptionId;

    @Column(
            name = "EXCEPTION_REFERENCE",
            nullable = false,
            updatable = false,
            length = 64)
    private String exceptionReference;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false)
    @JoinColumn(
            name = "TRANSACTION_ID",
            nullable = false,
            updatable = false)
    private TransactionDb transaction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "POSTING_ID",
            updatable = false)
    private LedgerPosting posting;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "PROCESSING_STAGE",
            nullable = false,
            updatable = false,
            length = 30)
    private TransactionProcessingStage processingStage;

    @Column(
            name = "ERROR_CODE",
            nullable = false,
            length = 100)
    private String errorCode;

    @Column(
            name = "ERROR_MESSAGE",
            nullable = false,
            length = 2000)
    private String errorMessage;

    @Column(
            name = "RETRYABLE_FLAG",
            nullable = false,
            updatable = false,
            length = 1,
            columnDefinition = "CHAR(1)")
    private String retryableFlag;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "STATUS",
            nullable = false,
            length = 30)
    private TransactionExceptionStatus status;

    @Column(
            name = "RETRY_COUNT",
            nullable = false,
            precision = 10,
            scale = 0)
    private int retryCount;

    @Column(name = "NEXT_RETRY_AT")
    private OffsetDateTime nextRetryAt;

    @Column(
            name = "FIRST_OCCURRED_AT",
            nullable = false,
            updatable = false)
    private OffsetDateTime firstOccurredAt;

    @Column(
            name = "LAST_OCCURRED_AT",
            nullable = false)
    private OffsetDateTime lastOccurredAt;

    @Column(name = "RESOLVED_AT")
    private OffsetDateTime resolvedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "RESOLVED_BY_USER_ID")
    private User resolvedByUser;

    @Column(
            name = "RESOLUTION_NOTE",
            length = 2000)
    private String resolutionNote;

    @Column(
            name = "CORRELATION_ID",
            nullable = false,
            updatable = false,
            length = 64)
    private String correlationId;

    @Column(
            name = "CREATED_AT",
            nullable = false,
            updatable = false)
    private OffsetDateTime createdAt;

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

    protected TransactionExceptionLog() {
        // Required by JPA.
    }

    public static TransactionExceptionLog open(
            String exceptionReference,
            TransactionDb transaction,
            LedgerPosting posting,
            TransactionProcessingStage processingStage,
            String errorCode,
            String errorMessage,
            boolean retryable,
            String correlationId,
            OffsetDateTime occurredAt) {

        requirePersistedTransaction(transaction);
        requireMatchingPosting(transaction, posting);

        OffsetDateTime timestamp = requireUtcTimestamp(
                occurredAt,
                "occurredAt");

        TransactionExceptionLog exception =
                new TransactionExceptionLog();

        exception.exceptionReference = requireText(
                exceptionReference,
                "exceptionReference",
                64);
        exception.transaction = transaction;
        exception.posting = posting;
        exception.processingStage = Objects.requireNonNull(
                processingStage,
                "processingStage is required");
        exception.errorCode = requireUppercaseText(
                errorCode,
                "errorCode",
                100);
        exception.errorMessage = requireText(
                errorMessage,
                "errorMessage",
                2000);
        exception.retryableFlag = retryable ? YES : NO;
        exception.status = TransactionExceptionStatus.OPEN;
        exception.retryCount = 0;
        exception.firstOccurredAt = timestamp;
        exception.lastOccurredAt = timestamp;
        exception.correlationId = requireText(
                correlationId,
                "correlationId",
                64);
        exception.createdAt = timestamp;
        exception.updatedAt = timestamp;

        return exception;
    }

    public void scheduleRetry(
            int retryAttempt,
            OffsetDateTime occurredAt,
            OffsetDateTime nextRetryAt) {

        requireUnresolved();

        if (!isRetryable()) {
            throw new IllegalStateException(
                    "A non-retryable exception cannot be scheduled");
        }

        if (retryAttempt != retryCount + 1) {
            throw new IllegalArgumentException(
                    "retryAttempt must increment retryCount by one");
        }

        OffsetDateTime occurrence = requireOccurrenceTime(occurredAt);
        OffsetDateTime nextRetry = requireUtcTimestamp(
                nextRetryAt,
                "nextRetryAt");

        if (nextRetry.isBefore(occurrence)) {
            throw new IllegalArgumentException(
                    "nextRetryAt cannot be before occurredAt");
        }

        status = TransactionExceptionStatus.RETRY_PENDING;
        retryCount = retryAttempt;
        this.nextRetryAt = nextRetry;
        lastOccurredAt = occurrence;
        updatedAt = occurrence;
    }

    public void moveToManualReview(
            int completedRetryCount,
            OffsetDateTime occurredAt) {

        requireUnresolved();

        if (completedRetryCount < retryCount) {
            throw new IllegalArgumentException(
                    "completedRetryCount cannot decrease retryCount");
        }

        if (isRetryable()
                && completedRetryCount
                        != MAX_AUTOMATIC_RETRIES) {
            throw new IllegalArgumentException(
                    "retryable exception requires exactly 3 completed retries before manual review");
        }

        OffsetDateTime occurrence = requireOccurrenceTime(occurredAt);

        status = TransactionExceptionStatus.MANUAL_REVIEW;
        retryCount = completedRetryCount;
        nextRetryAt = null;
        lastOccurredAt = occurrence;
        updatedAt = occurrence;
    }

    public void resolve(
            User resolvedByUser,
            String resolutionNote,
            OffsetDateTime resolvedAt) {

        requireUnresolved();
        requirePersistedUser(resolvedByUser);

        OffsetDateTime timestamp = requireOccurrenceTime(resolvedAt);

        status = TransactionExceptionStatus.RESOLVED;
        nextRetryAt = null;
        this.resolvedByUser = resolvedByUser;
        this.resolutionNote = requireText(
                resolutionNote,
                "resolutionNote",
                2000);
        this.resolvedAt = timestamp;
        lastOccurredAt = timestamp;
        updatedAt = timestamp;
    }

    private void requireUnresolved() {
        if (status.isResolved()) {
            throw new IllegalStateException(
                    "A resolved transaction exception cannot change");
        }
    }

    private OffsetDateTime requireOccurrenceTime(
            OffsetDateTime value) {

        OffsetDateTime timestamp = requireUtcTimestamp(
                value,
                "occurredAt");

        if (timestamp.isBefore(firstOccurredAt)
                || timestamp.isBefore(lastOccurredAt)) {
            throw new IllegalArgumentException(
                    "occurredAt cannot move backwards");
        }

        return timestamp;
    }

    private static void requirePersistedTransaction(
            TransactionDb transaction) {

        Objects.requireNonNull(
                transaction,
                "transaction is required");

        if (transaction.getTransactionId() == null
                || transaction.getTransactionId() <= 0L) {
            throw new IllegalArgumentException(
                    "transaction must already be persisted");
        }
    }

    private static void requireMatchingPosting(
            TransactionDb transaction,
            LedgerPosting posting) {

        if (posting == null) {
            return;
        }

        TransactionDb postingTransaction = posting.getTransaction();

        if (postingTransaction == null
                || !Objects.equals(
                        transaction.getTransactionId(),
                        postingTransaction.getTransactionId())) {
            throw new IllegalArgumentException(
                    "posting must belong to transaction");
        }
    }

    private static void requirePersistedUser(User user) {
        Objects.requireNonNull(user, "resolvedByUser is required");

        if (user.getUserId() == null
                || user.getUserId() <= 0L) {
            throw new IllegalArgumentException(
                    "resolvedByUser must already be persisted");
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

    public Long getTransactionExceptionId() {
        return transactionExceptionId;
    }

    public String getExceptionReference() {
        return exceptionReference;
    }

    public TransactionDb getTransaction() {
        return transaction;
    }

    public LedgerPosting getPosting() {
        return posting;
    }

    public TransactionProcessingStage getProcessingStage() {
        return processingStage;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public boolean isRetryable() {
        return YES.equals(retryableFlag);
    }

    public TransactionExceptionStatus getStatus() {
        return status;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public OffsetDateTime getNextRetryAt() {
        return nextRetryAt;
    }

    public OffsetDateTime getFirstOccurredAt() {
        return firstOccurredAt;
    }

    public OffsetDateTime getLastOccurredAt() {
        return lastOccurredAt;
    }

    public OffsetDateTime getResolvedAt() {
        return resolvedAt;
    }

    public User getResolvedByUser() {
        return resolvedByUser;
    }

    public String getResolutionNote() {
        return resolutionNote;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public long getVersionNo() {
        return versionNo;
    }
}
