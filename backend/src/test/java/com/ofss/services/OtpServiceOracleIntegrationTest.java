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
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.test.context.transaction.BeforeTransaction;
import org.springframework.transaction.annotation.Transactional;

import com.ofss.beans.OtpChallenge;
import com.ofss.beans.OtpChallengeStatus;
import com.ofss.beans.TransactionState;
import com.ofss.dto.transaction.AuthorizeTransactionRequest;
import com.ofss.dto.transaction.CreateTransactionRequest;
import com.ofss.dto.transaction.TransactionResponse;
import com.ofss.excp.ResourceNotFoundExcp;
import com.ofss.repository.AccountDao;
import com.ofss.repository.OtpChallengeDao;
import com.ofss.repository.TransactionDao;

@SpringBootTest
@Transactional
@Import(OtpServiceOracleIntegrationTest.OtpTestConfiguration.class)
class OtpServiceOracleIntegrationTest {

    private static final String TEST_OTP = "123456";

    @Autowired private TransactionService transactionService;
    @Autowired private OtpService otpService;
    @Autowired private OtpChallengeDao otpChallengeDao;
    @Autowired private TransactionDao transactionDao;
    @Autowired private AccountDao accountDao;
    @Autowired private TestOtpDeliveryGateway deliveryGateway;

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
    private String customerEmail;

    @BeforeTransaction
    void createOwnerFixtures() throws SQLException {
        fixtureToken = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 20);
        customerEmail = "otp-service-" + fixtureToken
                + "@example.invalid";

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

                insertUser(
                        connection,
                        customerId,
                        "OTP Service Customer",
                        customerEmail);
                insertUser(
                        connection,
                        otherCustomerId,
                        "Other OTP Customer",
                        "other-otp-" + fixtureToken
                                + "@example.invalid");
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

    @BeforeEach
    void resetDeliveryEvidence() {
        deliveryGateway.reset();
    }

    @Test
    void issuesOracleChallengeUsingStoredEmailAndNoRawOtpAtRest() {
        TransactionResponse payment = veryHighTransaction();

        OtpChallengeResult result = otpService.issue(
                customerId,
                id(payment));

        OtpChallenge challenge = latest(id(payment));

        assertThat(result.accepted()).isTrue();
        assertThat(result.response().maskedDestination())
                .endsWith("@example.invalid")
                .doesNotContain(customerEmail);
        assertThat(deliveryGateway.recipient())
                .isEqualTo(customerEmail);
        assertThat(deliveryGateway.deliveredCode())
                .isEqualTo(TEST_OTP);
        assertThat(challenge.getOtpHash())
                .startsWith("SHA-256$")
                .isNotEqualTo(TEST_OTP);
        assertThat(challenge.getStatus())
                .isEqualTo(OtpChallengeStatus.PENDING);
    }

    @Test
    void successfulVerificationMovesToPendingReviewAndKeepsReservation() {
        TransactionResponse payment = veryHighTransaction();
        OtpChallengeResult issued = otpService.issue(
                customerId,
                id(payment));

        OtpVerificationResult verified = otpService.verify(
                customerId,
                id(payment),
                Long.valueOf(issued.response().challengeId()),
                TEST_OTP);

        assertThat(verified.verified()).isTrue();
        assertThat(verified.response().challengeStatus())
                .isEqualTo(OtpChallengeStatus.VERIFIED);
        assertThat(verified.response().transactionState())
                .isEqualTo(TransactionState.PENDING_RISK_REVIEW);
        assertThat(transactionDao.findByIdForUpdate(id(payment))
                .orElseThrow()
                .getReservedAmount())
                .isEqualByComparingTo("100000.01");
        assertThat(accountDao.findById(sourceAccountId)
                .orElseThrow()
                .getReservedAmount())
                .isEqualByComparingTo("100000.01");
    }

    @Test
    void thirdIncorrectAttemptLocksChallengeCancelsAndReleasesFunds() {
        TransactionResponse payment = veryHighTransaction();
        OtpChallengeResult issued = otpService.issue(
                customerId,
                id(payment));
        Long challengeId = Long.valueOf(
                issued.response().challengeId());

        OtpVerificationResult first = otpService.verify(
                customerId,
                id(payment),
                challengeId,
                "000000");
        OtpVerificationResult second = otpService.verify(
                customerId,
                id(payment),
                challengeId,
                "000000");
        OtpVerificationResult third = otpService.verify(
                customerId,
                id(payment),
                challengeId,
                "000000");

        assertThat(first.errorCode()).isEqualTo("OTP_INVALID");
        assertThat(second.response().remainingAttempts()).isEqualTo(1);
        assertThat(third.errorCode())
                .isEqualTo("OTP_POLICY_EXHAUSTED");
        assertThat(third.response().challengeStatus())
                .isEqualTo(OtpChallengeStatus.LOCKED);
        assertThat(third.response().transactionState())
                .isEqualTo(TransactionState.CANCELLED);
        assertThat(transactionDao.findByIdForUpdate(id(payment))
                .orElseThrow()
                .getReservedAmount())
                .isEqualByComparingTo("0.00");
        assertThat(accountDao.findById(sourceAccountId)
                .orElseThrow()
                .getReservedAmount())
                .isEqualByComparingTo("0.00");
    }

