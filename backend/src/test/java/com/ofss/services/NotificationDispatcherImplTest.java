package com.ofss.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import com.ofss.beans.NotificationDeliveryStatus;
import com.ofss.repository.AppNotificationDao;
import com.ofss.scheduler.NotificationDispatcherProperties;

@ExtendWith(MockitoExtension.class)
class NotificationDispatcherImplTest {

    private static final OffsetDateTime NOW =
            OffsetDateTime.parse("2026-09-17T07:00:00Z");

    @Mock private AppNotificationDao notificationDao;
    @Mock private NotificationDispatchWorker worker;

    private NotificationDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new NotificationDispatcherImpl(
                notificationDao,
                worker,
                properties());
    }

    @Test
    void dispatchesOldestDueIdsWithinRequestedBatch() {
        when(notificationDao.currentDatabaseTime()).thenReturn(NOW);
        when(notificationDao.findDueNotificationIds(
                eq(NotificationDeliveryStatus.PENDING),
                eq(NotificationDeliveryStatus.RETRY_PENDING),
                eq(NOW),
                any(Pageable.class)))
                .thenReturn(List.of(11L, 12L));

        assertThat(dispatcher.dispatchDueNotifications(10)).isEqualTo(2);
        verify(worker).dispatch(11L);
        verify(worker).dispatch(12L);
    }

    @Test
    void oneWorkerFailureDoesNotPreventLaterNotification() {
        when(notificationDao.currentDatabaseTime()).thenReturn(NOW);
        when(notificationDao.findDueNotificationIds(
                any(), any(), eq(NOW), any(Pageable.class)))
                .thenReturn(List.of(11L, 12L));
        when(worker.dispatch(11L))
                .thenThrow(new IllegalStateException("fixture"));

        assertThat(dispatcher.dispatchDueNotifications(10)).isEqualTo(2);
        verify(worker).dispatch(12L);
    }

    @Test
    void emptyDueSetProducesNoAttempts() {
        when(notificationDao.currentDatabaseTime()).thenReturn(NOW);
        when(notificationDao.findDueNotificationIds(
                any(), any(), eq(NOW), any(Pageable.class)))
                .thenReturn(List.of());

        assertThat(dispatcher.dispatchDueNotifications(25)).isZero();
    }

    @Test
    void rejectsBatchAboveConfiguredLimit() {
        assertThatThrownBy(() ->
                dispatcher.dispatchDueNotifications(26))
                .isInstanceOf(IllegalArgumentException.class);
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
