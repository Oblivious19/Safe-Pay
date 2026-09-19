package com.ofss.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ofss.beans.IdempotencyOperation;
import com.ofss.beans.IdempotencyRecord;
import com.ofss.beans.TransactionDb;
import com.ofss.beans.User;
import com.ofss.excp.IdempotencyConflictException;
import com.ofss.repository.IdempotencyRecordDao;

import jakarta.persistence.EntityManager;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceImplTest {

    private static final Long USER_ID = 7L;
    private static final String KEY = "payment-request-001";
    private static final String HASH = "a".repeat(64);
    private static final String OTHER_HASH = "b".repeat(64);
    private static final String CORRELATION_ID =
            "phase27-correlation";
    private static final OffsetDateTime NOW =
            OffsetDateTime.parse("2026-09-16T06:30:00Z");

    @Mock private IdempotencyRecordDao idempotencyRecordDao;
    @Mock private EntityManager entityManager;

    private IdempotencyServiceImpl service;
    private User user;

    @BeforeEach
    void setUp() {
        user = mock(User.class);
        org.mockito.Mockito.lenient()
                .when(user.getUserId())
                .thenReturn(USER_ID);

        service = new IdempotencyServiceImpl(
                idempotencyRecordDao,
                entityManager,
                new ObjectMapper().findAndRegisterModules(),
                Clock.fixed(NOW.toInstant(), ZoneOffset.UTC),
                new NoOpTransactionManager(),
                Duration.ofHours(12));
    }

    @Test
    void claimsExecutesAndCompletesInOneTransaction() {
        when(idempotencyRecordDao.findByScopeForUpdate(
                USER_ID,
                IdempotencyOperation.TRANSACTION_CREATE,
                KEY)).thenReturn(Optional.empty());
        when(entityManager.getReference(User.class, USER_ID))
                .thenReturn(user);
        when(idempotencyRecordDao.saveAndFlush(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AtomicInteger effects = new AtomicInteger();

        IdempotencyExecutionResult<String> result = execute(
                HASH,
                () -> {
                    effects.incrementAndGet();
                    return IdempotencyExecutionResult.executed(
                            201,
                            "created",
                            null);
                });

        assertThat(result.replayed()).isFalse();
        assertThat(result.httpStatus()).isEqualTo(201);
        assertThat(result.responseBody()).isEqualTo("created");
        assertThat(effects).hasValue(1);

        ArgumentCaptor<IdempotencyRecord> captor =
                ArgumentCaptor.forClass(IdempotencyRecord.class);

        verify(idempotencyRecordDao,
                org.mockito.Mockito.times(2))
                .saveAndFlush(captor.capture());

        IdempotencyRecord completed = captor.getValue();

        assertThat(completed.getCreatedAt()).isEqualTo(NOW);
        assertThat(completed.getExpiresAt())
                .isEqualTo(NOW.plusHours(12));
        assertThat(completed.getCompletedAt()).isEqualTo(NOW);
        assertThat(completed.getHttpStatus()).isEqualTo(201);
        assertThat(completed.getResponseBody())
                .isEqualTo("\"created\"");
    }

    @Test
    void replaysTerminalRecordWithoutExecutingAction() {
        IdempotencyRecord completed = completedRecord(
                NOW.minusHours(1),
                NOW.plusHours(11),
                HASH,
                200,
                "\"original\"");

        when(idempotencyRecordDao.findByScopeForUpdate(
                USER_ID,
                IdempotencyOperation.TRANSACTION_CREATE,
                KEY)).thenReturn(Optional.of(completed));

        AtomicInteger effects = new AtomicInteger();

        IdempotencyExecutionResult<String> result = execute(
                HASH,
                () -> {
                    effects.incrementAndGet();
                    return IdempotencyExecutionResult.executed(
                            200,
                            "new",
                            null);
                });

        assertThat(result.replayed()).isTrue();
        assertThat(result.httpStatus()).isEqualTo(200);
        assertThat(result.responseBody()).isEqualTo("original");
        assertThat(effects).hasValue(0);
        verify(idempotencyRecordDao, never())
                .saveAndFlush(any());
    }

    @Test
    void rejectsDifferentRequestBeforeAction() {
        IdempotencyRecord completed = completedRecord(
                NOW.minusHours(1),
                NOW.plusHours(11),
                HASH,
                201,
                "\"created\"");

        when(idempotencyRecordDao.findByScopeForUpdate(
                USER_ID,
                IdempotencyOperation.TRANSACTION_CREATE,
                KEY)).thenReturn(Optional.of(completed));

        AtomicInteger effects = new AtomicInteger();

        assertThatThrownBy(() -> execute(
                OTHER_HASH,
                () -> {
                    effects.incrementAndGet();
                    return IdempotencyExecutionResult.executed(
                            201,
                            "new",
                            null);
                }))
                .isInstanceOf(IdempotencyConflictException.class)
                .extracting("errorCode")
                .isEqualTo("IDEMPOTENCY_KEY_REUSED");

        assertThat(effects).hasValue(0);
    }

    @Test
    void rejectsExpiredKeyWithoutSilentReexecution() {
        IdempotencyRecord expired = completedRecord(
                NOW.minusHours(13),
                NOW.minusHours(1),
                HASH,
                201,
                "\"created\"");

        when(idempotencyRecordDao.findByScopeForUpdate(
                USER_ID,
                IdempotencyOperation.TRANSACTION_CREATE,
                KEY)).thenReturn(Optional.of(expired));

        AtomicInteger effects = new AtomicInteger();

        assertThatThrownBy(() -> execute(
                HASH,
                () -> {
                    effects.incrementAndGet();
                    return IdempotencyExecutionResult.executed(
                            201,
                            "new",
                            null);
                }))
                .isInstanceOf(IdempotencyConflictException.class)
                .extracting("errorCode")
                .isEqualTo("IDEMPOTENCY_KEY_EXPIRED");

        assertThat(effects).hasValue(0);
    }

    @Test
    void rejectsPersistedInProgressRecord() {
        IdempotencyRecord inProgress = IdempotencyRecord.begin(
                user,
                IdempotencyOperation.TRANSACTION_CREATE,
                KEY,
                HASH,
                CORRELATION_ID,
                NOW.minusMinutes(1),
                NOW.plusHours(11));

        when(idempotencyRecordDao.findByScopeForUpdate(
                USER_ID,
                IdempotencyOperation.TRANSACTION_CREATE,
                KEY)).thenReturn(Optional.of(inProgress));

        assertThatThrownBy(() -> execute(
                HASH,
                () -> IdempotencyExecutionResult.executed(
                        201,
                        "new",
                        null)))
                .isInstanceOf(IdempotencyConflictException.class)
                .extracting("errorCode")
                .isEqualTo(
                        "IDEMPOTENCY_REQUEST_IN_PROGRESS");
    }

    @Test
    void resolvesUniqueConstraintRaceInFreshTransaction() {
        IdempotencyRecord winner = completedRecord(
                NOW.minusSeconds(1),
                NOW.plusHours(12).minusSeconds(1),
                HASH,
                201,
                "\"winner\"");

        when(idempotencyRecordDao.findByScopeForUpdate(
                USER_ID,
                IdempotencyOperation.TRANSACTION_CREATE,
                KEY))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winner));
        when(entityManager.getReference(User.class, USER_ID))
                .thenReturn(user);
        when(idempotencyRecordDao.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException(
                        "unique scope"));

        IdempotencyExecutionResult<String> result = execute(
                HASH,
                () -> IdempotencyExecutionResult.executed(
                        201,
                        "loser",
                        null));

        assertThat(result.replayed()).isTrue();
        assertThat(result.responseBody()).isEqualTo("winner");
    }

    @Test
    void doesNotMisclassifyUnrelatedIntegrityFailure() {
        DataIntegrityViolationException failure =
                new DataIntegrityViolationException(
                        "unrelated business constraint");

        when(idempotencyRecordDao.findByScopeForUpdate(
                USER_ID,
                IdempotencyOperation.TRANSACTION_CREATE,
                KEY)).thenReturn(Optional.empty());
        when(entityManager.getReference(User.class, USER_ID))
                .thenReturn(user);
        when(idempotencyRecordDao.saveAndFlush(any()))
                .thenThrow(failure);

        assertThatThrownBy(() -> execute(
                HASH,
                () -> IdempotencyExecutionResult.executed(
                        201,
                        "never",
                        null)))
                .isSameAs(failure);
    }

    @Test
    void linksStoredResultToOwnedTransaction() {
        TransactionDb transaction = mock(TransactionDb.class);
        when(transaction.getTransactionId()).thenReturn(101L);
        when(transaction.getCustomer()).thenReturn(user);

        when(idempotencyRecordDao.findByScopeForUpdate(
                USER_ID,
                IdempotencyOperation.TRANSACTION_CREATE,
                KEY)).thenReturn(Optional.empty());
        when(entityManager.getReference(User.class, USER_ID))
                .thenReturn(user);
        when(entityManager.getReference(TransactionDb.class, 101L))
                .thenReturn(transaction);
        when(idempotencyRecordDao.saveAndFlush(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        execute(
                HASH,
                () -> IdempotencyExecutionResult.executed(
                        201,
                        "created",
                        101L));

        ArgumentCaptor<IdempotencyRecord> captor =
                ArgumentCaptor.forClass(IdempotencyRecord.class);

        verify(idempotencyRecordDao,
                org.mockito.Mockito.times(2))
                .saveAndFlush(captor.capture());

        assertThat(captor.getValue().getTransaction())
                .isSameAs(transaction);
    }

    @Test
    void persistsAndReplaysExplicitFailedLogicalResult() {
        java.util.concurrent.atomic.AtomicReference<IdempotencyRecord>
                stored = new java.util.concurrent.atomic.AtomicReference<>();

        when(idempotencyRecordDao.findByScopeForUpdate(
                USER_ID,
                IdempotencyOperation.TRANSACTION_CREATE,
                KEY)).thenAnswer(invocation ->
                        Optional.ofNullable(stored.get()));
        when(entityManager.getReference(User.class, USER_ID))
                .thenReturn(user);
        when(idempotencyRecordDao.saveAndFlush(any()))
                .thenAnswer(invocation -> {
                    IdempotencyRecord record =
                            invocation.getArgument(0);
                    stored.set(record);
                    return record;
                });

        AtomicInteger effects = new AtomicInteger();

        IdempotencyExecutionResult<String> first = execute(
                HASH,
                () -> {
                    effects.incrementAndGet();
                    return IdempotencyExecutionResult.executed(
                            422,
                            "business failure",
                            null);
                });

        IdempotencyExecutionResult<String> replay = execute(
                HASH,
                () -> {
                    effects.incrementAndGet();
                    return IdempotencyExecutionResult.executed(
                            200,
                            "must not execute",
                            null);
                });

        assertThat(first.replayed()).isFalse();
        assertThat(stored.get().getStatus())
                .isEqualTo(
                        com.ofss.beans.IdempotencyStatus.FAILED);
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.httpStatus()).isEqualTo(422);
        assertThat(replay.responseBody())
                .isEqualTo("business failure");
        assertThat(effects).hasValue(1);
    }

    @Test
    void validatesKeyAndRetentionConfiguration() {
        assertThatThrownBy(() -> execute(
                "not-a-hash",
                () -> IdempotencyExecutionResult.executed(
                        200,
                        "response",
                        null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("64-character hexadecimal");

        assertThatThrownBy(() -> service.execute(
                USER_ID,
                IdempotencyOperation.TRANSACTION_CREATE,
                "   ",
                HASH,
                CORRELATION_ID,
                String.class,
                () -> IdempotencyExecutionResult.executed(
                        200,
                        "response",
                        null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("idempotencyKey is required");

        assertThatThrownBy(() -> service.execute(
                USER_ID,
                IdempotencyOperation.TRANSACTION_CREATE,
                "x".repeat(129),
                HASH,
                CORRELATION_ID,
                String.class,
                () -> IdempotencyExecutionResult.executed(
                        200,
                        "response",
                        null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "idempotencyKey has an invalid length");

        assertThatThrownBy(() -> service.execute(
                USER_ID,
                IdempotencyOperation.TRANSACTION_CREATE,
                "unsafe\nkey",
                HASH,
                CORRELATION_ID,
                String.class,
                () -> IdempotencyExecutionResult.executed(
                        200,
                        "response",
                        null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "idempotencyKey contains an invalid character");

        assertThatThrownBy(() -> new IdempotencyServiceImpl(
                idempotencyRecordDao,
                entityManager,
                new ObjectMapper(),
                Clock.systemUTC(),
                new NoOpTransactionManager(),
                Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("retention must be positive");
    }

    private IdempotencyExecutionResult<String> execute(
            String requestHash,
            java.util.function.Supplier<
                    IdempotencyExecutionResult<String>> action) {

        return service.execute(
                USER_ID,
                IdempotencyOperation.TRANSACTION_CREATE,
                KEY,
                requestHash,
                CORRELATION_ID,
                String.class,
                action);
    }

    private IdempotencyRecord completedRecord(
            OffsetDateTime createdAt,
            OffsetDateTime expiresAt,
            String hash,
            int httpStatus,
            String serializedBody) {

        IdempotencyRecord record = IdempotencyRecord.begin(
                user,
                IdempotencyOperation.TRANSACTION_CREATE,
                KEY,
                hash,
                CORRELATION_ID,
                createdAt,
                expiresAt);

        record.markCompleted(
                httpStatus,
                serializedBody,
                createdAt.plusSeconds(1));

        return record;
    }

    private static final class NoOpTransactionManager
            extends AbstractPlatformTransactionManager {

        private static final long serialVersionUID = 1L;

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(
                Object transaction,
                TransactionDefinition definition) {
            // No external resource is needed for this unit test.
        }

        @Override
        protected void doCommit(
                DefaultTransactionStatus status) {
            // No external resource is needed for this unit test.
        }

        @Override
        protected void doRollback(
                DefaultTransactionStatus status) {
            // No external resource is needed for this unit test.
        }
    }
}
