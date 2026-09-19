package com.ofss.scheduler;

import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ofss.services.NotificationDispatcher;

@ExtendWith(MockitoExtension.class)
class NotificationDispatcherSchedulerTest {

    @Mock private NotificationDispatcher dispatcher;

    @Test
    void delegatesOneApprovedBatchPerPoll() {
        NotificationDispatcherScheduler scheduler =
                new NotificationDispatcherScheduler(
                        dispatcher,
                        properties(25));

        scheduler.dispatchDueNotifications();

        verify(dispatcher).dispatchDueNotifications(25);
    }

    @Test
    void honorsConfiguredSmallerBatch() {
        NotificationDispatcherScheduler scheduler =
                new NotificationDispatcherScheduler(
                        dispatcher,
                        properties(7));

        scheduler.dispatchDueNotifications();

        verify(dispatcher).dispatchDueNotifications(7);
    }

    private static NotificationDispatcherProperties properties(int batch) {
        return new NotificationDispatcherProperties(
                true,
                Duration.ofSeconds(1),
                batch,
                5,
                List.of(
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(30),
                        Duration.ofMinutes(1),
                        Duration.ofMinutes(5)));
    }
}
