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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.ofss.excp.BusinessRuleException;

import jakarta.persistence.EntityManager;

@SpringBootTest
@Import(
        AccountFundsServiceOracleIntegrationTest
                .RollbackTestConfiguration.class)
class AccountFundsServiceOracleIntegrationTest {

    private static final BigDecimal INITIAL_SOURCE_BALANCE =
            new BigDecimal("1000.00");

    private static final BigDecimal MAXIMUM_ORACLE_BALANCE =
            new BigDecimal("9999999999999999.99");

    @Autowired
    private AccountFundsService accountFundsService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RollbackProbe rollbackProbe;

    @Value("${spring.flyway.url}")
    private String ownerUrl;

    @Value("${spring.flyway.user}")
    private String ownerUsername;

    @Value("${spring.flyway.password}")
    private String ownerPassword;

    private Long ownerUserId;
    private Long sourceAccountId;
    private Long clearingAccountId;

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
                ownerUserId = nextSequenceValue(
                        connection,
                        "SAFEPAY_OWNER.SEQ_APP_USER_ID");

                sourceAccountId = nextSequenceValue(
                        connection,
                        "SAFEPAY_OWNER.SEQ_ACCOUNT_ID");

                clearingAccountId = nextSequenceValue(
                        connection,
                        "SAFEPAY_OWNER.SEQ_ACCOUNT_ID");

