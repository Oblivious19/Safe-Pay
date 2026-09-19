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
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.test.context.transaction.BeforeTransaction;
import org.springframework.transaction.annotation.Transactional;

import com.ofss.beans.AppNotification;
import com.ofss.beans.NotificationDeliveryStatus;
import com.ofss.beans.NotificationSeverity;
import com.ofss.beans.NotificationType;
import com.ofss.beans.TransactionDb;
import com.ofss.beans.User;
import com.ofss.dto.notification.NotificationResponse;
import com.ofss.dto.transaction.CreateTransactionRequest;
import com.ofss.dto.transaction.TransactionResponse;
import com.ofss.excp.ResourceNotFoundExcp;
import com.ofss.repository.AppNotificationDao;
import com.ofss.repository.TransactionDao;
import com.ofss.repository.UserDao;

import jakarta.persistence.EntityManager;

@SpringBootTest(
        properties = "safepay.notification-dispatcher.enabled=false")
@Transactional
class NotificationServiceOracleIntegrationTest {

    @Autowired private NotificationService notificationService;
    @Autowired private TransactionService transactionService;
    @Autowired private AppNotificationDao notificationDao;
    @Autowired private TransactionDao transactionDao;
    @Autowired private UserDao userDao;
    @Autowired private EntityManager entityManager;

    @Value("${spring.flyway.url}") private String ownerUrl;
    @Value("${spring.flyway.user}") private String ownerUsername;
    @Value("${spring.flyway.password}") private String ownerPassword;

    private Long customerId;
    private Long otherCustomerId;
    private Long accountId;
    private Long beneficiaryId;
    private String fixtureToken;

