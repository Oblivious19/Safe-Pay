package com.ofss.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.ofss.beans.TransactionState;
import com.ofss.dto.transaction.TransactionResponse;
import com.ofss.excp.InvalidStateTransitionException;
import com.ofss.repository.TransactionDao;

@SpringBootTest(
        properties = "safepay.protection-scheduler.enabled=false")
class ProtectedTransactionSchedulerOracleIntegrationTest {

    private static final BigDecimal PAYMENT_AMOUNT =
            new BigDecimal("5000.01");

    @Autowired
    private ProtectedTransactionReleaseService releaseService;

    @Autowired
    private ProtectedTransactionReleaseWorker releaseWorker;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private TransactionDao transactionDao;

    /*
     * This integration class owns and removes short-lived scheduler fixtures.
     * Durable lifecycle evidence is intentionally isolated here because Oracle
     * correctly forbids deleting its immutable audit/notification rows. The
     * evidence persistence contract is covered by its dedicated integration
     * tests; this class remains focused on release, backlog and race semantics.
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
    void createFixtures() throws SQLException {
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
    void removeFixtures() throws SQLException {
        try (Connection connection = ownerConnection()) {
            connection.setAutoCommit(false);

            try {
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
    void recoversExpiredDatabaseBacklogWithoutInMemoryTimerState()
            throws SQLException {

        Long transactionId = insertProtectedPayment("RECOVERY", 10);

        assertThat(releaseService.releaseDueTransactions(50))
                .isEqualTo(1);

        TransactionResponse released = transactionService.getTransaction(
                customerId,
                transactionId);
        assertThat(released.state()).isEqualTo(TransactionState.RELEASED);
        assertThat(released.releasedAt()).isNotNull();
        assertThat(released.reservedAmount())
                .isEqualByComparingTo(PAYMENT_AMOUNT);
        assertThat(transactionDao.findOwnedById(
                transactionId,
                customerId).orElseThrow().getReservationEndedAt())
                .isNull();
        assertThat(readAccountReservedAmount())
                .isEqualByComparingTo(PAYMENT_AMOUNT);
    }

    @Test
    void duplicateSchedulerRunsProduceExactlyOneReleaseTransition()
            throws Exception {

        Long transactionId = insertProtectedPayment("DUPLICATE", 10);

        ConcurrentResult<Integer, Integer> result =
                runConcurrently(
                        () -> releaseService.releaseDueTransactions(50),
                        () -> releaseService.releaseDueTransactions(50));

        assertThat(Arrays.asList(result.first(), result.second()))
                .containsExactlyInAnyOrder(
                        1,
                        0);
        assertThat(transactionService.getTransaction(
                customerId,
                transactionId).state())
                .isEqualTo(TransactionState.RELEASED);
    }

    @Test
    void expiredCancellationRaceAlwaysEndsInOneRelease()
            throws Exception {

        Long transactionId = insertProtectedPayment("CANCEL-RACE", 10);

        ConcurrentResult<Boolean,
                ProtectedTransactionReleaseOutcome> result =
                runConcurrently(
                        () -> attemptCancellation(transactionId),
                        () -> releaseWorker.releaseIfExpired(transactionId));

        assertThat(result.first()).isFalse();
        assertThat(result.second())
                .isEqualTo(ProtectedTransactionReleaseOutcome.RELEASED);
        assertThat(transactionService.getTransaction(
                customerId,
                transactionId).state())
                .isEqualTo(TransactionState.RELEASED);
        assertThat(readAccountReservedAmount())
                .isEqualByComparingTo(PAYMENT_AMOUNT);
    }

    @Test
    void boundedPollingDrainsOlderBacklogAcrossLaterCycles()
            throws SQLException {

        Long olderId = insertProtectedPayment("OLDER", 20);
        Long newerId = insertProtectedPayment("NEWER", 10);

        assertThat(releaseService.releaseDueTransactions(1))
                .isEqualTo(1);
        assertThat(transactionService.getTransaction(
                customerId,
                olderId).state())
                .isEqualTo(TransactionState.RELEASED);
        assertThat(transactionService.getTransaction(
                customerId,
                newerId).state())
                .isEqualTo(TransactionState.PROTECTED);

        assertThat(releaseService.releaseDueTransactions(1))
                .isEqualTo(1);
        assertThat(transactionService.getTransaction(
                customerId,
                newerId).state())
                .isEqualTo(TransactionState.RELEASED);
        assertThat(readAccountReservedAmount())
                .isEqualByComparingTo(PAYMENT_AMOUNT.multiply(
                        BigDecimal.valueOf(2L)));
    }

    private boolean attemptCancellation(Long transactionId) {
        try {
            transactionService.cancelTransaction(
                    customerId,
                    transactionId);
            return true;
        } catch (InvalidStateTransitionException expectedConflict) {
            return false;
        }
    }

    private Long insertProtectedPayment(
            String suffix,
            int deadlineSecondsAgo) throws SQLException {

        try (Connection connection = ownerConnection()) {
            connection.setAutoCommit(false);

            try {
                Long transactionId = nextSequenceValue(
                        connection,
                        "SAFEPAY_OWNER.SEQ_PAYMENT_TRANSACTION_ID");

                try (PreparedStatement accountStatement =
                             connection.prepareStatement(
                                     """
                                     UPDATE SAFEPAY_OWNER.ACCOUNT
                                        SET reserved_amount =
                                                reserved_amount + ?,
                                            updated_at = SYSTIMESTAMP
                                      WHERE account_id = ?
                                     """)) {

                    accountStatement.setBigDecimal(1, PAYMENT_AMOUNT);
                    accountStatement.setLong(2, sourceAccountId);
                    assertThat(accountStatement.executeUpdate())
                            .isEqualTo(1);
                }

                try (PreparedStatement transactionStatement =
                             connection.prepareStatement(
                                     """
                                     INSERT INTO SAFEPAY_OWNER.PAYMENT_TRANSACTION (
                                         transaction_id,
                                         transaction_reference,
                                         customer_user_id,
                                         source_account_id,
                                         beneficiary_id,
                                         amount,
                                         currency_code,
                                         state,
                                         version_no,
                                         reserved_amount,
                                         reserved_at,
                                         risk_policy_id,
                                         risk_policy_band_id,
                                         protection_policy_id,
                                         risk_tier,
                                         policy_version,
                                         matched_band_code,
                                         protection_seconds,
                                         risk_explanation,
                                         risk_assessed_at,
                                         created_at,
                                         authorized_at,
                                         protected_until,
                                         updated_at
                                     )
                                     SELECT ?, ?, ?, ?, ?, ?, 'INR',
                                            'PROTECTED', 0, ?,
                                            SYSTIMESTAMP
                                                - NUMTODSINTERVAL(30, 'SECOND'),
                                            policy.risk_policy_id,
                                            band.risk_policy_band_id,
                                            protection.protection_policy_id,
                                            'MEDIUM',
                                            policy.policy_version,
                                            band.band_code,
                                            protection.protection_seconds,
                                            band.explanation_template,
                                            SYSTIMESTAMP
                                                - NUMTODSINTERVAL(30, 'SECOND'),
                                            SYSTIMESTAMP
                                                - NUMTODSINTERVAL(32, 'SECOND'),
                                            SYSTIMESTAMP
                                                - NUMTODSINTERVAL(31, 'SECOND'),
                                            SYSTIMESTAMP
                                                - NUMTODSINTERVAL(?, 'SECOND'),
                                            SYSTIMESTAMP
                                       FROM SAFEPAY_OWNER.RISK_POLICY policy
                                       JOIN SAFEPAY_OWNER.RISK_POLICY_BAND band
                                         ON band.risk_policy_id =
                                                policy.risk_policy_id
                                       JOIN SAFEPAY_OWNER.PROTECTION_POLICY
                                                protection
                                         ON protection.protection_policy_id =
                                                band.protection_policy_id
                                      WHERE policy.policy_version =
                                                'AMOUNT_ONLY_V1'
                                        AND band.band_code =
                                                'AMOUNT_MEDIUM_V1'
                                     """)) {

                    transactionStatement.setLong(1, transactionId);
                    transactionStatement.setString(
                            2,
                            "TX-SCHED-" + fixtureToken + "-" + suffix);
                    transactionStatement.setLong(3, customerId);
                    transactionStatement.setLong(4, sourceAccountId);
                    transactionStatement.setLong(5, beneficiaryId);
                    transactionStatement.setBigDecimal(6, PAYMENT_AMOUNT);
                    transactionStatement.setBigDecimal(7, PAYMENT_AMOUNT);
                    transactionStatement.setInt(8, deadlineSecondsAgo);

                    assertThat(transactionStatement.executeUpdate())
                            .isEqualTo(1);
                }

                connection.commit();
                return transactionId;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private BigDecimal readAccountReservedAmount()
            throws SQLException {

        try (Connection connection = ownerConnection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                     SELECT reserved_amount
                       FROM SAFEPAY_OWNER.ACCOUNT
                      WHERE account_id = ?
                     """)) {

            statement.setLong(1, sourceAccountId);

            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getBigDecimal(1);
            }
        }
    }

    private <A, B> ConcurrentResult<A, B> runConcurrently(
            Callable<A> firstTask,
            Callable<B> secondTask) throws Exception {

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<A> first = executor.submit(() -> {
                ready.countDown();
                assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                return firstTask.call();
            });
            Future<B> second = executor.submit(() -> {
                ready.countDown();
                assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                return secondTask.call();
            });

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            return new ConcurrentResult<>(
                    first.get(15, TimeUnit.SECONDS),
                    second.get(15, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(
                    5,
                    TimeUnit.SECONDS)).isTrue();
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
            statement.setString(2, "Scheduler Race Customer");
            statement.setString(
                    3,
                    "scheduler-race-" + fixtureToken
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
            statement.setString(3, "SR" + fixtureToken);
            statement.setString(4, "SafePay Scheduler Race Bank");
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
            statement.setString(3, "Scheduler Race Beneficiary");
            statement.setString(
                    4,
                    "scheduler.race." + fixtureToken + "@safepay");
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

    private record ConcurrentResult<A, B>(A first, B second) {
    }
}
