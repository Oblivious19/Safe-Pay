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
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.test.context.transaction.BeforeTransaction;
import org.springframework.transaction.annotation.Transactional;

import com.ofss.beans.TransactionRiskFactorCode;
import com.ofss.beans.TransactionState;
import com.ofss.dto.transaction.AuthorizeTransactionRequest;
import com.ofss.dto.transaction.CreateTransactionRequest;
import com.ofss.dto.transaction.TransactionResponse;
import com.ofss.excp.ResourceNotFoundExcp;
import com.ofss.repository.AccountDao;
import com.ofss.repository.TransactionRiskFactorDao;

@SpringBootTest
@Transactional
class TransactionServiceOracleIntegrationTest {

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private AccountDao accountDao;

    @Autowired
    private TransactionRiskFactorDao riskFactorDao;

    @Value("${spring.flyway.url}")
    private String ownerUrl;

    @Value("${spring.flyway.user}")
    private String ownerUsername;

    @Value("${spring.flyway.password}")
    private String ownerPassword;

    private Long customerId;
    private Long otherCustomerId;
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
                otherCustomerId = nextSequenceValue(
                        connection,
                        "SAFEPAY_OWNER.SEQ_APP_USER_ID");
                sourceAccountId = nextSequenceValue(
                        connection,
                        "SAFEPAY_OWNER.SEQ_ACCOUNT_ID");
                beneficiaryId = nextSequenceValue(
                        connection,
                        "SAFEPAY_OWNER.SEQ_BENEFICIARY_ID");

                insertUser(connection, customerId, "owner");
                insertUser(connection, otherCustomerId, "other");
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
                executeDelete(
                        connection,
                        "DELETE FROM SAFEPAY_OWNER.APP_USER "
                                + "WHERE USER_ID = ?",
                        otherCustomerId);
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    @Test
    void createsOnlyCreatedInstructionInOracle() {
        TransactionResponse response = create("100.00");

        assertThat(response.state()).isEqualTo(TransactionState.CREATED);
        assertThat(response.riskTier()).isNull();
        assertThat(response.reservedAmount()).isEqualByComparingTo("0.00");
        assertThat(riskFactorDao.findAllOwnedForTransaction(
                id(response),
                customerId)).isEmpty();
    }

    @Test
    void authorizesLowRiskWithReservationAndImmutableEvidence() {
        TransactionResponse response = authorize(create("100.00"));

        assertThat(response.state()).isEqualTo(TransactionState.RELEASED);
        assertThat(response.reservedAmount()).isEqualByComparingTo("100.00");
        assertThat(response.protectedUntil()).isNull();
        assertThat(accountDao.findById(sourceAccountId)
                .orElseThrow()
                .getReservedAmount()).isEqualByComparingTo("100.00");
        assertThat(riskFactorDao.findOwnedByFactorCode(
                id(response),
                customerId,
                TransactionRiskFactorCode.PAYMENT_AMOUNT)).isPresent();
    }

    @Test
    void protectsMediumRiskForTenSecondsAndCancelsAtomically() {
        TransactionResponse protectedPayment = authorize(
                create("5000.01"));

        assertThat(protectedPayment.state())
                .isEqualTo(TransactionState.PROTECTED);
        assertThat(Duration.between(
                protectedPayment.riskAssessedAt(),
                protectedPayment.protectedUntil()))
                .isEqualTo(Duration.ofSeconds(10));

        TransactionResponse cancelled = transactionService
                .cancelTransaction(
                        customerId,
                        id(protectedPayment));

        assertThat(cancelled.state())
                .isEqualTo(TransactionState.CANCELLED);
        assertThat(cancelled.reservedAmount())
                .isEqualByComparingTo("0.00");
        assertThat(accountDao.findById(sourceAccountId)
                .orElseThrow()
                .getReservedAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void protectsHighRiskForSixtySeconds() {
        TransactionResponse response = authorize(
                create("25000.01"));

        assertThat(response.state()).isEqualTo(TransactionState.PROTECTED);
        assertThat(Duration.between(
                response.riskAssessedAt(),
                response.protectedUntil()))
                .isEqualTo(Duration.ofSeconds(60));
    }

    @Test
    void routesVeryHighRiskToVerificationWithoutDeadline() {
        TransactionResponse response = authorize(
                create("100000.01"));

        assertThat(response.state())
                .isEqualTo(TransactionState.VERIFICATION_REQUIRED);
        assertThat(response.protectedUntil()).isNull();
        assertThat(response.protectionSeconds()).isNull();
        assertThat(response.reservedAmount())
                .isEqualByComparingTo("100000.01");
    }

    @Test
    void hidesTransactionAndRiskExplanationFromOtherCustomer() {
        TransactionResponse response = authorize(create("100.00"));

        assertThatThrownBy(() -> transactionService.getTransaction(
                otherCustomerId,
                id(response)))
                .isInstanceOf(ResourceNotFoundExcp.class)
                .extracting("errorCode")
                .isEqualTo("TRANSACTION_NOT_FOUND");

        assertThatThrownBy(() -> transactionService.getRiskExplanation(
                otherCustomerId,
                id(response)))
                .isInstanceOf(ResourceNotFoundExcp.class)
                .extracting("errorCode")
                .isEqualTo("TRANSACTION_NOT_FOUND");
    }

    private TransactionResponse create(String amount) {
        return transactionService.createTransaction(
                customerId,
                new CreateTransactionRequest(
                        sourceAccountId,
                        beneficiaryId,
                        new BigDecimal(amount),
                        "Oracle integration payment",
                        "IT-" + fixtureToken, com.ofss.beans.PaymentCategory.appliesTo(new BigDecimal(amount))
                                ? com.ofss.beans.PaymentCategory.MEDICAL : null));
    }

    private TransactionResponse authorize(
            TransactionResponse created) {

        return transactionService.authorizeTransaction(
                customerId,
                id(created),
                new AuthorizeTransactionRequest(true));
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

    private void insertUser(
            Connection connection,
            Long userId,
            String label) throws SQLException {

        try (PreparedStatement statement = connection.prepareStatement(
                """
                INSERT INTO SAFEPAY_OWNER.APP_USER
                    (USER_ID, FULL_NAME, EMAIL, PASSWORD_HASH)
                VALUES (?, ?, ?, ?)
                """)) {

            statement.setLong(1, userId);
            statement.setString(2, "Transaction Service " + label);
            statement.setString(
                    3,
                    "tx-service-" + label + "-" + fixtureToken
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
            statement.setString(3, "TS" + fixtureToken);
            statement.setString(4, "SafePay Transaction Service Bank");
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
            statement.setString(3, "Integration Beneficiary");
            statement.setString(
                    4,
                    "ts." + fixtureToken + "@safepay");
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