    @BeforeTransaction
    void createOwnerFixtures() throws SQLException {
        fixtureToken = UUID.randomUUID()
                .toString().replace("-", "").substring(0, 20);
        try (Connection connection = ownerConnection()) {
            connection.setAutoCommit(false);
            try {
                customerId = nextSequenceValue(
                        connection,
                        "SAFEPAY_OWNER.SEQ_APP_USER_ID");
                otherCustomerId = nextSequenceValue(
                        connection,
                        "SAFEPAY_OWNER.SEQ_APP_USER_ID");
                accountId = nextSequenceValue(
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
                executeDelete(connection,
                        "DELETE FROM SAFEPAY_OWNER.BENEFICIARY WHERE BENEFICIARY_ID = ?",
                        beneficiaryId);
                executeDelete(connection,
                        "DELETE FROM SAFEPAY_OWNER.ACCOUNT WHERE ACCOUNT_ID = ?",
                        accountId);
                executeDelete(connection,
                        "DELETE FROM SAFEPAY_OWNER.APP_USER WHERE USER_ID = ?",
                        customerId);
                executeDelete(connection,
                        "DELETE FROM SAFEPAY_OWNER.APP_USER WHERE USER_ID = ?",
                        otherCustomerId);
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    @Test
    void ownedListingReturnsNotificationAndHidesItFromOtherUser() {
        AppNotification notification = persistNotification("LIST");

        assertThat(notificationService.listOwnedNotifications(
                customerId, 0, 20).items())
                .extracting(NotificationResponse::notificationId)
                .containsExactly(notification.getNotificationId().toString());
        assertThat(notificationService.listOwnedNotifications(
                otherCustomerId, 0, 20).items()).isEmpty();
    }

    @Test
    void markReadPersistsDatabaseTimeAndReplayIsIdempotent() {
        AppNotification notification = persistNotification("READ");

        NotificationResponse first =
                notificationService.markOwnedNotificationRead(
                        customerId,
                        notification.getNotificationId());
        NotificationResponse replay =
                notificationService.markOwnedNotificationRead(
                        customerId,
                        notification.getNotificationId());
        entityManager.flush();

        assertThat(first.read()).isTrue();
        assertThat(replay.readAt()).isEqualTo(first.readAt());
    }

    @Test
    void crossUserMarkReadReturnsSameNotFoundContract() {
        AppNotification notification = persistNotification("CROSS-USER");

        assertThatThrownBy(() ->
                notificationService.markOwnedNotificationRead(
                        otherCustomerId,
                        notification.getNotificationId()))
                .isInstanceOf(ResourceNotFoundExcp.class)
                .extracting("errorCode")
                .isEqualTo("NOTIFICATION_NOT_FOUND");
    }

    @Test
    void dueQueryIncludesPendingAndExcludesFutureRetry() {
        AppNotification pending = persistNotification("PENDING");
        AppNotification retry = persistNotification("RETRY");
        OffsetDateTime databaseTime = notificationDao.currentDatabaseTime();
        retry.recordDeliveryFailure(
                "BROKER_UNAVAILABLE",
                databaseTime,
                Duration.ofMinutes(5));
        notificationDao.save(retry);
        entityManager.flush();

        List<Long> due = notificationDao.findDueNotificationIds(
                NotificationDeliveryStatus.PENDING,
                NotificationDeliveryStatus.RETRY_PENDING,
                databaseTime,
                PageRequest.of(0, 25));

        assertThat(due).contains(pending.getNotificationId());
        assertThat(due).doesNotContain(retry.getNotificationId());
    }

    @Test
    void entityLifecyclePersistsRetryShapeAcceptedByOracleChecks() {
        AppNotification notification = persistNotification("ORACLE-LIFECYCLE");
        OffsetDateTime databaseTime = notificationDao.currentDatabaseTime();
        notification.recordDeliveryFailure(
                "BROKER_UNAVAILABLE",
                databaseTime,
                Duration.ofSeconds(5));
        notificationDao.save(notification);
        entityManager.flush();
        entityManager.clear();

        AppNotification persisted = notificationDao
                .findByIdForDispatch(notification.getNotificationId())
                .orElseThrow();
        assertThat(persisted.getDeliveryStatus())
                .isEqualTo(NotificationDeliveryStatus.RETRY_PENDING);
        assertThat(persisted.getAttemptCount()).isEqualTo(1);
        assertThat(persisted.getNextAttemptAt())
                .isAfter(databaseTime);
    }

    private AppNotification persistNotification(String suffix) {
        TransactionResponse response = transactionService.createTransaction(
                customerId,
                new CreateTransactionRequest(
                        accountId,
                        beneficiaryId,
                        new BigDecimal("100.00"),
                        "Notification integration",
                        "NOTIFY-" + suffix + "-" + fixtureToken),
                OperationContext.internal());
        TransactionDb transaction = transactionDao
                .findOwnedById(
                        Long.valueOf(response.transactionId()),
                        customerId)
                .orElseThrow();
        User customer = userDao.findById(customerId).orElseThrow();
        OffsetDateTime createdAt = notificationDao.currentDatabaseTime();
        AppNotification notification = AppNotification.pendingLifecycle(
                "N-" + suffix + "-" + fixtureToken,
                customer,
                transaction,
                NotificationType.PAYMENT_PROTECTED,
                NotificationSeverity.WARNING,
                "Payment protected",
                "Your payment is protected.",
                "D-" + suffix + "-" + fixtureToken,
                "C-" + suffix + "-" + fixtureToken,
                createdAt);
        notificationDao.save(notification);
        entityManager.flush();
        return notification;
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
            statement.setString(2, "Notification " + label + " user");
            statement.setString(
                    3,
                    "notification-" + label + "-" + fixtureToken
                            + "@example.invalid");
            statement.setString(4, "integration-test-password-hash");
            statement.executeUpdate();
        }
    }

    private void insertAccount(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                """
                INSERT INTO SAFEPAY_OWNER.ACCOUNT
                    (ACCOUNT_ID, OWNER_USER_ID, ACCOUNT_NUMBER,
                     ACCOUNT_TYPE, BANK_NAME, IFSC_CODE,
                     CURRENT_BALANCE)
                VALUES (?, ?, ?, 'SAVINGS', ?, 'HDFC0000001', ?)
                """)) {
            statement.setLong(1, accountId);
            statement.setLong(2, customerId);
            statement.setString(3, "NT" + fixtureToken);
            statement.setString(4, "SafePay Notification Bank");
            statement.setBigDecimal(5, new BigDecimal("500000.00"));
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
            statement.setString(3, "Notification Beneficiary");
            statement.setString(
                    4,
                    "notification." + fixtureToken + "@safepay");
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
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            statement.executeUpdate();
        }
    }
}
