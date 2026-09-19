package com.ofss.repository;

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

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.test.context.transaction.BeforeTransaction;
import org.springframework.transaction.annotation.Transactional;

import com.ofss.beans.OtpChallenge;
import com.ofss.beans.OtpChallengeStatus;
import com.ofss.beans.PaymentCategory;
import com.ofss.beans.TransactionDb;
import com.ofss.beans.TransactionState;
import com.ofss.dto.transaction.AuthorizeTransactionRequest;
import com.ofss.dto.transaction.CreateTransactionRequest;
import com.ofss.dto.transaction.TransactionResponse;
import com.ofss.services.OtpPolicyProperties;
import com.ofss.services.TransactionService;

@SpringBootTest
@Transactional
class OtpChallengeDaoOracleIntegrationTest {

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private TransactionDao transactionDao;

    @Autowired
    private OtpChallengeDao otpChallengeDao;

    @Autowired
    private OtpPolicyProperties policy;

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
    void persistsExactPendingChallengeAndReadsOwnedLatest() {
        TransactionDb transaction = veryHighTransaction();
        OffsetDateTime issuedAt = now();

        OtpChallenge saved = otpChallengeDao.saveAndFlush(
                challenge(transaction, "hash-one", issuedAt));

        OtpChallenge latest = otpChallengeDao
                .findFirstByTransaction_TransactionIdAndCustomer_UserIdOrderByCreatedAtDescOtpChallengeIdDesc(
                        transaction.getTransactionId(),
                        customerId)
                .orElseThrow();

        assertThat(latest.getOtpChallengeId())
                .isEqualTo(saved.getOtpChallengeId());
        assertThat(latest.getStatus())
                .isEqualTo(OtpChallengeStatus.PENDING);
        assertThat(latest.getExpiresAt())
                .isEqualTo(issuedAt.plusMinutes(5));
        assertThat(latest.getAttemptCount()).isZero();
        assertThat(latest.getMaxAttempts()).isEqualTo(3);
    }

    @Test
    void lockedQueriesRequireMatchingTransactionAndCustomer() {
        TransactionDb transaction = veryHighTransaction();
        OtpChallenge saved = otpChallengeDao.saveAndFlush(
                challenge(transaction, "hash-two", now()));

        assertThat(otpChallengeDao.findOwnedByIdForUpdate(
                saved.getOtpChallengeId(),
                transaction.getTransactionId(),
                customerId)).isPresent();
        assertThat(otpChallengeDao.findPendingOwnedForUpdate(
                transaction.getTransactionId(),
                customerId)).isPresent();

        assertThat(otpChallengeDao.findOwnedByIdForUpdate(
                saved.getOtpChallengeId(),
                transaction.getTransactionId(),
                customerId + 999L)).isEmpty();
        assertThat(otpChallengeDao.findPendingOwnedForUpdate(
                transaction.getTransactionId(),
                customerId + 999L)).isEmpty();
    }

    @Test
    void countsOnlyIssuesInsideSuppliedVerificationCycle() {
        TransactionDb transaction = veryHighTransaction();
        OffsetDateTime cycleStart = now();

        OtpChallenge first = challenge(
                transaction,
                "hash-three-a",
                cycleStart);
        first.cancel(cycleStart.plusSeconds(30));
        otpChallengeDao.saveAndFlush(first);

        otpChallengeDao.saveAndFlush(challenge(
                transaction,
                "hash-three-b",
                cycleStart.plusSeconds(31)));

        assertThat(otpChallengeDao
                .countByTransaction_TransactionIdAndCustomer_UserIdAndCreatedAtGreaterThanEqual(
                        transaction.getTransactionId(),
                        customerId,
                        cycleStart))
                .isEqualTo(2L);
        assertThat(otpChallengeDao
                .findAllByTransaction_TransactionIdAndCustomer_UserIdOrderByCreatedAtAscOtpChallengeIdAsc(
                        transaction.getTransactionId(),
                        customerId))
                .extracting(OtpChallenge::getStatus)
                .containsExactly(
                        OtpChallengeStatus.CANCELLED,
                        OtpChallengeStatus.PENDING);
    }

    @Test
    void databaseRejectsSecondPendingChallengeForTransaction() {
        TransactionDb transaction = veryHighTransaction();
        OffsetDateTime issuedAt = now();

        otpChallengeDao.saveAndFlush(challenge(
                transaction,
                "hash-four-a",
                issuedAt));

        assertThatThrownBy(() -> otpChallengeDao.saveAndFlush(
                challenge(
                        transaction,
                        "hash-four-b",
                        issuedAt.plusSeconds(1))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private TransactionDb veryHighTransaction() {
        TransactionResponse created =
                transactionService.createTransaction(
                        customerId,
                        new CreateTransactionRequest(
                                sourceAccountId,
                                beneficiaryId,
                                new BigDecimal("100000.01"),
                                "OTP integration payment",
                                "OTP-" + fixtureToken,
                                PaymentCategory.MEDICAL));

        TransactionResponse authorized =
                transactionService.authorizeTransaction(
                        customerId,
                        Long.valueOf(created.transactionId()),
                        new AuthorizeTransactionRequest(true));

        assertThat(authorized.state())
                .isEqualTo(TransactionState.VERIFICATION_REQUIRED);

        return transactionDao.findByIdForUpdate(
                Long.valueOf(authorized.transactionId()))
                .orElseThrow();
    }

    private OtpChallenge challenge(
            TransactionDb transaction,
            String hash,
            OffsetDateTime issuedAt) {

        return OtpChallenge.issue(
                transaction,
                hash,
                policy,
                issuedAt);
    }

    private static OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC)
                .withNano(0);
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
            statement.setString(2, "OTP Integration Customer");
            statement.setString(
                    3,
                    "otp-" + fixtureToken + "@example.invalid");
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
            statement.setString(3, "OTP" + fixtureToken);
            statement.setString(4, "SafePay OTP Integration Bank");
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
            statement.setString(3, "OTP Integration Beneficiary");
            statement.setString(
                    4,
                    "otp." + fixtureToken + "@safepay");
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
