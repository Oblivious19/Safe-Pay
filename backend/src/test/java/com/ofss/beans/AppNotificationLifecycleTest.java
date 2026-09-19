package com.ofss.beans;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AppNotificationLifecycleTest {

    private static final OffsetDateTime CREATED =
            OffsetDateTime.parse("2026-09-17T06:00:00Z");

    @Mock private User customer;
    @Mock private TransactionDb transaction;

    private AppNotification notification;

    @BeforeEach
    void setUp() {
        when(customer.getUserId()).thenReturn(7L);
        when(transaction.getTransactionId()).thenReturn(101L);
        when(transaction.getCustomer()).thenReturn(customer);
        notification = AppNotification.pendingLifecycle(
                "NOTIFY-101",
                customer,
                transaction,
                NotificationType.PAYMENT_PROTECTED,
                NotificationSeverity.WARNING,
                "Payment protected",
                "Your payment is in a protection window.",
                "PAYMENT_PROTECTED:101",
                "CORR-101",
                CREATED);
    }

    @Test
    void marksUnreadNotificationExactlyOnce() {
        OffsetDateTime firstRead = CREATED.plusSeconds(2);
        notification.markRead(firstRead);
        notification.markRead(firstRead.plusMinutes(1));

        assertThat(notification.isRead()).isTrue();
        assertThat(notification.getReadAt()).isEqualTo(firstRead);
        assertThat(notification.getUpdatedAt()).isEqualTo(firstRead);
    }

    @Test
    void successfulFirstAttemptBecomesTerminalDelivered() {
        OffsetDateTime deliveredAt = CREATED.plusSeconds(1);
        notification.recordDeliverySuccess(deliveredAt);

        assertThat(notification.getDeliveryStatus())
                .isEqualTo(NotificationDeliveryStatus.DELIVERED);
        assertThat(notification.getAttemptCount()).isEqualTo(1);
        assertThat(notification.getDeliveredAt()).isEqualTo(deliveredAt);
        assertThat(notification.getNextAttemptAt()).isNull();
        assertThat(notification.getLastErrorCode()).isNull();
    }

    @Test
    void failedAttemptUsesApprovedRetryDeadlineAndUppercaseCode() {
        notification.recordDeliveryFailure(
                " broker_unavailable ",
                CREATED.plusSeconds(1),
                Duration.ofSeconds(5));

        assertThat(notification.getDeliveryStatus())
                .isEqualTo(NotificationDeliveryStatus.RETRY_PENDING);
        assertThat(notification.getAttemptCount()).isEqualTo(1);
        assertThat(notification.getNextAttemptAt())
                .isEqualTo(CREATED.plusSeconds(6));
        assertThat(notification.getLastErrorCode())
                .isEqualTo("BROKER_UNAVAILABLE");
    }

    @Test
    void fifthFailureExhaustsDeliveryWithoutAnotherRetry() {
        Duration[] delays = {
                Duration.ofSeconds(5),
                Duration.ofSeconds(30),
                Duration.ofMinutes(1),
                Duration.ofMinutes(5)
        };
        for (int attempt = 1; attempt <= 4; attempt++) {
            notification.recordDeliveryFailure(
                    "BROKER_UNAVAILABLE",
                    CREATED.plusMinutes(attempt),
                    delays[attempt - 1]);
        }
        OffsetDateTime failedAt = CREATED.plusMinutes(10);
        notification.recordDeliveryFailure(
                "BROKER_UNAVAILABLE",
                failedAt,
                null);

        assertThat(notification.getDeliveryStatus())
                .isEqualTo(NotificationDeliveryStatus.FAILED);
        assertThat(notification.getAttemptCount()).isEqualTo(5);
        assertThat(notification.getFailedAt()).isEqualTo(failedAt);
        assertThat(notification.getNextAttemptAt()).isNull();
    }

    @Test
    void dueCheckHonorsRetryTimestamp() {
        notification.recordDeliveryFailure(
                "BROKER_UNAVAILABLE",
                CREATED.plusSeconds(1),
                Duration.ofSeconds(5));

        assertThat(notification.isDueForDelivery(
                CREATED.plusSeconds(5))).isFalse();
        assertThat(notification.isDueForDelivery(
                CREATED.plusSeconds(6))).isTrue();
    }

    @Test
    void terminalDeliveryCannotBeReopenedOrRetried() {
        notification.recordDeliverySuccess(CREATED.plusSeconds(1));

        assertThatThrownBy(() -> notification.recordDeliveryFailure(
                "LATE_FAILURE",
                CREATED.plusSeconds(2),
                Duration.ofSeconds(5)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("notification delivery is already terminal");
    }

    @Test
    void lifecycleTimestampCannotPrecedeCreation() {
        assertThatThrownBy(() ->
                notification.markRead(CREATED.minusNanos(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("databaseTime cannot be before createdAt");
    }
}
