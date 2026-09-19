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
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.test.context.transaction.BeforeTransaction;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import com.ofss.beans.Account;
import com.ofss.beans.AccountStatus;
import com.ofss.beans.AuditOutcome;
import com.ofss.beans.LedgerEntry;
import com.ofss.beans.LedgerEntryType;
import com.ofss.beans.LedgerPostingStatus;
import com.ofss.beans.NotificationDeliveryStatus;
import com.ofss.beans.TransactionDb;
import com.ofss.beans.TransactionExceptionLog;
import com.ofss.beans.TransactionProcessingStage;
import com.ofss.beans.TransactionState;
import com.ofss.dto.transaction.AuthorizeTransactionRequest;
import com.ofss.dto.transaction.CreateTransactionRequest;
import com.ofss.dto.transaction.TransactionResponse;
import com.ofss.repository.AccountDao;
import com.ofss.repository.AppNotificationDao;
import com.ofss.repository.AuditLogDao;
import com.ofss.repository.LedgerEntryDao;
import com.ofss.repository.LedgerPostingDao;
import com.ofss.repository.TransactionDao;
import com.ofss.repository.TransactionExceptionDao;
import com.ofss.scheduler.SettlementProcessorProperties;

import jakarta.persistence.EntityManager;

@SpringBootTest(properties = "safepay.settlement-processor.enabled=false")
@Transactional
class SettlementServiceOracleIntegrationTest {

    private static final BigDecimal PAYMENT_AMOUNT = new BigDecimal("250.00");

    @Autowired private TransactionService transactionService;
    @Autowired private TransactionDao transactionDao;
    @Autowired private AccountDao accountDao;
    @Autowired private LedgerPostingDao postingDao;
    @Autowired private LedgerEntryDao entryDao;
    @Autowired private AuditLogDao auditDao;
    @Autowired private AppNotificationDao notificationDao;
    @Autowired private TransactionExceptionDao exceptionDao;
    @Autowired private SettlementEvidenceService evidenceService;
    @Autowired private TransactionStateService stateService;
    @Autowired private EntityManager entityManager;
    @Autowired private com.ofss.repository.ReportingReadRepository reporting;

    @Test
    void statisticsFollowLatestSettlementExceptionAndIgnoreOtherStages() {
        long baseline = reporting.statistics().dueSettlementPayments();
        TransactionDb payment = releasedPayment("REPORT-DUE");
        entityManager.flush();
        assertThat(reporting.statistics().dueSettlementPayments()).isEqualTo(baseline + 1);
        OffsetDateTime now = transactionDao.currentDatabaseTime();
        var past = now.minusMinutes(5);
        var first = TransactionExceptionLog.open("REPORT-FIRST-" + payment.getTransactionId(), payment, null,
                TransactionProcessingStage.SETTLEMENT, "TEMPORARY_FAILURE", "Private technical details",
                true, "REPORT-" + fixtureToken, past);
        first.scheduleRetry(1, past, past.plusSeconds(5));
        exceptionDao.saveAndFlush(first);
        assertThat(reporting.statistics().dueSettlementPayments()).isEqualTo(baseline + 1);

        var latest = TransactionExceptionLog.open("REPORT-LATEST-" + payment.getTransactionId(), payment, null,
                TransactionProcessingStage.SETTLEMENT, "TEMPORARY_FAILURE", "Private technical details",
                true, "REPORT-" + fixtureToken, past.plusMinutes(1));
        latest.scheduleRetry(1, past.plusMinutes(1), now.plusHours(1));
        exceptionDao.saveAndFlush(latest);
        assertThat(reporting.statistics().dueSettlementPayments()).isEqualTo(baseline);

        var unrelated = TransactionExceptionLog.open("REPORT-OTHER-" + payment.getTransactionId(), payment, null,
                TransactionProcessingStage.AUTHORIZATION, "TEMPORARY_FAILURE", "Private details",
                true, "REPORT-" + fixtureToken, past.plusMinutes(2));
        exceptionDao.saveAndFlush(unrelated);
        assertThat(reporting.statistics().dueSettlementPayments()).isEqualTo(baseline);

        latest.scheduleRetry(2, now.minusMinutes(1), now.minusSeconds(30));
        exceptionDao.saveAndFlush(latest);
        assertThat(reporting.statistics().dueSettlementPayments()).isEqualTo(baseline + 1);
        latest.moveToManualReview(3, now);
        exceptionDao.saveAndFlush(latest);
        assertThat(reporting.statistics().dueSettlementPayments()).isEqualTo(baseline);
    }

