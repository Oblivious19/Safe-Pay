package com.ofss.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.ofss.beans.IdempotencyOperation;
import com.ofss.beans.IdempotencyRecord;
import com.ofss.dto.transaction.CreateTransactionRequest;
import com.ofss.dto.transaction.TransactionResponse;
import com.ofss.excp.IdempotencyConflictException;
import com.ofss.repository.IdempotencyRecordDao;

@SpringBootTest
class IdempotencyServiceOracleIntegrationTest {

    @Autowired
    private IdempotencyService idempotencyService;

    @Autowired
    private RequestFingerprintService fingerprintService;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private IdempotencyRecordDao idempotencyRecordDao;

    /*
     * This class verifies committed idempotency claims and business effects,
     * then removes its temporary Oracle transactions. Durable lifecycle
     * evidence is covered independently and cannot be deleted by design, so
     * isolate that collaborator to keep these fixtures safely removable.
     */
    @MockitoBean
    private TransactionLifecycleEvidenceService lifecycleEvidenceService;

    @Value("${spring.flyway.url}")
    private String ownerUrl;

    @Value("${spring.flyway.user}")
    private String ownerUsername;

    @Value("${spring.flyway.password}")
    private String ownerPassword;

    private Long customerId;
    private Long sourceAccountId;
    private Long beneficiaryId;
    private String fixtureToken;

    @BeforeEach
    void createOwnerFixtures() throws SQLException {
        fixtureToken = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 20);