                insertFixtureUser(connection);
                insertSourceAccount(connection);
                insertClearingAccount(connection);

                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    @AfterEach
    void removeFixtures() throws SQLException {
        if (ownerUserId == null) {
            return;
        }

        try (Connection connection = ownerConnection()) {
            connection.setAutoCommit(false);

            try {
                deleteAccount(
                        connection,
                        sourceAccountId);

                deleteAccount(
                        connection,
                        clearingAccountId);

                deleteUser(
                        connection,
                        ownerUserId);

                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    @Test
    void commitsReservationAndReleaseAgainstOracle() {
        OffsetDateTime occurredAt =
                OffsetDateTime.now(ZoneOffset.UTC);

        accountFundsService.reserveOwnedFunds(
                ownerUserId,
                sourceAccountId,
                new BigDecimal("250.00"),
                occurredAt);

        AccountBalances reserved =
                readBalances(sourceAccountId);

        assertThat(reserved.currentBalance())
                .isEqualByComparingTo("1000.00");

        assertThat(reserved.reservedAmount())
                .isEqualByComparingTo("250.00");

        assertThat(reserved.availableBalance())
                .isEqualByComparingTo("750.00");

        accountFundsService.releaseReservedFunds(
                sourceAccountId,
                new BigDecimal("100.00"),
                occurredAt.plusSeconds(1));

        AccountBalances released =
                readBalances(sourceAccountId);

        assertThat(released.currentBalance())
                .isEqualByComparingTo("1000.00");

        assertThat(released.reservedAmount())
                .isEqualByComparingTo("150.00");

        assertThat(released.availableBalance())
                .isEqualByComparingTo("850.00");
    }

    @Test
    void rejectsReservationAboveAvailableBalance() {
        assertThatThrownBy(
                () -> accountFundsService.reserveOwnedFunds(
                        ownerUserId,
                        sourceAccountId,
                        new BigDecimal("1000.01"),
                        OffsetDateTime.now(ZoneOffset.UTC)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("errorCode")
                .isEqualTo(
                        "INSUFFICIENT_AVAILABLE_BALANCE");

        AccountBalances balances =
                readBalances(sourceAccountId);

        assertThat(balances.currentBalance())
                .isEqualByComparingTo("1000.00");

        assertThat(balances.reservedAmount())
                .isEqualByComparingTo("0.00");

        assertThat(balances.availableBalance())
                .isEqualByComparingTo("1000.00");
    }

    @Test
    void settlesSourceAndClearingAccountsAtomically() {
        OffsetDateTime occurredAt =
                OffsetDateTime.now(ZoneOffset.UTC);

        accountFundsService.reserveOwnedFunds(
                ownerUserId,
                sourceAccountId,
                new BigDecimal("300.00"),
                occurredAt);

        accountFundsService.settleReservedFunds(
                sourceAccountId,
                clearingAccountId,
                new BigDecimal("300.00"),
                occurredAt.plusSeconds(1));

        AccountBalances source =
                readBalances(sourceAccountId);

        AccountBalances clearing =
                readBalances(clearingAccountId);

        assertThat(source.currentBalance())
                .isEqualByComparingTo("700.00");

        assertThat(source.reservedAmount())
                .isEqualByComparingTo("0.00");

        assertThat(source.availableBalance())
                .isEqualByComparingTo("700.00");

        assertThat(clearing.currentBalance())
                .isEqualByComparingTo("300.00");

        assertThat(clearing.reservedAmount())
                .isEqualByComparingTo("0.00");
    }

    @Test
    void rollsBackSettlementWhenClearingCreditOverflows() {
        OffsetDateTime occurredAt =
                OffsetDateTime.now(ZoneOffset.UTC);

        accountFundsService.reserveOwnedFunds(
                ownerUserId,
                sourceAccountId,
                new BigDecimal("250.00"),
                occurredAt);

        setClearingBalanceAsOwner(
                MAXIMUM_ORACLE_BALANCE);

        assertThatThrownBy(
                () -> accountFundsService.settleReservedFunds(
                        sourceAccountId,
                        clearingAccountId,
                        new BigDecimal("250.00"),
                        occurredAt.plusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);

        AccountBalances source =
                readBalances(sourceAccountId);

        AccountBalances clearing =
                readBalances(clearingAccountId);

        /*
         * The earlier reservation remains, but the attempted
         * settlement debit and clearing credit are both absent.
         */
        assertThat(source.currentBalance())
                .isEqualByComparingTo("1000.00");

        assertThat(source.reservedAmount())
                .isEqualByComparingTo("250.00");

        assertThat(source.availableBalance())
                .isEqualByComparingTo("750.00");

        assertThat(clearing.currentBalance())
                .isEqualByComparingTo(
                        MAXIMUM_ORACLE_BALANCE);
    }

    @Test
    void rollsBackAReservationAlreadyFlushedToOracle() {
        assertThatThrownBy(
                () -> rollbackProbe.reserveFlushAndFail(
                        ownerUserId,
                        sourceAccountId,
                        new BigDecimal("200.00"),
                        OffsetDateTime.now(ZoneOffset.UTC)))
                .isInstanceOf(
                        ForcedRollbackException.class);

        AccountBalances balances =
                readBalances(sourceAccountId);

        /*
         * RollbackProbe explicitly flushes the UPDATE before failing.
         * This proves that the database transaction rolls it back.
         */
        assertThat(balances.currentBalance())
                .isEqualByComparingTo("1000.00");

        assertThat(balances.reservedAmount())
                .isEqualByComparingTo("0.00");

        assertThat(balances.availableBalance())
                .isEqualByComparingTo("1000.00");
    }

    @Test
    void concurrentReservationsAllowOnlyOneWinner()
            throws Exception {

        BigDecimal competingAmount =
                new BigDecimal("700.00");

        CountDownLatch ready =
                new CountDownLatch(2);

        CountDownLatch start =
                new CountDownLatch(1);

        ExecutorService executor =
                Executors.newFixedThreadPool(2);

        try {
            Future<Boolean> firstAttempt =
                    executor.submit(
                            () -> attemptReservation(
                                    competingAmount,
                                    ready,
                                    start));

            Future<Boolean> secondAttempt =
                    executor.submit(
                            () -> attemptReservation(
                                    competingAmount,
                                    ready,
                                    start));

            assertThat(
                    ready.await(
                            10,
                            TimeUnit.SECONDS))
                    .isTrue();

            start.countDown();

            boolean firstSucceeded =
                    firstAttempt.get(
                            20,
                            TimeUnit.SECONDS);

            boolean secondSucceeded =
                    secondAttempt.get(
                            20,
                            TimeUnit.SECONDS);

            long successfulAttempts =
                    Stream.of(
                                    firstSucceeded,
                                    secondSucceeded)
                            .filter(Boolean::booleanValue)
                            .count();

            assertThat(successfulAttempts)
                    .isEqualTo(1);

            AccountBalances balances =
                    readBalances(sourceAccountId);

            assertThat(balances.currentBalance())
                    .isEqualByComparingTo("1000.00");

            assertThat(balances.reservedAmount())
                    .isEqualByComparingTo("700.00");

            assertThat(balances.availableBalance())
                    .isEqualByComparingTo("300.00");
        } finally {
            start.countDown();
            executor.shutdownNow();

            executor.awaitTermination(
                    5,
                    TimeUnit.SECONDS);
        }
    }

    private boolean attemptReservation(
            BigDecimal amount,
            CountDownLatch ready,
            CountDownLatch start)
            throws InterruptedException {

        ready.countDown();

        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException(
                    "Concurrent reservation start timed out");
        }

        try {
            accountFundsService.reserveOwnedFunds(
                    ownerUserId,
                    sourceAccountId,
                    amount,
                    OffsetDateTime.now(ZoneOffset.UTC));

            return true;
        } catch (BusinessRuleException exception) {
            assertThat(exception.getErrorCode())
                    .isEqualTo(
                            "INSUFFICIENT_AVAILABLE_BALANCE");

            return false;
        }
    }

    private AccountBalances readBalances(
            Long accountId) {

        return jdbcTemplate.queryForObject(
                """
                SELECT
                    CURRENT_BALANCE,
                    RESERVED_AMOUNT,
                    AVAILABLE_BALANCE
                FROM SAFEPAY_OWNER.ACCOUNT
                WHERE ACCOUNT_ID = ?
                """,
                (resultSet, rowNumber) ->
                        new AccountBalances(
                                resultSet.getBigDecimal(
                                        "CURRENT_BALANCE"),
                                resultSet.getBigDecimal(
                                        "RESERVED_AMOUNT"),
                                resultSet.getBigDecimal(
                                        "AVAILABLE_BALANCE")),
                accountId);
    }

    private void setClearingBalanceAsOwner(
            BigDecimal balance) {

        try (Connection connection = ownerConnection();
             PreparedStatement statement =
                     connection.prepareStatement(
                             """
                             UPDATE SAFEPAY_OWNER.ACCOUNT
                                SET CURRENT_BALANCE = ?,
                                    UPDATED_AT = SYSTIMESTAMP,
                                    VERSION_NO = VERSION_NO + 1
                              WHERE ACCOUNT_ID = ?
                             """)) {

            statement.setBigDecimal(1, balance);
            statement.setLong(2, clearingAccountId);

            int affectedRows = statement.executeUpdate();

            if (affectedRows != 1) {
                throw new IllegalStateException(
                        "Unable to prepare clearing fixture");
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Unable to update clearing fixture",
                    exception);
        }
    }

    private Connection ownerConnection()
            throws SQLException {

        return DriverManager.getConnection(
                ownerUrl,
                ownerUsername,
                ownerPassword);
    }

    private static Long nextSequenceValue(
            Connection connection,
            String qualifiedSequenceName)
            throws SQLException {

        String sql =
                "SELECT "
                        + qualifiedSequenceName
                        + ".NEXTVAL FROM DUAL";

        try (Statement statement =
                     connection.createStatement();
             ResultSet resultSet =
                     statement.executeQuery(sql)) {

            if (!resultSet.next()) {
                throw new SQLException(
                        "Sequence returned no value");
            }

            return resultSet.getLong(1);
        }
    }

    private void insertFixtureUser(
            Connection connection)
            throws SQLException {

        try (PreparedStatement statement =
                     connection.prepareStatement(
                             """
                             INSERT INTO SAFEPAY_OWNER.APP_USER
                             (
                                 USER_ID,
                                 FULL_NAME,
                                 EMAIL,
                                 PASSWORD_HASH
                             )
                             VALUES (?, ?, ?, ?)
                             """)) {

            statement.setLong(1, ownerUserId);
            statement.setString(
                    2,
                    "SafePay Integration User");
            statement.setString(
                    3,
                    "safepay-it-"
                            + fixtureToken
                            + "@example.invalid");
            statement.setString(
                    4,
                    "integration-test-password-hash");

            statement.executeUpdate();
        }
    }

    private void insertSourceAccount(
            Connection connection)
            throws SQLException {

        try (PreparedStatement statement =
                     connection.prepareStatement(
                             """
                             INSERT INTO SAFEPAY_OWNER.ACCOUNT
                             (
                                 ACCOUNT_ID,
                                 OWNER_USER_ID,
                                 ACCOUNT_NUMBER,
                                 ACCOUNT_TYPE,
                                 BANK_NAME,
                                 IFSC_CODE,
                                 CURRENT_BALANCE,
                                 RESERVED_AMOUNT
                             )
                             VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                             """)) {

            statement.setLong(1, sourceAccountId);
            statement.setLong(2, ownerUserId);
            statement.setString(
                    3,
                    "SPIT-S-" + fixtureToken);
            statement.setString(4, "SAVINGS");
            statement.setString(
                    5,
                    "SafePay Integration Bank");
            statement.setString(
                    6,
                    "HDFC0000001");
            statement.setBigDecimal(
                    7,
                    INITIAL_SOURCE_BALANCE);
            statement.setBigDecimal(
                    8,
                    BigDecimal.ZERO.setScale(2));

            statement.executeUpdate();
        }
    }

    private void insertClearingAccount(
            Connection connection)
            throws SQLException {

        try (PreparedStatement statement =
                     connection.prepareStatement(
                             """
                             INSERT INTO SAFEPAY_OWNER.ACCOUNT
                             (
                                 ACCOUNT_ID,
                                 ACCOUNT_NUMBER,
                                 ACCOUNT_TYPE,
                                 BANK_NAME,
                                 CURRENT_BALANCE,
                                 RESERVED_AMOUNT
                             )
                             VALUES (?, ?, ?, ?, ?, ?)
                             """)) {

            statement.setLong(1, clearingAccountId);
            statement.setString(
                    2,
                    "SPIT-C-" + fixtureToken);
            statement.setString(
                    3,
                    "OUTBOUND_CLEARING");
            statement.setString(
                    4,
                    "SafePay Clearing");
            statement.setBigDecimal(
                    5,
                    BigDecimal.ZERO.setScale(2));
            statement.setBigDecimal(
                    6,
                    BigDecimal.ZERO.setScale(2));

            statement.executeUpdate();
        }
    }

    private static void deleteAccount(
            Connection connection,
            Long accountId)
            throws SQLException {

        if (accountId == null) {
            return;
        }

        try (PreparedStatement statement =
                     connection.prepareStatement(
                             """
                             DELETE FROM SAFEPAY_OWNER.ACCOUNT
                             WHERE ACCOUNT_ID = ?
                             """)) {

            statement.setLong(1, accountId);
            statement.executeUpdate();
        }
    }

    private static void deleteUser(
            Connection connection,
            Long userId)
            throws SQLException {

        try (PreparedStatement statement =
                     connection.prepareStatement(
                             """
                             DELETE FROM SAFEPAY_OWNER.APP_USER
                             WHERE USER_ID = ?
                             """)) {

            statement.setLong(1, userId);
            statement.executeUpdate();
        }
    }

    private record AccountBalances(
            BigDecimal currentBalance,
            BigDecimal reservedAmount,
            BigDecimal availableBalance) {
    }

    static class ForcedRollbackException
            extends RuntimeException {

        private static final long serialVersionUID = 1L;

        ForcedRollbackException() {
            super("Forced integration-test rollback");
        }
    }

    static class RollbackProbe {

        private final AccountFundsService accountFundsService;
        private final EntityManager entityManager;

        RollbackProbe(
                AccountFundsService accountFundsService,
                EntityManager entityManager) {

            this.accountFundsService =
                    accountFundsService;

            this.entityManager =
                    entityManager;
        }

        @Transactional
        public void reserveFlushAndFail(
                Long ownerUserId,
                Long sourceAccountId,
                BigDecimal amount,
                OffsetDateTime occurredAt) {

            accountFundsService.reserveOwnedFunds(
                    ownerUserId,
                    sourceAccountId,
                    amount,
                    occurredAt);

            /*
             * Force Hibernate to issue the Oracle UPDATE before
             * the exception. The outer transaction must undo it.
             */
            entityManager.flush();

            throw new ForcedRollbackException();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class RollbackTestConfiguration {

        @Bean
        RollbackProbe rollbackProbe(
                AccountFundsService accountFundsService,
                EntityManager entityManager) {

            return new RollbackProbe(
                    accountFundsService,
                    entityManager);
        }
    }
}