    @Test
    void immediateResendIsRejectedByDatabaseTimeCooldown() {
        TransactionResponse payment = veryHighTransaction();
        otpService.issue(customerId, id(payment));

        assertThatThrownBy(() -> otpService.resend(
                customerId,
                id(payment)))
                .isInstanceOf(com.ofss.excp.BusinessRuleException.class)
                .extracting("errorCode")
                .isEqualTo("OTP_RESEND_COOLDOWN");

        assertThat(otpChallengeDao
                .findAllByTransaction_TransactionIdAndCustomer_UserIdOrderByCreatedAtAscOtpChallengeIdAsc(
                        id(payment),
                        customerId))
                .hasSize(1)
                .extracting(OtpChallenge::getStatus)
                .containsExactly(OtpChallengeStatus.PENDING);
    }

    @Test
    void hidesOwnedTransactionFromAnotherCustomer() {
        TransactionResponse payment = veryHighTransaction();

        assertThatThrownBy(() -> otpService.issue(
                otherCustomerId,
                id(payment)))
                .isInstanceOf(ResourceNotFoundExcp.class)
                .extracting("errorCode")
                .isEqualTo("TRANSACTION_NOT_FOUND");

        assertThat(deliveryGateway.recipient()).isNull();
    }

    private TransactionResponse veryHighTransaction() {
        TransactionResponse created =
                transactionService.createTransaction(
                        customerId,
                        new CreateTransactionRequest(
                                sourceAccountId,
                                beneficiaryId,
                                new BigDecimal("100000.01"),
                                "OTP workflow integration payment",
                                "OTP-SVC-" + fixtureToken, com.ofss.beans.PaymentCategory.MEDICAL));

        TransactionResponse authorized =
                transactionService.authorizeTransaction(
                        customerId,
                        id(created),
                        new AuthorizeTransactionRequest(true));

        assertThat(authorized.state())
                .isEqualTo(TransactionState.VERIFICATION_REQUIRED);

        return authorized;
    }

    private OtpChallenge latest(Long transactionId) {
        return otpChallengeDao
                .findFirstByTransaction_TransactionIdAndCustomer_UserIdOrderByCreatedAtDescOtpChallengeIdDesc(
                        transactionId,
                        customerId)
                .orElseThrow();
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

    private static void insertUser(
            Connection connection,
            Long userId,
            String name,
            String email) throws SQLException {

        try (PreparedStatement statement = connection.prepareStatement(
                """
                INSERT INTO SAFEPAY_OWNER.APP_USER
                    (USER_ID, FULL_NAME, EMAIL, PASSWORD_HASH)
                VALUES (?, ?, ?, ?)
                """)) {

            statement.setLong(1, userId);
            statement.setString(2, name);
            statement.setString(3, email);
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
            statement.setString(3, "OTP2" + fixtureToken);
            statement.setString(4, "SafePay OTP Workflow Bank");
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
            statement.setString(3, "OTP Workflow Beneficiary");
            statement.setString(
                    4,
                    "otp.service." + fixtureToken + "@safepay");
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

    @TestConfiguration(proxyBeanMethods = false)
    static class OtpTestConfiguration {

        @Bean
        @Primary
        OtpCodeGenerator deterministicOtpCodeGenerator() {
            return () -> new OtpCode(TEST_OTP, 6);
        }

        @Bean
        @Primary
        TestOtpDeliveryGateway testOtpDeliveryGateway() {
            return new TestOtpDeliveryGateway();
        }
    }

    static final class TestOtpDeliveryGateway
            implements OtpDeliveryGateway {

        private final AtomicReference<String> recipient =
                new AtomicReference<>();
        private final AtomicReference<String> deliveredCode =
                new AtomicReference<>();

        @Override
        public void deliver(
                String recipientEmail,
                OtpCode otpCode,
                OffsetDateTime expiresAt) {

            recipient.set(recipientEmail);
            deliveredCode.set(otpCode.value());
        }

        String recipient() {
            return recipient.get();
        }

        String deliveredCode() {
            return deliveredCode.get();
        }

        void reset() {
            recipient.set(null);
            deliveredCode.set(null);
        }
    }
}