        try (Connection connection = ownerConnection()) {
            connection.setAutoCommit(false);

            try {
                customerId = nextSequenceValue(
                        connection,
                        "SAFEPAY_OWNER.SEQ_APP_USER_ID");
                sourceAccountId = nextSequenceValue(
                        connection,
                        "SAFEPAY_OWNER.SEQ_ACCOUNT_ID");
                beneficiaryId = nextSequenceValue(
                        connection,
                        "SAFEPAY_OWNER.SEQ_BENEFICIARY_ID");

                insertUser(connection);
                insertAccount(connection);
                insertBeneficiary(connection);
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    @AfterEach
    void removeOwnerFixtures() throws SQLException {
        try (Connection connection = ownerConnection()) {
            connection.setAutoCommit(false);

            try {
                executeDelete(
                        connection,
                        "DELETE FROM SAFEPAY_OWNER.IDEMPOTENCY_RECORD "
                                + "WHERE USER_ID = ?",
                        customerId);
                executeDelete(
                        connection,
                        "DELETE FROM SAFEPAY_OWNER.PAYMENT_TRANSACTION "
                                + "WHERE CUSTOMER_USER_ID = ?",
                        customerId);
                executeDelete(
                        connection,
                        "DELETE FROM SAFEPAY_OWNER.BENEFICIARY "
                                + "WHERE BENEFICIARY_ID = ?",
                        beneficiaryId);
                executeDelete(
                        connection,
                        "DELETE FROM SAFEPAY_OWNER.ACCOUNT "
                                + "WHERE ACCOUNT_ID = ?",
                        sourceAccountId);
                executeDelete(
                        connection,
                        "DELETE FROM SAFEPAY_OWNER.APP_USER "
                                + "WHERE USER_ID = ?",
                        customerId);
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    @Test
    void replaysCreateWithOneDatabaseEffectAndTwelveHourExpiry() {
        CreateTransactionRequest request = createRequest("5000.00");
        AtomicInteger effects = new AtomicInteger();

        IdempotencyExecutionResult<TransactionResponse> first =
                executeCreate("sequential-key", request, effects);

        IdempotencyExecutionResult<TransactionResponse> replay =
                executeCreate("sequential-key", request, effects);

        assertThat(first.replayed()).isFalse();
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.httpStatus()).isEqualTo(201);
        assertThat(replay.transactionId())
                .isEqualTo(first.transactionId());
        assertThat(replay.responseBody())
                .isEqualTo(first.responseBody());
        assertThat(effects).hasValue(1);
        assertThat(countTransactions()).isEqualTo(1L);
        assertThat(countIdempotencyRecords()).isEqualTo(1L);

        IdempotencyRecord record = idempotencyRecordDao
                .findByScope(
                        customerId,
                        IdempotencyOperation.TRANSACTION_CREATE,
                        "sequential-key")
                .orElseThrow();

        assertThat(Duration.between(
                record.getCreatedAt(),
                record.getExpiresAt()))
                .isEqualTo(Duration.ofHours(12));
    }

    @Test
    void rejectsSameKeyWithDifferentRequestWithoutSecondEffect() {
        AtomicInteger effects = new AtomicInteger();

        executeCreate(
                "conflict-key",
                createRequest("5000.00"),
                effects);

        assertThatThrownBy(() -> executeCreate(
                "conflict-key",
                createRequest("5000.01"),
                effects))
                .isInstanceOf(IdempotencyConflictException.class)
                .extracting("errorCode")
                .isEqualTo("IDEMPOTENCY_KEY_REUSED");

        assertThat(effects).hasValue(1);
        assertThat(countTransactions()).isEqualTo(1L);
        assertThat(countIdempotencyRecords()).isEqualTo(1L);
    }

    @Test
    void concurrentDuplicatesProduceOneCommittedEffect()
            throws Exception {

        CreateTransactionRequest request = createRequest("5000.00");
        AtomicInteger effects = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<IdempotencyExecutionResult<TransactionResponse>>
                    first = executor.submit(() -> {
                        start.await();
                        return executeCreate(
                                "concurrent-key",
                                request,
                                effects);
                    });

            Future<IdempotencyExecutionResult<TransactionResponse>>
                    second = executor.submit(() -> {
                        start.await();
                        return executeCreate(
                                "concurrent-key",
                                request,
                                effects);
                    });

            start.countDown();

            IdempotencyExecutionResult<TransactionResponse>
                    firstResult = first.get(30, TimeUnit.SECONDS);
            IdempotencyExecutionResult<TransactionResponse>
                    secondResult = second.get(30, TimeUnit.SECONDS);

            assertThat(List.of(
                    firstResult.replayed(),
                    secondResult.replayed()))
                    .containsExactlyInAnyOrder(false, true);

            assertThat(firstResult.transactionId())
                    .isEqualTo(secondResult.transactionId());
            assertThat(firstResult.responseBody())
                    .isEqualTo(secondResult.responseBody());
            assertThat(effects).hasValue(1);
            assertThat(countTransactions()).isEqualTo(1L);
            assertThat(countIdempotencyRecords()).isEqualTo(1L);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(
                    5,
                    TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void rollsBackClaimAndBusinessEffectTogetherOnFailure() {
        CreateTransactionRequest request = createRequest("5000.00");
        String requestHash = fingerprint(request);
        AtomicInteger effects = new AtomicInteger();

        assertThatThrownBy(() -> idempotencyService.execute(
                customerId,
                IdempotencyOperation.TRANSACTION_CREATE,
                "rollback-key",
                requestHash,
                "rollback-correlation",
                TransactionResponse.class,
                () -> {
                    effects.incrementAndGet();
                    transactionService.createTransaction(
                            customerId,
                            request);
                    throw new IllegalStateException(
                            "simulated response-stage failure");
                }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("simulated response-stage failure");

        assertThat(countTransactions()).isZero();
        assertThat(countIdempotencyRecords()).isZero();

        IdempotencyExecutionResult<TransactionResponse> retry =
                executeCreate("rollback-key", request, effects);

        assertThat(retry.replayed()).isFalse();
        assertThat(effects).hasValue(2);
        assertThat(countTransactions()).isEqualTo(1L);
        assertThat(countIdempotencyRecords()).isEqualTo(1L);
    }

    @Test
    void expiredKeyRequiresNewKeyAndNeverReexecutesSilently()
            throws SQLException {

        CreateTransactionRequest request = createRequest("5000.00");
        AtomicInteger effects = new AtomicInteger();

        executeCreate("expired-key", request, effects);
        expireIdempotencyRecord("expired-key");

        assertThatThrownBy(() -> executeCreate(
                "expired-key",
                request,
                effects))
                .isInstanceOf(IdempotencyConflictException.class)
                .extracting("errorCode")
                .isEqualTo("IDEMPOTENCY_KEY_EXPIRED");

        assertThat(effects).hasValue(1);
        assertThat(countTransactions()).isEqualTo(1L);
    }

    @Test
    void changingCategoryWithSameKeyConflictsAndCachedResponseRetainsCategory() {
        var medical = new CreateTransactionRequest(sourceAccountId, beneficiaryId, new BigDecimal("100000.01"),
                "Category test", null, com.ofss.beans.PaymentCategory.MEDICAL);
        var loan = new CreateTransactionRequest(sourceAccountId, beneficiaryId, new BigDecimal("100000.01"),
                "Category test", null, com.ofss.beans.PaymentCategory.LOAN);
        AtomicInteger effects = new AtomicInteger();
        executeCreate("category-key", medical, effects);
        var replay = executeCreate("category-key", medical, effects);
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.responseBody().category()).isEqualTo(com.ofss.beans.PaymentCategory.MEDICAL);
        assertThatThrownBy(() -> executeCreate("category-key", loan, effects)).isInstanceOf(IdempotencyConflictException.class);
        assertThat(effects).hasValue(1);
        assertThat(countTransactions()).isEqualTo(1);
    }

    private IdempotencyExecutionResult<TransactionResponse>
            executeCreate(
                    String key,
                    CreateTransactionRequest request,
                    AtomicInteger effects) {

        return idempotencyService.execute(
                customerId,
                IdempotencyOperation.TRANSACTION_CREATE,
                key,
                fingerprint(request),
                "oracle-" + fixtureToken,
                TransactionResponse.class,
                () -> {
                    effects.incrementAndGet();

                    TransactionResponse response =
                            transactionService.createTransaction(
                                    customerId,
                                    request);

                    return IdempotencyExecutionResult.executed(
                            201,
                            response,
                            Long.valueOf(response.transactionId()));
                });
    }

    private String fingerprint(CreateTransactionRequest request) {
        return fingerprintService.fingerprint(
                IdempotencyOperation.TRANSACTION_CREATE,
                "/api/v1/transactions",
                request);
    }

    private CreateTransactionRequest createRequest(String amount) {
        return new CreateTransactionRequest(
                sourceAccountId,
                beneficiaryId,
                new BigDecimal(amount),
                "Oracle idempotency payment",
                "IDEM-" + fixtureToken);
    }

    private long countTransactions() {
        return countRows(
                "SELECT COUNT(*) FROM SAFEPAY_OWNER.PAYMENT_TRANSACTION "
                        + "WHERE CUSTOMER_USER_ID = ?",
                customerId);
    }

    private long countIdempotencyRecords() {
        return countRows(
                "SELECT COUNT(*) FROM SAFEPAY_OWNER.IDEMPOTENCY_RECORD "
                        + "WHERE USER_ID = ?",
                customerId);
    }

    private long countRows(String sql, Long id) {
        try (Connection connection = ownerConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setLong(1, id);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException("Count returned no row");
                }

                return resultSet.getLong(1);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Could not inspect Oracle test state",
                    exception);
        }
    }

    private void expireIdempotencyRecord(String key)
            throws SQLException {

        try (Connection connection = ownerConnection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                     UPDATE SAFEPAY_OWNER.IDEMPOTENCY_RECORD
                        SET CREATED_AT = SYSTIMESTAMP - INTERVAL '13' HOUR,
                            EXPIRES_AT = SYSTIMESTAMP - INTERVAL '1' HOUR
                      WHERE USER_ID = ?
                        AND OPERATION_CODE = 'TRANSACTION_CREATE'
                        AND IDEMPOTENCY_KEY = ?
                     """)) {

            statement.setLong(1, customerId);
            statement.setString(2, key);

            assertThat(statement.executeUpdate()).isEqualTo(1);
        }
    }

    private Connection ownerConnection() throws SQLException {
        return DriverManager.getConnection(
                ownerUrl,
                ownerUsername,
                ownerPassword);
    }

    private static Long nextSequenceValue(
            Connection connection,
            String sequenceName) throws SQLException {

        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT " + sequenceName + ".NEXTVAL FROM DUAL")) {

            if (!resultSet.next()) {
                throw new SQLException("Sequence returned no value");
            }

            return resultSet.getLong(1);
        }
    }

    private void insertUser(Connection connection)
            throws SQLException {

        try (PreparedStatement statement = connection.prepareStatement(
                """
                INSERT INTO SAFEPAY_OWNER.APP_USER
                    (USER_ID, FULL_NAME, EMAIL, PASSWORD_HASH)
                VALUES (?, ?, ?, ?)
                """)) {

            statement.setLong(1, customerId);
            statement.setString(2, "Idempotency Integration User");
            statement.setString(
                    3,
                    "idempotency-" + fixtureToken
                            + "@example.invalid");
            statement.setString(4, "integration-test-password-hash");
            statement.executeUpdate();
        }
    }

    private void insertAccount(Connection connection)
            throws SQLException {

        try (PreparedStatement statement = connection.prepareStatement(
                """
                INSERT INTO SAFEPAY_OWNER.ACCOUNT
                    (ACCOUNT_ID, OWNER_USER_ID, ACCOUNT_NUMBER,
                     ACCOUNT_TYPE, BANK_NAME, IFSC_CODE,
                     CURRENT_BALANCE)
                VALUES (?, ?, ?, 'SAVINGS', ?, 'HDFC0000001', ?)
                """)) {

            statement.setLong(1, sourceAccountId);
            statement.setLong(2, customerId);
            statement.setString(3, "ID" + fixtureToken);
            statement.setString(4, "SafePay Idempotency Bank");
            statement.setBigDecimal(
                    5,
                    new BigDecimal("500000.00"));
            statement.executeUpdate();
        }
    }

    private void insertBeneficiary(Connection connection)
            throws SQLException {

        try (PreparedStatement statement = connection.prepareStatement(
                """
                INSERT INTO SAFEPAY_OWNER.BENEFICIARY
                    (BENEFICIARY_ID, OWNER_USER_ID,
                     BENEFICIARY_NAME, PAYMENT_METHOD, UPI_ID)
                VALUES (?, ?, ?, 'UPI', ?)
                """)) {

            statement.setLong(1, beneficiaryId);
            statement.setLong(2, customerId);
            statement.setString(3, "Idempotency Beneficiary");
            statement.setString(
                    4,
                    "idem." + fixtureToken + "@safepay");
            statement.executeUpdate();
        }
    }

    private static void executeDelete(
            Connection connection,
            String sql,
            Long id) throws SQLException {

        if (id == null) {
            return;
        }

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            statement.executeUpdate();
        }
    }
}
