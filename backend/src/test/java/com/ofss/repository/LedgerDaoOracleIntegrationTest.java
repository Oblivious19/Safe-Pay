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
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.test.context.transaction.BeforeTransaction;
import org.springframework.transaction.annotation.Transactional;

import com.ofss.beans.Account;
import com.ofss.beans.LedgerEntry;
import com.ofss.beans.LedgerEntryType;
import com.ofss.beans.LedgerPosting;
import com.ofss.beans.LedgerPostingStatus;
import com.ofss.beans.TransactionDb;
import com.ofss.beans.TransactionExceptionLog;
import com.ofss.beans.TransactionExceptionStatus;
import com.ofss.beans.TransactionProcessingStage;
import com.ofss.dto.transaction.AuthorizeTransactionRequest;
import com.ofss.dto.transaction.CreateTransactionRequest;
import com.ofss.dto.transaction.TransactionResponse;
import com.ofss.scheduler.SettlementProcessorProperties;
import com.ofss.services.SettlementPostingFactory;
import com.ofss.services.SettlementPostingPair;
import com.ofss.services.TransactionService;

import jakarta.persistence.EntityManager;

@SpringBootTest
@Transactional
class LedgerDaoOracleIntegrationTest {

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private TransactionDao transactionDao;

    @Autowired
    private AccountDao accountDao;

    @Autowired
    private LedgerPostingDao ledgerPostingDao;

    @Autowired
    private LedgerEntryDao ledgerEntryDao;

    @Autowired
    private TransactionExceptionDao transactionExceptionDao;

    @Autowired
    private EntityManager entityManager;

    @Value("${spring.flyway.url}")
    private String ownerUrl;

    @Value("${spring.flyway.user}")
    private String ownerUsername;

    @Value("${spring.flyway.password}")
    private String ownerPassword;

    private Long customerId;
    private Long sourceAccountId;
    private Long clearingAccountId;
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
                clearingAccountId = nextSequenceValue(
                        connection,
                        "SAFEPAY_OWNER.SEQ_ACCOUNT_ID");
                beneficiaryId = nextSequenceValue(
                        connection,
                        "SAFEPAY_OWNER.SEQ_BENEFICIARY_ID");

