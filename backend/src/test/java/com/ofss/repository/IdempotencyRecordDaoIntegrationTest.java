package com.ofss.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import com.ofss.beans.IdempotencyOperation;
import com.ofss.beans.IdempotencyRecord;
import com.ofss.beans.IdempotencyStatus;
import com.ofss.beans.User;

@DataJpaTest
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE)
class IdempotencyRecordDaoIntegrationTest {

    private static final OffsetDateTime CREATED_AT =
            OffsetDateTime.parse("2026-09-16T06:00:00Z");

    private static final String FIRST_HASH = "a".repeat(64);
    private static final String SECOND_HASH = "b".repeat(64);

    @Autowired
    private UserDao userDao;

    @Autowired
    private IdempotencyRecordDao idempotencyRecordDao;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void persistsAndFindsExactInProgressScope() {
        User user = createUser(
                "Idempotency Scope User",
                "phase27.scope@safepay.test");

        IdempotencyRecord record = beginRecord(
                user,
                IdempotencyOperation.TRANSACTION_CREATE,
                "Create-Key-001",
                FIRST_HASH);

        idempotencyRecordDao.saveAndFlush(record);
        entityManager.clear();

        IdempotencyRecord reloaded = idempotencyRecordDao
                .findByScope(
                        user.getUserId(),
                        IdempotencyOperation.TRANSACTION_CREATE,
                        "Create-Key-001")
                .orElseThrow();

        assertThat(reloaded.getIdempotencyRecordId()).isPositive();
        assertThat(reloaded.getUser().getUserId())
                .isEqualTo(user.getUserId());
        assertThat(reloaded.getOperationCode())
                .isEqualTo(
                        IdempotencyOperation.TRANSACTION_CREATE);
        assertThat(reloaded.getRequestHash())
                .isEqualTo(FIRST_HASH);
        assertThat(reloaded.getStatus())
                .isEqualTo(IdempotencyStatus.IN_PROGRESS);
        assertThat(reloaded.getVersionNo()).isZero();
    }

    @Test
    void persistsTerminalResponseBodyAndOptimisticVersion() {
        User user = createUser(
                "Idempotency Completion User",
                "phase27.completion@safepay.test");

        IdempotencyRecord record = beginRecord(
                user,
                IdempotencyOperation.TRANSACTION_AUTHORIZE,
                "Authorize-Key-001",
                FIRST_HASH);

        idempotencyRecordDao.saveAndFlush(record);

        String responseBody =
                "{\"transactionId\":\"9001\",\"state\":\"PROTECTED\"}";

        record.markCompleted(
                200,
                responseBody,
                CREATED_AT.plusSeconds(2));

        idempotencyRecordDao.saveAndFlush(record);
        entityManager.clear();

        IdempotencyRecord reloaded = idempotencyRecordDao
                .findByScope(
                        user.getUserId(),
                        IdempotencyOperation.TRANSACTION_AUTHORIZE,
                        "Authorize-Key-001")
                .orElseThrow();

        assertThat(reloaded.getStatus())
                .isEqualTo(IdempotencyStatus.COMPLETED);
        assertThat(reloaded.getHttpStatus()).isEqualTo(200);
        assertThat(reloaded.getResponseBody())
                .isEqualTo(responseBody);
        assertThat(reloaded.getCompletedAt())
                .isEqualTo(CREATED_AT.plusSeconds(2));
        assertThat(reloaded.getVersionNo()).isEqualTo(1L);
    }

    @Test
    void isolatesSameKeyByActorAndOperationAndSupportsLocking() {
        User firstUser = createUser(
                "First Scope User",
                "phase27.first@safepay.test");

        User secondUser = createUser(
                "Second Scope User",
                "phase27.second@safepay.test");

        idempotencyRecordDao.save(beginRecord(
                firstUser,
                IdempotencyOperation.TRANSACTION_CREATE,
                "Shared-Key",
                FIRST_HASH));

        idempotencyRecordDao.save(beginRecord(
                firstUser,
                IdempotencyOperation.TRANSACTION_CANCEL,
                "Shared-Key",
                SECOND_HASH));

        idempotencyRecordDao.saveAndFlush(beginRecord(
                secondUser,
                IdempotencyOperation.TRANSACTION_CREATE,
                "Shared-Key",
                SECOND_HASH));

        assertThat(idempotencyRecordDao.findByScopeForUpdate(
                firstUser.getUserId(),
                IdempotencyOperation.TRANSACTION_CREATE,
                "Shared-Key"))
                .hasValueSatisfying(record ->
                        assertThat(record.getRequestHash())
                                .isEqualTo(FIRST_HASH));

        assertThat(idempotencyRecordDao.findByScope(
                firstUser.getUserId(),
                IdempotencyOperation.TRANSACTION_CANCEL,
                "Shared-Key"))
                .hasValueSatisfying(record ->
                        assertThat(record.getRequestHash())
                                .isEqualTo(SECOND_HASH));

        assertThat(idempotencyRecordDao.findByScope(
                secondUser.getUserId(),
                IdempotencyOperation.TRANSACTION_CREATE,
                "Shared-Key"))
                .hasValueSatisfying(record ->
                        assertThat(record.getRequestHash())
                                .isEqualTo(SECOND_HASH));

        assertThat(idempotencyRecordDao.findByScope(
                firstUser.getUserId(),
                IdempotencyOperation.TRANSACTION_AUTHORIZE,
                "Shared-Key")).isEmpty();
    }

    @Test
    void databaseRejectsDuplicateActorOperationAndKey() {
        User user = createUser(
                "Duplicate Scope User",
                "phase27.duplicate@safepay.test");

        idempotencyRecordDao.saveAndFlush(beginRecord(
                user,
                IdempotencyOperation.TRANSACTION_CREATE,
                "Duplicate-Key",
                FIRST_HASH));

        assertThatThrownBy(() ->
                idempotencyRecordDao.saveAndFlush(beginRecord(
                        user,
                        IdempotencyOperation.TRANSACTION_CREATE,
                        "Duplicate-Key",
                        SECOND_HASH)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void exposesNoDeleteOperations() {
        Set<String> prohibitedMethods = Set.of(
                "delete",
                "deleteById",
                "deleteAll",
                "deleteAllById");

        Set<String> exposedMethods = Arrays
                .stream(IdempotencyRecordDao.class.getMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());

        assertThat(exposedMethods)
                .doesNotContainAnyElementsOf(prohibitedMethods);
    }

    private User createUser(
            String fullName,
            String email) {

        return userDao.save(
                User.createActiveUser(
                        fullName,
                        email,
                        null,
                        "test-only-password-hash",
                        CREATED_AT.minusMinutes(1)));
    }

    private static IdempotencyRecord beginRecord(
            User user,
            IdempotencyOperation operation,
            String key,
            String requestHash) {

        return IdempotencyRecord.begin(
                user,
                operation,
                key,
                requestHash,
                "phase27-correlation",
                CREATED_AT,
                CREATED_AT.plusHours(2));
    }
}
