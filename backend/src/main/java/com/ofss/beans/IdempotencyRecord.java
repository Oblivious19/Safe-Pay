package com.ofss.beans;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

@Entity
@Table(
        name = "IDEMPOTENCY_RECORD",
        schema = "SAFEPAY_OWNER",
        uniqueConstraints = @UniqueConstraint(
                name = "UK_IDEMPOTENCY_REQUEST",
                columnNames = {
                        "USER_ID",
                        "OPERATION_CODE",
                        "IDEMPOTENCY_KEY"
                }))
@SequenceGenerator(
        name = "idempotencyRecordSequence",
        sequenceName =
                "SAFEPAY_OWNER.SEQ_IDEMPOTENCY_RECORD_ID",
        allocationSize = 1)
public class IdempotencyRecord {

    private static final Pattern SHA_256_PATTERN =
            Pattern.compile("[0-9a-f]{64}");

    @Id
    @GeneratedValue(
            strategy = GenerationType.SEQUENCE,
            generator = "idempotencyRecordSequence")
    @Column(
            name = "IDEMPOTENCY_RECORD_ID",
            nullable = false,
            updatable = false)
    private Long idempotencyRecordId;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false)
    @JoinColumn(
            name = "USER_ID",
            nullable = false,
            updatable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "OPERATION_CODE",
            nullable = false,
            updatable = false,
            length = 50)
    private IdempotencyOperation operationCode;

    @Column(
            name = "IDEMPOTENCY_KEY",
            nullable = false,
            updatable = false,
            length = 128)
    private String idempotencyKey;

    @Column(
            name = "REQUEST_HASH",
            nullable = false,
            updatable = false,
            length = 64)
    private String requestHash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "TRANSACTION_ID")
    private TransactionDb transaction;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "STATUS",
            nullable = false,
            length = 20)
    private IdempotencyStatus status;

    @Column(
            name = "HTTP_STATUS",
            precision = 3,
            scale = 0)
    private Integer httpStatus;

    @Lob
    @Column(name = "RESPONSE_BODY")
    private String responseBody;

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

    @Column(name = "COMPLETED_AT")
    private OffsetDateTime completedAt;

    @Column(
            name = "EXPIRES_AT",
            nullable = false,
            updatable = false)
    private OffsetDateTime expiresAt;

    @Version
    @Column(
            name = "VERSION_NO",
            nullable = false,
            precision = 10,
            scale = 0)
    private long versionNo;

    protected IdempotencyRecord() {
        // Required by JPA.
    }

    public static IdempotencyRecord begin(
            User user,
            IdempotencyOperation operationCode,
            String idempotencyKey,
            String requestHash,
            String correlationId,
            OffsetDateTime createdAt,
            OffsetDateTime expiresAt) {

        requirePersistedUser(user);

        OffsetDateTime normalizedCreatedAt =
                requireUtcTimestamp(createdAt, "createdAt");

        OffsetDateTime normalizedExpiresAt =
                requireUtcTimestamp(expiresAt, "expiresAt");

        if (!normalizedExpiresAt.isAfter(
                normalizedCreatedAt)) {
            throw new IllegalArgumentException(
                    "expiresAt must be after createdAt");
        }

        IdempotencyRecord record = new IdempotencyRecord();

        record.user = user;
        record.operationCode = Objects.requireNonNull(
                operationCode,
                "operationCode is required");
        record.idempotencyKey = requireText(
                idempotencyKey,
                "idempotencyKey",
                128);
        record.requestHash = requireSha256(requestHash);
        record.correlationId = requireText(
                correlationId,
                "correlationId",
                64);
        record.status = IdempotencyStatus.IN_PROGRESS;
        record.createdAt = normalizedCreatedAt;
        record.expiresAt = normalizedExpiresAt;

        return record;
    }

    public void linkTransaction(TransactionDb transaction) {
        requireInProgress();
        requirePersistedTransaction(transaction);

        Long transactionOwnerId = Objects.requireNonNull(
                        transaction.getCustomer(),
                        "transaction customer is required")
                .getUserId();

        if (!Objects.equals(
                user.getUserId(),
                transactionOwnerId)) {
            throw new IllegalArgumentException(
                    "transaction must belong to idempotency user");
        }

        if (this.transaction != null
                && !Objects.equals(
                        this.transaction.getTransactionId(),
                        transaction.getTransactionId())) {
            throw new IllegalStateException(
                    "Idempotency record is already linked to another transaction");
        }

        this.transaction = transaction;
    }

    public void markCompleted(
            int httpStatus,
            String responseBody,
            OffsetDateTime completedAt) {

        finish(
                IdempotencyStatus.COMPLETED,
                httpStatus,
                responseBody,
                completedAt);
    }

    public void markFailed(
            int httpStatus,
            String responseBody,
            OffsetDateTime completedAt) {

        finish(
                IdempotencyStatus.FAILED,
                httpStatus,
                responseBody,
                completedAt);
    }

    public boolean matchesRequestHash(String candidateHash) {
        String normalizedCandidate = requireSha256(candidateHash);

        return MessageDigest.isEqual(
                requestHash.getBytes(StandardCharsets.US_ASCII),
                normalizedCandidate.getBytes(
                        StandardCharsets.US_ASCII));
    }

    public boolean isExpiredAt(OffsetDateTime instant) {
        OffsetDateTime normalizedInstant =
                requireUtcTimestamp(instant, "instant");

        return !normalizedInstant.isBefore(expiresAt);
    }

    private void finish(
            IdempotencyStatus terminalStatus,
            int httpStatus,
            String responseBody,
            OffsetDateTime completedAt) {

        requireInProgress();

        if (terminalStatus == null
                || !terminalStatus.isTerminal()) {
            throw new IllegalArgumentException(
                    "terminalStatus is required");
        }

        if (httpStatus < 100 || httpStatus > 599) {
            throw new IllegalArgumentException(
                    "httpStatus must be between 100 and 599");
        }

        OffsetDateTime normalizedCompletedAt =
                requireUtcTimestamp(
                        completedAt,
                        "completedAt");

        if (normalizedCompletedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException(
                    "completedAt cannot be before createdAt");
        }

        this.status = terminalStatus;
        this.httpStatus = httpStatus;
        this.responseBody = responseBody;
        this.completedAt = normalizedCompletedAt;
    }

    private void requireInProgress() {
        if (status != IdempotencyStatus.IN_PROGRESS) {
            throw new IllegalStateException(
                    "Only an in-progress idempotency record can be changed");
        }
    }

    private static void requirePersistedUser(User user) {
        Objects.requireNonNull(user, "user is required");

        if (user.getUserId() == null
                || user.getUserId() <= 0L) {
            throw new IllegalArgumentException(
                    "user must already be persisted");
        }
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

    private static String requireSha256(String value) {
        if (value == null) {
            throw new IllegalArgumentException(
                    "requestHash is required");
        }

        String normalizedValue = value
                .trim()
                .toLowerCase(Locale.ROOT);

        if (!SHA_256_PATTERN.matcher(normalizedValue).matches()) {
            throw new IllegalArgumentException(
                    "requestHash must be a 64-character hexadecimal SHA-256 value");
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

    public Long getIdempotencyRecordId() {
        return idempotencyRecordId;
    }

    public User getUser() {
        return user;
    }

    public IdempotencyOperation getOperationCode() {
        return operationCode;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public TransactionDb getTransaction() {
        return transaction;
    }

    public IdempotencyStatus getStatus() {
        return status;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getCompletedAt() {
        return completedAt;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public long getVersionNo() {
        return versionNo;
    }
}