                insertUser(connection);
                insertCustomerAccount(connection);
                insertClearingAccount(connection);
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
                        "DELETE FROM SAFEPAY_OWNER.ACCOUNT "
                                + "WHERE ACCOUNT_ID = ?",
                        clearingAccountId);
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
    void persistsAndReadsCanonicalBalancedPair() {
        SettlementPostingPair pair = buildPair();

        persistPendingPair(pair);

        List<LedgerEntry> entries = ledgerEntryDao
                .findAllByPosting_PostingIdOrderByLineNumberAsc(
                        pair.posting().getPostingId());

        assertThat(entries)
                .extracting(LedgerEntry::getEntryType)
                .containsExactly(
                        LedgerEntryType.DEBIT,
                        LedgerEntryType.CREDIT);
        assertThat(entries)
                .extracting(LedgerEntry::getAmount)
                .allSatisfy(amount ->
                        assertThat(amount)
                                .isEqualByComparingTo("250.00"));
        assertThat(entries.get(0).getAccount().getAccountId())
                .isEqualTo(sourceAccountId);
        assertThat(entries.get(1).getAccount().getAccountId())
                .isEqualTo(clearingAccountId);
    }

    @Test
    void finalizesOnlyCompleteBalancedPair() {
        SettlementPostingPair pair = buildPair();
        persistPendingPair(pair);

        pair.posting().markPosted(now().plusSeconds(1));
        ledgerPostingDao.saveAndFlush(pair.posting());

        LedgerPosting locked = ledgerPostingDao
                .findByIdForUpdate(pair.posting().getPostingId())
                .orElseThrow();

        assertThat(locked.getStatus())
                .isEqualTo(LedgerPostingStatus.POSTED);
        assertThat(locked.getPostedAt()).isNotNull();
        assertThat(ledgerEntryDao
                .findAllByTransaction_TransactionIdOrderByLineNumberAsc(
                        locked.getTransaction().getTransactionId()))
                .hasSize(2);
    }

    @Test
    void databaseRejectsSecondPostingForSameTransaction() {
        TransactionDb transaction = releasedTransaction();
        Account clearingAccount = accountDao
                .findById(clearingAccountId)
                .orElseThrow();
        SettlementPostingFactory factory =
                settlementPostingFactory();

        SettlementPostingPair first = factory.create(
                transaction,
                clearingAccount,
                now());
        ledgerPostingDao.saveAndFlush(first.posting());

        SettlementPostingPair duplicate = factory.create(
                transaction,
                clearingAccount,
                now().plusSeconds(1));

        assertThatThrownBy(() ->
                ledgerPostingDao.saveAndFlush(
                        duplicate.posting()))
                .isInstanceOf(
                        DataIntegrityViolationException.class);
    }

    @Test
    void persistsAndLocksManualReviewException() {
        TransactionDb transaction = releasedTransaction();
        OffsetDateTime occurredAt = now();

        TransactionExceptionLog exception =
                TransactionExceptionLog.open(
                        "EX-" + fixtureToken,
                        transaction,
                        null,
                        TransactionProcessingStage.SETTLEMENT,
                        "DATABASE_TIMEOUT",
                        "Temporary settlement failure",
                        true,
                        "CORR-" + fixtureToken,
                        occurredAt);

        exception.scheduleRetry(
                1,
                occurredAt,
                occurredAt.plusSeconds(5));
        exception.scheduleRetry(
                2,
                occurredAt.plusSeconds(5),
                occurredAt.plusSeconds(35));
        exception.scheduleRetry(
                3,
                occurredAt.plusSeconds(35),
                occurredAt.plusSeconds(95));
        exception.moveToManualReview(
                3,
                occurredAt.plusSeconds(95));

        TransactionExceptionLog saved =
                transactionExceptionDao.saveAndFlush(exception);

        TransactionExceptionLog locked = transactionExceptionDao
                .findByIdForUpdate(
                        saved.getTransactionExceptionId())
                .orElseThrow();

        assertThat(locked.getStatus())
                .isEqualTo(
                        TransactionExceptionStatus.MANUAL_REVIEW);
        assertThat(locked.getRetryCount()).isEqualTo(3);
        assertThat(locked.getNextRetryAt()).isNull();
        assertThat(transactionExceptionDao
                .findAllByTransaction_TransactionIdOrderByCreatedAtAsc(
                        transaction.getTransactionId()))
                .extracting(
                        TransactionExceptionLog::getExceptionReference)
                .containsExactly("EX-" + fixtureToken);
    }

    private SettlementPostingPair buildPair() {
        TransactionDb transaction = releasedTransaction();
        Account clearingAccount = accountDao
                .findById(clearingAccountId)
                .orElseThrow();

        return settlementPostingFactory().create(
                transaction,
                clearingAccount,
                now());
    }

    private TransactionDb releasedTransaction() {
        TransactionResponse created =
                transactionService.createTransaction(
                        customerId,
                        new CreateTransactionRequest(
                                sourceAccountId,
                                beneficiaryId,
                                new BigDecimal("250.00"),
                                "Ledger integration payment",
                                "LEDGER-" + fixtureToken));

        TransactionResponse released =
                transactionService.authorizeTransaction(
                        customerId,
                        Long.valueOf(created.transactionId()),
                        new AuthorizeTransactionRequest(true));

        return transactionDao.findByIdForUpdate(
                Long.valueOf(released.transactionId()))
                .orElseThrow();
    }

    private void persistPendingPair(SettlementPostingPair pair) {
        ledgerPostingDao.saveAndFlush(pair.posting());
        ledgerEntryDao.saveAll(pair.entriesInPostingOrder());
        entityManager.flush();
        entityManager.clear();
    }

    private SettlementPostingFactory settlementPostingFactory() {
        SettlementProcessorProperties properties =
                new SettlementProcessorProperties(
                        true,
                        Duration.ofSeconds(1),
                        25,
                        clearingAccountId,
                        List.of(
                                Duration.ofSeconds(5),
                                Duration.ofSeconds(30),
                                Duration.ofMinutes(1)));

        return new SettlementPostingFactory(properties);
    }

    private static OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private Connection ownerConnection() throws SQLException {
        return DriverManager.getConnection(
                ownerUrl,
                ownerUsername,
                ownerPassword);
    }

    private static Long nextSequenceValue(
            Connection connection,
            String qualifiedSequenceName) throws SQLException {

        String sql = "SELECT "
                + qualifiedSequenceName
                + ".NEXTVAL FROM DUAL";

        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {

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
            statement.setString(2, "Ledger Integration Customer");
            statement.setString(
                    3,
                    "ledger-" + fixtureToken + "@example.invalid");
            statement.setString(
                    4,
                    "integration-test-password-hash");
            statement.executeUpdate();
        }
    }

    private void insertCustomerAccount(Connection connection)
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
            statement.setString(3, "LS" + fixtureToken);
            statement.setString(4, "SafePay Ledger Test Bank");
            statement.setBigDecimal(
                    5,
                    new BigDecimal("100000.00"));
            statement.executeUpdate();
        }
    }

    private void insertClearingAccount(Connection connection)
            throws SQLException {

        try (PreparedStatement statement = connection.prepareStatement(
                """
                INSERT INTO SAFEPAY_OWNER.ACCOUNT
                    (ACCOUNT_ID, OWNER_USER_ID, ACCOUNT_NUMBER,
                     ACCOUNT_TYPE, BANK_NAME, IFSC_CODE,
                     CURRENT_BALANCE)
                VALUES (?, NULL, ?, 'OUTBOUND_CLEARING', ?, NULL, 0)
                """)) {

            statement.setLong(1, clearingAccountId);
            statement.setString(2, "LC" + fixtureToken);
            statement.setString(3, "SafePay Outbound Clearing");
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
            statement.setString(3, "Ledger Beneficiary");
            statement.setString(
                    4,
                    "ledger." + fixtureToken + "@safepay");
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