    @Test
    void failureUnionPaginatesFixtureSourcesAndUsesTerminalNotificationTimestamp() {
        TransactionDb payment = releasedPayment("REPORT-FAILURE");
        OffsetDateTime now = transactionDao.currentDatabaseTime();
        var first = now.minusMinutes(5);
        var exception = TransactionExceptionLog.open("REPORT-FAIL-" + payment.getTransactionId(), payment, null,
                TransactionProcessingStage.SETTLEMENT, "TEMPORARY_FAILURE", "Never expose this raw error",
                true, "REPORT-" + fixtureToken, first);
        exceptionDao.saveAndFlush(exception);
        var notification = com.ofss.beans.AppNotification.pendingLifecycle(
                "REPORT-NOTIFY-" + payment.getTransactionId(), payment.getCustomer(), payment,
                com.ofss.beans.NotificationType.PAYMENT_FAILED, com.ofss.beans.NotificationSeverity.WARNING,
                "Private title", "Private body", "REPORT-NOTIFY-" + payment.getTransactionId(),
                "REPORT-" + fixtureToken, first);
        notification.recordDeliveryFailure("DISPATCH_FAILURE", first.plusSeconds(1), Duration.ofSeconds(5));
        notificationDao.save(notification);
        entityManager.flush();
        Long id = payment.getTransactionId();
        var page = reporting.failures(null, id, first, now.plusMinutes(1), 0, 1);
        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.items().getFirst().source()).isEqualTo(com.ofss.dto.admin.OperationalFailureResponse.Source.NOTIFICATION);
        assertThat(page.items().getFirst().occurredAt()).isEqualTo(first.plusSeconds(1));
        assertThat(reporting.failures(null, id, first, now.plusMinutes(1), 1, 1).items().getFirst().source())
                .isEqualTo(com.ofss.dto.admin.OperationalFailureResponse.Source.TRANSACTION);
        assertThat(reporting.failures(null, id, first, first.plusSeconds(1), 0, 20).totalElements()).isEqualTo(1);
        for (int attempt = 2; attempt <= 5; attempt++) {
            notification.recordDeliveryFailure("DISPATCH_FAILURE", first.plusSeconds(attempt * 10L), Duration.ofSeconds(5));
        }
        entityManager.flush();
        var terminal = reporting.failures(com.ofss.dto.admin.OperationalFailureResponse.Source.NOTIFICATION,
                id, null, null, 0, 20).items().getFirst();
        assertThat(terminal.status()).isEqualTo("FAILED");
        assertThat(terminal.retryable()).isFalse();
        assertThat(terminal.occurredAt()).isEqualTo(first.plusSeconds(50));
        assertThat(terminal.nextAttemptAt()).isNull();
    }

    @Value("${spring.flyway.url}") private String ownerUrl;
    @Value("${spring.flyway.user}") private String ownerUsername;
    @Value("${spring.flyway.password}") private String ownerPassword;

    private Long customerId;
    private Long sourceAccountId;
    private Long clearingAccountId;
    private Long beneficiaryId;
    private String fixtureToken;

    @BeforeTransaction
    void createOwnerFixtures() throws SQLException {
        fixtureToken = UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        try (Connection connection = ownerConnection()) {
            connection.setAutoCommit(false);
            try {
                customerId = nextSequenceValue(connection, "SAFEPAY_OWNER.SEQ_APP_USER_ID");
                sourceAccountId = nextSequenceValue(connection, "SAFEPAY_OWNER.SEQ_ACCOUNT_ID");
                clearingAccountId = nextSequenceValue(connection, "SAFEPAY_OWNER.SEQ_ACCOUNT_ID");
                beneficiaryId = nextSequenceValue(connection, "SAFEPAY_OWNER.SEQ_BENEFICIARY_ID");
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
                executeDelete(connection, "DELETE FROM SAFEPAY_OWNER.BENEFICIARY WHERE BENEFICIARY_ID = ?", beneficiaryId);
                executeDelete(connection, "DELETE FROM SAFEPAY_OWNER.ACCOUNT WHERE ACCOUNT_ID = ?", sourceAccountId);
                executeDelete(connection, "DELETE FROM SAFEPAY_OWNER.ACCOUNT WHERE ACCOUNT_ID = ?", clearingAccountId);
                executeDelete(connection, "DELETE FROM SAFEPAY_OWNER.APP_USER WHERE USER_ID = ?", customerId);
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    @Test
    void settlesReleasedPaymentWithBalancesLedgerAndAtomicEvidence() {
        TransactionDb payment = releasedPayment("SUCCESS");
        BigDecimal sourceBefore = accountDao.findById(sourceAccountId).orElseThrow().getCurrentBalance();

        assertThat(settlementService().settleIfReleased(payment.getTransactionId(), "CORR-" + fixtureToken))
                .isEqualTo(SettlementAttemptOutcome.SETTLED);
        entityManager.flush();
        entityManager.clear();

        TransactionDb settled = transactionDao.findByIdForUpdate(payment.getTransactionId()).orElseThrow();
        Account source = accountDao.findById(sourceAccountId).orElseThrow();
        Account clearing = accountDao.findById(clearingAccountId).orElseThrow();
        assertThat(settled.getState()).isEqualTo(TransactionState.SETTLED);
        assertThat(settled.getReservedAmount()).isEqualByComparingTo("0.00");
        assertThat(source.getCurrentBalance()).isEqualByComparingTo(sourceBefore.subtract(PAYMENT_AMOUNT));
        assertThat(source.getReservedAmount()).isEqualByComparingTo("0.00");
        assertThat(clearing.getCurrentBalance()).isEqualByComparingTo(PAYMENT_AMOUNT);
        assertThat(postingDao.findByTransaction_TransactionId(payment.getTransactionId()).orElseThrow().getStatus())
                .isEqualTo(LedgerPostingStatus.POSTED);
        assertThat(auditDao.findAllByTransaction_TransactionIdOrderByOccurredAtAsc(payment.getTransactionId()))
                .extracting(
                        com.ofss.beans.AuditLog::getActionCode,
                        com.ofss.beans.AuditLog::getOutcome)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("PAYMENT_CREATED", AuditOutcome.SUCCESS),
                        org.assertj.core.groups.Tuple.tuple("PAYMENT_RELEASED", AuditOutcome.SUCCESS),
                        org.assertj.core.groups.Tuple.tuple("PAYMENT_SETTLED", AuditOutcome.SUCCESS));
        assertThat(notificationDao.findAllByTransaction_TransactionIdOrderByCreatedAtAsc(payment.getTransactionId()))
                .extracting(
                        com.ofss.beans.AppNotification::getNotificationType,
                        com.ofss.beans.AppNotification::getDeliveryStatus)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(
                                com.ofss.beans.NotificationType.PAYMENT_RELEASED,
                                NotificationDeliveryStatus.PENDING),
                        org.assertj.core.groups.Tuple.tuple(
                                com.ofss.beans.NotificationType.PAYMENT_SETTLED,
                                NotificationDeliveryStatus.PENDING));
    }

    @Test
    void duplicateSettlementIsReadOnlyAndDoesNotDuplicateAnyEffect() {
        TransactionDb payment = releasedPayment("DUPLICATE");
        SettlementService service = settlementService();

        assertThat(service.settleIfReleased(payment.getTransactionId(), "CORR-" + fixtureToken))
                .isEqualTo(SettlementAttemptOutcome.SETTLED);
        assertThat(service.settleIfReleased(payment.getTransactionId(), "CORR-" + fixtureToken))
                .isEqualTo(SettlementAttemptOutcome.ALREADY_SETTLED);
        entityManager.flush();

        assertThat(entryDao.findAllByTransaction_TransactionIdOrderByLineNumberAsc(payment.getTransactionId()))
                .hasSize(2);
        assertThat(auditDao.findAllByTransaction_TransactionIdOrderByOccurredAtAsc(payment.getTransactionId()))
                .hasSize(3);
        assertThat(notificationDao.findAllByTransaction_TransactionIdOrderByCreatedAtAsc(payment.getTransactionId()))
                .hasSize(2);
    }

    @Test
    void invalidClearingAccountStopsBeforeAnyFinancialOrLedgerMutation() {
        TransactionDb payment = releasedPayment("INVALID-CLEARING");
        Account source = accountDao.findById(sourceAccountId).orElseThrow();
        Account clearing = accountDao.findById(clearingAccountId).orElseThrow();
        BigDecimal currentBefore = source.getCurrentBalance();
        ReflectionTestUtils.setField(clearing, "status", AccountStatus.INACTIVE);

        assertThatThrownBy(() -> settlementService().settleIfReleased(
                payment.getTransactionId(), "CORR-" + fixtureToken))
                .isInstanceOf(SettlementInvariantException.class)
                .hasMessageContaining("not an active ownerless INR");
        assertThat(source.getCurrentBalance()).isEqualByComparingTo(currentBefore);
        assertThat(payment.getState()).isEqualTo(TransactionState.RELEASED);
        assertThat(postingDao.findByTransaction_TransactionId(payment.getTransactionId())).isEmpty();
    }

    @Test
    void ledgerReconstructsEqualDebitAndCreditTotalsAcrossDifferentAccounts() {
        TransactionDb payment = releasedPayment("RECONSTRUCT");
        settlementService().settleIfReleased(payment.getTransactionId(), "CORR-" + fixtureToken);
        entityManager.flush();

        List<LedgerEntry> entries = entryDao
                .findAllByTransaction_TransactionIdOrderByLineNumberAsc(payment.getTransactionId());
        BigDecimal debit = entries.stream()
                .filter(entry -> entry.getEntryType() == LedgerEntryType.DEBIT)
                .map(LedgerEntry::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal credit = entries.stream()
                .filter(entry -> entry.getEntryType() == LedgerEntryType.CREDIT)
                .map(LedgerEntry::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(entries).hasSize(2);
        assertThat(entries).extracting(entry -> entry.getAccount().getAccountId())
                .containsExactly(sourceAccountId, clearingAccountId);
        assertThat(debit).isEqualByComparingTo(credit).isEqualByComparingTo(PAYMENT_AMOUNT);
    }

    @Test
    void dueQueryHonorsPersistedRetryDeadline() {
        TransactionDb payment = releasedPayment("RETRY-DUE");
        OffsetDateTime databaseTime = transactionDao.currentDatabaseTime();
        OffsetDateTime originalFailure = databaseTime.minusMinutes(2);
        TransactionExceptionLog exception = TransactionExceptionLog.open(
                "SETTLEMENT-EXCEPTION-" + payment.getTransactionId(),
                payment,
                null,
                TransactionProcessingStage.SETTLEMENT,
                "TEMPORARY_FAILURE",
                "Temporary failure",
                true,
                "SETTLEMENT-" + payment.getTransactionId(),
                originalFailure);
        exception.scheduleRetry(1, originalFailure, originalFailure.plusSeconds(5));
        exceptionDao.saveAndFlush(exception);

        assertThat(transactionDao.findDueSettlementTransactionIds(25))
                .contains(payment.getTransactionId());

        exception.scheduleRetry(2, databaseTime, databaseTime.plusSeconds(30));
        exceptionDao.saveAndFlush(exception);
        assertThat(transactionDao.findDueSettlementTransactionIds(25))
                .doesNotContain(payment.getTransactionId());
    }

    private SettlementService settlementService() {
        SettlementProcessorProperties properties = new SettlementProcessorProperties(
                true,
                Duration.ofSeconds(1),
                25,
                clearingAccountId,
                List.of(Duration.ofSeconds(5), Duration.ofSeconds(30), Duration.ofMinutes(1)));
        return new SettlementServiceImpl(
                transactionDao,
                accountDao,
                postingDao,
                entryDao,
                new SettlementPostingFactory(properties),
                evidenceService,
                stateService,
                properties,
                entityManager);
    }

    private TransactionDb releasedPayment(String suffix) {
        TransactionResponse created = transactionService.createTransaction(
                customerId,
                new CreateTransactionRequest(
                        sourceAccountId,
                        beneficiaryId,
                        PAYMENT_AMOUNT,
                        "Settlement integration payment",
                        suffix + "-" + fixtureToken));
        TransactionResponse released = transactionService.authorizeTransaction(
                customerId,
                Long.valueOf(created.transactionId()),
                new AuthorizeTransactionRequest(true));
        assertThat(released.state()).isEqualTo(TransactionState.RELEASED);
        return transactionDao.findByIdForUpdate(Long.valueOf(released.transactionId())).orElseThrow();
    }

    private Connection ownerConnection() throws SQLException {
        return DriverManager.getConnection(ownerUrl, ownerUsername, ownerPassword);
    }

    private static Long nextSequenceValue(Connection connection, String sequence) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT " + sequence + ".NEXTVAL FROM DUAL")) {
            if (!resultSet.next()) throw new SQLException("Sequence returned no value");
            return resultSet.getLong(1);
        }
    }

    private void insertUser(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO SAFEPAY_OWNER.APP_USER (USER_ID,FULL_NAME,EMAIL,PASSWORD_HASH) VALUES (?,?,?,?)")) {
            statement.setLong(1, customerId);
            statement.setString(2, "Settlement Integration Customer");
            statement.setString(3, "settlement-" + fixtureToken + "@example.invalid");
            statement.setString(4, "integration-test-password-hash");
            statement.executeUpdate();
        }
    }

    private void insertCustomerAccount(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                """
                INSERT INTO SAFEPAY_OWNER.ACCOUNT
                    (ACCOUNT_ID,OWNER_USER_ID,ACCOUNT_NUMBER,ACCOUNT_TYPE,BANK_NAME,IFSC_CODE,CURRENT_BALANCE)
                VALUES (?, ?, ?, 'SAVINGS', ?, 'HDFC0000001', 100000)
                """)) {
            statement.setLong(1, sourceAccountId);
            statement.setLong(2, customerId);
            statement.setString(3, "SS" + fixtureToken);
            statement.setString(4, "SafePay Settlement Test Bank");
            statement.executeUpdate();
        }
    }

    private void insertClearingAccount(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                """
                INSERT INTO SAFEPAY_OWNER.ACCOUNT
                    (ACCOUNT_ID,OWNER_USER_ID,ACCOUNT_NUMBER,ACCOUNT_TYPE,BANK_NAME,IFSC_CODE,CURRENT_BALANCE)
                VALUES (?, NULL, ?, 'OUTBOUND_CLEARING', ?, NULL, 0)
                """)) {
            statement.setLong(1, clearingAccountId);
            statement.setString(2, "SC" + fixtureToken);
            statement.setString(3, "SafePay Outbound Clearing");
            statement.executeUpdate();
        }
    }

    private void insertBeneficiary(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                """
                INSERT INTO SAFEPAY_OWNER.BENEFICIARY
                    (BENEFICIARY_ID,OWNER_USER_ID,BENEFICIARY_NAME,PAYMENT_METHOD,UPI_ID)
                VALUES (?, ?, ?, 'UPI', ?)
                """)) {
            statement.setLong(1, beneficiaryId);
            statement.setLong(2, customerId);
            statement.setString(3, "Settlement Beneficiary");
            statement.setString(4, "settlement." + fixtureToken + "@safepay");
            statement.executeUpdate();
        }
    }

    private static void executeDelete(Connection connection, String sql, Long id) throws SQLException {
        if (id == null) return;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            statement.executeUpdate();
        }
    }
}
