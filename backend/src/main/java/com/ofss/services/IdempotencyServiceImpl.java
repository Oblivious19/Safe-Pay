package com.ofss.services;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ofss.beans.IdempotencyOperation;
import com.ofss.beans.IdempotencyRecord;
import com.ofss.beans.IdempotencyStatus;
import com.ofss.beans.TransactionDb;
import com.ofss.beans.User;
import com.ofss.excp.IdempotencyConflictException;
import com.ofss.repository.IdempotencyRecordDao;

import jakarta.persistence.EntityManager;

@Service
public class IdempotencyServiceImpl
        implements IdempotencyService {

    private static final Pattern SHA_256_PATTERN =
            Pattern.compile("[0-9a-f]{64}");

    private final IdempotencyRecordDao idempotencyRecordDao;
    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Duration retention;
    private final TransactionTemplate transactionTemplate;

    public IdempotencyServiceImpl(
            IdempotencyRecordDao idempotencyRecordDao,
            EntityManager entityManager,
            ObjectMapper objectMapper,
            Clock clock,
            PlatformTransactionManager transactionManager,
            @Value("${safepay.idempotency.retention}")
                    Duration retention) {

        this.idempotencyRecordDao = Objects.requireNonNull(
                idempotencyRecordDao,
                "idempotencyRecordDao is required");
        this.entityManager = Objects.requireNonNull(
                entityManager,
                "entityManager is required");
        this.objectMapper = Objects.requireNonNull(
                objectMapper,
                "objectMapper is required");
        this.clock = Objects.requireNonNull(
                clock,
                "clock is required");
        this.retention = requirePositiveRetention(retention);

        this.transactionTemplate = new TransactionTemplate(
                Objects.requireNonNull(
                        transactionManager,
                        "transactionManager is required"));
        this.transactionTemplate.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public <T> IdempotencyExecutionResult<T> execute(
            Long userId,
            IdempotencyOperation operationCode,
            String idempotencyKey,
            String requestHash,
            String correlationId,
            Class<T> responseType,
            Supplier<IdempotencyExecutionResult<T>> action) {

        Long normalizedUserId = requirePositiveId(userId);
        IdempotencyOperation normalizedOperation =
                Objects.requireNonNull(
                        operationCode,
                        "operationCode is required");
        String normalizedKey = requireIdempotencyKey(
                idempotencyKey);
        String normalizedHash = requireSha256(requestHash);
        String normalizedCorrelationId = requireText(
                correlationId,
                "correlationId",
                64);
        Class<T> normalizedResponseType = Objects.requireNonNull(
                responseType,
                "responseType is required");
        Supplier<IdempotencyExecutionResult<T>> normalizedAction =
                Objects.requireNonNull(action, "action is required");

        try {
            return requireTransactionResult(
                    transactionTemplate.execute(status ->
                            executeInTransaction(
                                    normalizedUserId,
                                    normalizedOperation,
                                    normalizedKey,
                                    normalizedHash,
                                    normalizedCorrelationId,
                                    normalizedResponseType,
                                    normalizedAction)));
        } catch (DataIntegrityViolationException conflict) {
            return requireTransactionResult(
                    transactionTemplate.execute(status ->
                            resolveAfterConstraintRace(
                                    normalizedUserId,
                                    normalizedOperation,
                                    normalizedKey,
                                    normalizedHash,
                                    normalizedResponseType,
                                    conflict)));
        }
    }

    private <T> IdempotencyExecutionResult<T>
            executeInTransaction(
                    Long userId,
                    IdempotencyOperation operationCode,
                    String idempotencyKey,
                    String requestHash,
                    String correlationId,
                    Class<T> responseType,
                    Supplier<IdempotencyExecutionResult<T>> action) {

        var existing = idempotencyRecordDao
                .findByScopeForUpdate(
                        userId,
                        operationCode,
                        idempotencyKey);

        if (existing.isPresent()) {
            return replayExisting(
                    existing.orElseThrow(),
                    requestHash,
                    responseType,
                    currentUtcTime());
        }

        OffsetDateTime createdAt = currentUtcTime();
        User userReference = entityManager.getReference(
                User.class,
                userId);

        IdempotencyRecord record = IdempotencyRecord.begin(
                userReference,
                operationCode,
                idempotencyKey,
                requestHash,
                correlationId,
                createdAt,
                createdAt.plus(retention));

        /*
         * Flush before the business action so Oracle's unique scope is the
         * concurrency arbiter. The row remains uncommitted until the action
         * and terminal response record commit in this same transaction.
         */
        idempotencyRecordDao.saveAndFlush(record);

        IdempotencyExecutionResult<T> result =
                Objects.requireNonNull(
                        action.get(),
                        "action result is required");

        if (result.replayed()) {
            throw new IllegalArgumentException(
                    "action must return a newly executed result");
        }

        if (result.transactionId() != null) {
            TransactionDb transactionReference =
                    entityManager.getReference(
                            TransactionDb.class,
                            result.transactionId());

            record.linkTransaction(transactionReference);
        }

        String serializedBody = serialize(
                result.responseBody());
        OffsetDateTime completedAt = currentUtcTime();

        if (result.httpStatus() >= 400) {
            record.markFailed(
                    result.httpStatus(),
                    serializedBody,
                    completedAt);
        } else {
            record.markCompleted(
                    result.httpStatus(),
                    serializedBody,
                    completedAt);
        }

        idempotencyRecordDao.saveAndFlush(record);
        return result;
    }

    private <T> IdempotencyExecutionResult<T>
            resolveAfterConstraintRace(
                    Long userId,
                    IdempotencyOperation operationCode,
                    String idempotencyKey,
                    String requestHash,
                    Class<T> responseType,
                    DataIntegrityViolationException originalFailure) {

        IdempotencyRecord winner = idempotencyRecordDao
                .findByScopeForUpdate(
                        userId,
                        operationCode,
                        idempotencyKey)
                .orElseThrow(() -> originalFailure);

        return replayExisting(
                winner,
                requestHash,
                responseType,
                currentUtcTime());
    }

    private <T> IdempotencyExecutionResult<T> replayExisting(
            IdempotencyRecord record,
            String requestHash,
            Class<T> responseType,
            OffsetDateTime now) {

        if (!record.matchesRequestHash(requestHash)) {
            throw IdempotencyConflictException.differentRequest();
        }

        if (record.isExpiredAt(now)) {
            throw IdempotencyConflictException.expired();
        }

        if (record.getStatus() == IdempotencyStatus.IN_PROGRESS) {
            throw IdempotencyConflictException.requestInProgress();
        }

        Integer storedHttpStatus = record.getHttpStatus();

        if (storedHttpStatus == null) {
            throw new IllegalStateException(
                    "Terminal idempotency record is missing HTTP status");
        }

        Long transactionId = record.getTransaction() == null
                ? null
                : record.getTransaction().getTransactionId();

        return IdempotencyExecutionResult.replayed(
                storedHttpStatus,
                deserialize(
                        record.getResponseBody(),
                        responseType),
                transactionId);
    }

    private String serialize(Object responseBody) {
        if (responseBody == null) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(responseBody);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Idempotent response could not be serialized",
                    exception);
        }
    }

    private <T> T deserialize(
            String responseBody,
            Class<T> responseType) {

        if (responseBody == null) {
            return null;
        }

        try {
            return objectMapper.readValue(
                    responseBody,
                    responseType);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Stored idempotent response could not be read",
                    exception);
        }
    }

    private OffsetDateTime currentUtcTime() {
        return OffsetDateTime.now(clock)
                .withOffsetSameInstant(ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.MICROS);
    }

    private static Duration requirePositiveRetention(
            Duration value) {

        Duration retention = Objects.requireNonNull(
                value,
                "retention is required");

        if (retention.isZero() || retention.isNegative()) {
            throw new IllegalArgumentException(
                    "retention must be positive");
        }

        return retention;
    }

    private static Long requirePositiveId(Long value) {
        if (value == null || value <= 0L) {
            throw new IllegalArgumentException(
                    "userId must be positive");
        }

        return value;
    }

    private static String requireIdempotencyKey(String value) {
        String normalized = requireText(
                value,
                "idempotencyKey",
                128);

        if (normalized.chars().anyMatch(
                character -> Character.isISOControl(character))) {
            throw new IllegalArgumentException(
                    "idempotencyKey contains an invalid character");
        }

        return normalized;
    }

    private static String requireSha256(String value) {
        if (value == null) {
            throw new IllegalArgumentException(
                    "requestHash is required");
        }

        String normalized = value
                .trim()
                .toLowerCase(Locale.ROOT);

        if (!SHA_256_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                    "requestHash must be a 64-character hexadecimal SHA-256 value");
        }

        return normalized;
    }

    private static String requireText(
            String value,
            String fieldName,
            int maximumLength) {

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + " is required");
        }

        String normalized = value.trim();

        if (normalized.length() > maximumLength) {
            throw new IllegalArgumentException(
                    fieldName + " has an invalid length");
        }

        return normalized;
    }

    private static <T> IdempotencyExecutionResult<T>
            requireTransactionResult(
                    IdempotencyExecutionResult<T> result) {

        return Objects.requireNonNull(
                result,
                "idempotency transaction returned no result");
    }
}
