package com.ofss.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.ofss.beans.AppNotification;
import com.ofss.beans.NotificationDeliveryStatus;
import com.ofss.beans.NotificationSeverity;
import com.ofss.beans.NotificationType;
import com.ofss.beans.TransactionDb;
import com.ofss.beans.User;
import com.ofss.dto.notification.NotificationRealtimeEvent;
import com.ofss.repository.AppNotificationDao;
import com.ofss.scheduler.NotificationDispatcherProperties;

import jakarta.persistence.EntityManager;

@ExtendWith(MockitoExtension.class)
class NotificationDispatchWorkerImplTest {

    private static final OffsetDateTime CREATED =
            OffsetDateTime.parse("2026-09-17T07:00:00Z");

    @Mock private AppNotificationDao notificationDao;
    @Mock private NotificationEventPublisher eventPublisher;
    @Mock private EntityManager entityManager;
    @Mock private User customer;
    @Mock private TransactionDb transaction;

    private NotificationDispatchWorker worker;
    private AppNotification notification;

    @BeforeEach
    void setUp() {
        worker = new NotificationDispatchWorkerImpl(
                notificationDao,
                eventPublisher,
                properties(),
                entityManager);
        org.mockito.Mockito.lenient()
                .when(customer.getUserId()).thenReturn(7L);
        org.mockito.Mockito.lenient()
                .when(customer.getEmail())
                .thenReturn("customer@example.com");
        org.mockito.Mockito.lenient()
                .when(transaction.getTransactionId()).thenReturn(101L);
        org.mockito.Mockito.lenient()
                .when(transaction.getCustomer()).thenReturn(customer);
        notification = AppNotification.pendingLifecycle(
                "NOTIFY-501", customer, transaction,
                NotificationType.PAYMENT_RELEASED,
                NotificationSeverity.INFO,
                "Payment released",
                "Your payment was released.",
                "DEDUP-501", "CORR-501", CREATED);
        ReflectionTestUtils.setField(
                notification,
                "notificationId",
                501L);
    }

    @Test
    void successfulPublishMarksDeliveredAtomically() {
        dueAt(CREATED.plusSeconds(1));

        assertThat(worker.dispatch(501L))
                .isEqualTo(NotificationDispatchOutcome.DELIVERED);
        assertThat(notification.getDeliveryStatus())
                .isEqualTo(NotificationDeliveryStatus.DELIVERED);
        assertThat(notification.getAttemptCount()).isEqualTo(1);
        verify(eventPublisher).publish(
                org.mockito.ArgumentMatchers.eq("customer@example.com"),
                any(NotificationRealtimeEvent.class));
        verify(entityManager).flush();
    }

    @Test
    void firstPublishFailureSchedulesFiveSecondRetry() {
        OffsetDateTime attemptedAt = CREATED.plusSeconds(1);
        dueAt(attemptedAt);
        org.mockito.Mockito.doThrow(new IllegalStateException("broker"))
                .when(eventPublisher)
                .publish(any(), any());

        assertThat(worker.dispatch(501L))
                .isEqualTo(NotificationDispatchOutcome.RETRY_SCHEDULED);
        assertThat(notification.getAttemptCount()).isEqualTo(1);
        assertThat(notification.getNextAttemptAt())
                .isEqualTo(attemptedAt.plusSeconds(5));
        assertThat(notification.getLastErrorCode())
                .isEqualTo("ILLEGAL_STATE_EXCEPTION");
    }

    @Test
    void fifthPublishFailureBecomesTerminalFailed() {
        for (int attempt = 1; attempt <= 4; attempt++) {
            notification.recordDeliveryFailure(
                    "BROKER_UNAVAILABLE",
                    CREATED.plusMinutes(attempt),
                    properties().retryDelayAfterFailure(attempt));
        }
        OffsetDateTime fifthAttempt = CREATED.plusMinutes(10);
        dueAt(fifthAttempt);
        org.mockito.Mockito.doThrow(new RuntimeException("broker"))
                .when(eventPublisher)
                .publish(any(), any());

        assertThat(worker.dispatch(501L))
                .isEqualTo(NotificationDispatchOutcome.FAILED);
        assertThat(notification.getAttemptCount()).isEqualTo(5);
        assertThat(notification.getDeliveryStatus())
                .isEqualTo(NotificationDeliveryStatus.FAILED);
        assertThat(notification.getNextAttemptAt()).isNull();
    }

    @Test
    void retryBeforeDeadlineIsNotPublished() {
        notification.recordDeliveryFailure(
                "BROKER_UNAVAILABLE",
                CREATED.plusSeconds(1),
                Duration.ofSeconds(5));
        dueAt(CREATED.plusSeconds(5));

        assertThat(worker.dispatch(501L))
                .isEqualTo(NotificationDispatchOutcome.NOT_DUE);
        verify(eventPublisher, never()).publish(any(), any());
    }

    @Test
    void terminalNotificationIsNotRepublished() {
        notification.recordDeliverySuccess(CREATED.plusSeconds(1));
        when(notificationDao.findByIdForDispatch(501L))
                .thenReturn(Optional.of(notification));

        assertThat(worker.dispatch(501L))
                .isEqualTo(NotificationDispatchOutcome.TERMINAL);
        verify(eventPublisher, never()).publish(any(), any());
        verify(notificationDao, never()).currentDatabaseTime();
    }

    @Test
    void missingNotificationIsSafeNoOp() {
        when(notificationDao.findByIdForDispatch(999L))
                .thenReturn(Optional.empty());

        assertThat(worker.dispatch(999L))
                .isEqualTo(NotificationDispatchOutcome.NOT_FOUND);
        verify(eventPublisher, never()).publish(any(), any());
    }

    @Test
    void rejectsInvalidNotificationIdBeforeRepositoryAccess() {
        assertThatThrownBy(() -> worker.dispatch(0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("notificationId must be positive");
        verify(notificationDao, never()).findByIdForDispatch(any());
    }

    @Test
    void realtimeEnvelopeContainsOnlyApprovedSafeFields() {
        NotificationRealtimeEvent event =
                NotificationRealtimeEvent.from(notification);

        assertThat(event.notificationReference()).isEqualTo("NOTIFY-501");
        assertThat(event.transactionId()).isEqualTo("101");
        assertThat(NotificationRealtimeEvent.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .doesNotContain(
                        "amount",
                        "accountNumber",
                        "otp",
                        "detailsJson",
                        "internalNote",
                        "message");
    }

    private void dueAt(OffsetDateTime databaseTime) {
        when(notificationDao.findByIdForDispatch(501L))
                .thenReturn(Optional.of(notification));
        when(notificationDao.currentDatabaseTime())
                .thenReturn(databaseTime);
    }

    private static NotificationDispatcherProperties properties() {
        return new NotificationDispatcherProperties(
                true,
                Duration.ofSeconds(1),
                25,
                5,
                List.of(
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(30),
                        Duration.ofMinutes(1),
                        Duration.ofMinutes(5)));
    }
}
