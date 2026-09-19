package com.ofss.beans;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

@Entity
@Table(
        name = "APP_NOTIFICATION",
        schema = "SAFEPAY_OWNER",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "UK_APP_NOTIFICATION_REF",
                        columnNames = "NOTIFICATION_REFERENCE"),
                @UniqueConstraint(
                        name = "UK_APP_NOTIFICATION_DEDUP",
                        columnNames = {"RECIPIENT_USER_ID", "DEDUPLICATION_KEY"})
        })
@SequenceGenerator(
        name = "appNotificationSequence",
        sequenceName = "SAFEPAY_OWNER.SEQ_APP_NOTIFICATION_ID",
        allocationSize = 1)
public class AppNotification {

    public static final int DEFAULT_MAX_ATTEMPTS = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "appNotificationSequence")
    @Column(name = "NOTIFICATION_ID", nullable = false, updatable = false)
    private Long notificationId;

    @Column(name = "NOTIFICATION_REFERENCE", nullable = false, updatable = false, length = 64)
    private String notificationReference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "RECIPIENT_USER_ID", nullable = false, updatable = false)
    private User recipient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "TRANSACTION_ID", updatable = false)
    private TransactionDb transaction;

    @Enumerated(EnumType.STRING)
    @Column(name = "NOTIFICATION_TYPE", nullable = false, updatable = false, length = 40)
    private NotificationType notificationType;

    @Enumerated(EnumType.STRING)
    @Column(name = "SEVERITY", nullable = false, updatable = false, length = 20)
    private NotificationSeverity severity;

    @Column(name = "TITLE", nullable = false, updatable = false, length = 200)
    private String title;

    @Column(name = "MESSAGE", nullable = false, updatable = false, length = 1000)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "DELIVERY_CHANNEL", nullable = false, updatable = false, length = 20)
    private NotificationDeliveryChannel deliveryChannel;

    @Enumerated(EnumType.STRING)
    @Column(name = "DELIVERY_STATUS", nullable = false, length = 20)
    private NotificationDeliveryStatus deliveryStatus;

    @Column(name = "DEDUPLICATION_KEY", nullable = false, updatable = false, length = 160)
    private String deduplicationKey;

    @Column(name = "ATTEMPT_COUNT", nullable = false, precision = 10, scale = 0)
    private int attemptCount;

    @Column(name = "MAX_ATTEMPTS", nullable = false, updatable = false, precision = 10, scale = 0)
    private int maxAttempts;

    @Column(name = "NEXT_ATTEMPT_AT") private OffsetDateTime nextAttemptAt;
    @Column(name = "DELIVERED_AT") private OffsetDateTime deliveredAt;
    @Column(name = "FAILED_AT") private OffsetDateTime failedAt;
    @Column(name = "READ_AT") private OffsetDateTime readAt;
    @Column(name = "LAST_ERROR_CODE", length = 100) private String lastErrorCode;

    @Column(name = "CORRELATION_ID", nullable = false, updatable = false, length = 64)
    private String correlationId;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "UPDATED_AT", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(name = "VERSION_NO", nullable = false, precision = 10, scale = 0)
    private long versionNo;

    protected AppNotification() {
        // Required by JPA.
    }

    public static AppNotification pendingSettlement(
            String notificationReference,
            User recipient,
            TransactionDb transaction,
            String deduplicationKey,
            String correlationId,
            OffsetDateTime createdAt) {

        return pendingLifecycle(
                notificationReference,
                recipient,
                transaction,
                NotificationType.PAYMENT_SETTLED,
                NotificationSeverity.INFO,
                "Payment settled",
                "Your payment completed SafePay's simulated settlement process.",
                deduplicationKey,
                correlationId,
                createdAt);
    }

    public static AppNotification pendingLifecycle(
            String notificationReference,
            User recipient,
            TransactionDb transaction,
            NotificationType notificationType,
            NotificationSeverity severity,
            String title,
            String message,
            String deduplicationKey,
            String correlationId,
            OffsetDateTime createdAt) {

        requirePersistedUser(recipient);
        requirePersistedTransaction(transaction);
        if (!Objects.equals(
                recipient.getUserId(),
                transaction.getCustomer().getUserId())) {
            throw new IllegalArgumentException(
                    "recipient must own transaction");
        }

        AppNotification notification = new AppNotification();
        notification.notificationReference = requireText(
                notificationReference,
                "notificationReference",
                64);
        notification.recipient = recipient;
        notification.transaction = transaction;
        notification.notificationType = Objects.requireNonNull(
                notificationType,
                "notificationType is required");
        notification.severity = Objects.requireNonNull(
                severity,
                "severity is required");
        notification.title = requireText(title, "title", 200);
        notification.message = requireText(message, "message", 1000);
        notification.deliveryChannel = NotificationDeliveryChannel.IN_APP;
        notification.deliveryStatus = NotificationDeliveryStatus.PENDING;
        notification.deduplicationKey = requireText(
                deduplicationKey,
                "deduplicationKey",
                160);
        notification.attemptCount = 0;
        notification.maxAttempts = DEFAULT_MAX_ATTEMPTS;
        notification.correlationId = requireText(
                correlationId,
                "correlationId",
                64);
        notification.createdAt = requireUtcTimestamp(
                createdAt,
                "createdAt");
        notification.updatedAt = notification.createdAt;
        return notification;
    }

    public boolean isRead() {
        return readAt != null;
    }

    public boolean isDueForDelivery(OffsetDateTime databaseTime) {
        OffsetDateTime now = requireLifecycleTimestamp(
                databaseTime,
                "databaseTime");
        if (deliveryStatus == NotificationDeliveryStatus.PENDING) {
            return true;
        }
        return deliveryStatus == NotificationDeliveryStatus.RETRY_PENDING
                && nextAttemptAt != null
                && !nextAttemptAt.isAfter(now);
    }

    public void markRead(OffsetDateTime databaseTime) {
        if (readAt != null) {
            return;
        }
        OffsetDateTime occurredAt = requireLifecycleTimestamp(
                databaseTime,
                "databaseTime");
        readAt = occurredAt;
        updatedAt = occurredAt;
    }

    public void recordDeliverySuccess(OffsetDateTime databaseTime) {
        requireDispatchable();
        OffsetDateTime occurredAt = requireLifecycleTimestamp(
                databaseTime,
                "databaseTime");
        attemptCount++;
        deliveryStatus = NotificationDeliveryStatus.DELIVERED;
        nextAttemptAt = null;
        deliveredAt = occurredAt;
        failedAt = null;
        lastErrorCode = null;
        updatedAt = occurredAt;
    }

    public void recordDeliveryFailure(
            String errorCode,
            OffsetDateTime databaseTime,
            Duration retryDelay) {
        requireDispatchable();
        OffsetDateTime occurredAt = requireLifecycleTimestamp(
                databaseTime,
                "databaseTime");
        String normalizedError = requireText(
                errorCode,
                "errorCode",
                100).toUpperCase(java.util.Locale.ROOT);

        attemptCount++;
        deliveredAt = null;
        lastErrorCode = normalizedError;
        updatedAt = occurredAt;

        if (attemptCount == maxAttempts) {
            deliveryStatus = NotificationDeliveryStatus.FAILED;
            nextAttemptAt = null;
            failedAt = occurredAt;
            return;
        }

        Duration delay = Objects.requireNonNull(
                retryDelay,
                "retryDelay is required");
        if (delay.isZero() || delay.isNegative()) {
            throw new IllegalArgumentException(
                    "retryDelay must be positive");
        }
        deliveryStatus = NotificationDeliveryStatus.RETRY_PENDING;
        nextAttemptAt = occurredAt.plus(delay);
        failedAt = null;
    }

    private void requireDispatchable() {
        if (deliveryStatus != NotificationDeliveryStatus.PENDING
                && deliveryStatus
                        != NotificationDeliveryStatus.RETRY_PENDING) {
            throw new IllegalStateException(
                    "notification delivery is already terminal");
        }
        if (attemptCount >= maxAttempts) {
            throw new IllegalStateException(
                    "notification delivery attempts are exhausted");
        }
    }

    private OffsetDateTime requireLifecycleTimestamp(
            OffsetDateTime value,
            String fieldName) {
        OffsetDateTime normalized = requireUtcTimestamp(
                value,
                fieldName);
        if (createdAt != null && normalized.isBefore(createdAt)) {
            throw new IllegalArgumentException(
                    fieldName + " cannot be before createdAt");
        }
        return normalized;
    }

    private static void requirePersistedUser(User user) {
        Objects.requireNonNull(user, "recipient is required");
        if (user.getUserId() == null || user.getUserId() <= 0L) {
            throw new IllegalArgumentException("recipient must already be persisted");
        }
    }

    private static void requirePersistedTransaction(TransactionDb transaction) {
        Objects.requireNonNull(transaction, "transaction is required");
        if (transaction.getTransactionId() == null || transaction.getTransactionId() <= 0L) {
            throw new IllegalArgumentException("transaction must already be persisted");
        }
    }

    private static String requireText(String value, String fieldName, int maximumLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        String normalized = value.trim();
        if (normalized.length() > maximumLength) {
            throw new IllegalArgumentException(fieldName + " has an invalid length");
        }
        return normalized;
    }

    private static OffsetDateTime requireUtcTimestamp(OffsetDateTime value, String fieldName) {
        return Objects.requireNonNull(value, fieldName + " is required")
                .withOffsetSameInstant(ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.MICROS);
    }

    public Long getNotificationId() { return notificationId; }
    public String getNotificationReference() { return notificationReference; }
    public User getRecipient() { return recipient; }
    public TransactionDb getTransaction() { return transaction; }
    public NotificationType getNotificationType() { return notificationType; }
    public NotificationSeverity getSeverity() { return severity; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public NotificationDeliveryChannel getDeliveryChannel() { return deliveryChannel; }
    public NotificationDeliveryStatus getDeliveryStatus() { return deliveryStatus; }
    public String getDeduplicationKey() { return deduplicationKey; }
    public int getAttemptCount() { return attemptCount; }
    public int getMaxAttempts() { return maxAttempts; }
    public OffsetDateTime getNextAttemptAt() { return nextAttemptAt; }
    public OffsetDateTime getDeliveredAt() { return deliveredAt; }
    public OffsetDateTime getFailedAt() { return failedAt; }
    public OffsetDateTime getReadAt() { return readAt; }
    public String getLastErrorCode() { return lastErrorCode; }
    public String getCorrelationId() { return correlationId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public long getVersionNo() { return versionNo; }
}
