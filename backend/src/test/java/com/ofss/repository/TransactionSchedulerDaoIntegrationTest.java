package com.ofss.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.test.context.transaction.BeforeTransaction;
import org.springframework.transaction.annotation.Transactional;

import com.ofss.beans.PaymentCategory;
import com.ofss.dto.transaction.AuthorizeTransactionRequest;
import com.ofss.dto.transaction.CreateTransactionRequest;
import com.ofss.dto.transaction.TransactionResponse;
import com.ofss.services.TransactionService;

import jakarta.persistence.EntityManager;

@SpringBootTest(
        properties = "safepay.protection-scheduler.enabled=false")
@Transactional
class TransactionSchedulerDaoIntegrationTest {

    @Autowired
    private TransactionDao transactionDao;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private ReportingReadRepository reporting;

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

    @BeforeTransaction
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

    @AfterTransaction
    void removeOwnerFixtures() throws SQLException {
        try (Connection connection = ownerConnection()) {
            connection.setAutoCommit(false);

            try {
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
    void obtainsAuthoritativeOracleTime() {
        OffsetDateTime before = OffsetDateTime.now(ZoneOffset.UTC)
                .minusMinutes(1);

        OffsetDateTime databaseTime =
                transactionDao.currentDatabaseTime();

        OffsetDateTime after = OffsetDateTime.now(ZoneOffset.UTC)
                .plusMinutes(1);
        assertThat(databaseTime).isBetween(before, after);
    }

    @Test
    void discoversOnlyExpiredProtectedTransactions() {
        TransactionResponse expiredMedium = authorize(
                create("5000.01", "EXPIRED"));
        TransactionResponse futureMedium = authorize(
                create("5000.01", "FUTURE"));
        TransactionResponse releasedLow = authorize(
                create("100.00", "LOW"));
        TransactionResponse veryHigh = authorize(
                create("100000.01", "VERY-HIGH"));

        forceExpired(id(expiredMedium), 20);

        List<Long> candidateIds = transactionDao
                .findExpiredProtectedTransactionIds(50);

        assertThat(candidateIds)
                .containsExactly(id(expiredMedium))
                .doesNotContain(
                        id(futureMedium),
                        id(releasedLow),
                        id(veryHigh));
        assertThat(reporting.statistics().expiredProtectedPayments()).isEqualTo(candidateIds.size());
    }

    @Test
    void returnsOldestDeadlinesFirstAndHonoursBatchLimit() {
        TransactionResponse oldest = authorize(
                create("5000.01", "OLDEST"));
        TransactionResponse middle = authorize(
                create("5000.01", "MIDDLE"));
        TransactionResponse newest = authorize(
                create("5000.01", "NEWEST"));

        forceExpired(id(oldest), 30);
        forceExpired(id(middle), 20);
        forceExpired(id(newest), 10);

        assertThat(transactionDao
                .findExpiredProtectedTransactionIds(2))
                .containsExactly(id(oldest), id(middle));
    }

    private TransactionResponse create(
            String amount,
            String suffix) {

        BigDecimal paymentAmount = new BigDecimal(amount);
        return transactionService.createTransaction(
                customerId,
                new CreateTransactionRequest(
                        sourceAccountId,
                        beneficiaryId,
                        paymentAmount,
                        "Scheduler integration payment",
                        "SCH-" + suffix + "-" + fixtureToken,
                        PaymentCategory.appliesTo(paymentAmount)
                                ? PaymentCategory.MEDICAL : null));
    }

    private TransactionResponse authorize(
            TransactionResponse created) {

        return transactionService.authorizeTransaction(
                customerId,
                id(created),
                new AuthorizeTransactionRequest(true));
    }

    private void forceExpired(
            Long transactionId,
            int secondsAgo) {

        entityManager.flush();

        int updated = entityManager.createNativeQuery(
                """
                UPDATE SAFEPAY_OWNER.PAYMENT_TRANSACTION
                   SET reserved_at = SYSTIMESTAMP
                           - NUMTODSINTERVAL(:reservedSeconds, 'SECOND'),
                       protected_until = SYSTIMESTAMP
                           - NUMTODSINTERVAL(:deadlineSeconds, 'SECOND')
                 WHERE transaction_id = :transactionId
                """)
                .setParameter(
                        "reservedSeconds",
                        secondsAgo + 1)
                .setParameter("deadlineSeconds", secondsAgo)
                .setParameter("transactionId", transactionId)
                .executeUpdate();

        assertThat(updated).isEqualTo(1);
        entityManager.clear();
    }

    private static Long id(TransactionResponse response) {
        return Long.valueOf(response.transactionId());
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
            statement.setString(2, "Scheduler Integration Customer");
            statement.setString(
                    3,
                    "scheduler-" + fixtureToken + "@example.invalid");
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
            statement.setString(3, "SC" + fixtureToken);
            statement.setString(4, "SafePay Scheduler Bank");
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
            statement.setString(3, "Scheduler Beneficiary");
            statement.setString(
                    4,
                    "scheduler." + fixtureToken + "@safepay");
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
