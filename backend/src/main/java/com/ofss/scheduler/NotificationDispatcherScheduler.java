package com.ofss.scheduler;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ofss.services.NotificationDispatcher;

@Component
@ConditionalOnProperty(
        prefix = "safepay.notification-dispatcher",
        name = "enabled",
        havingValue = "true")
public class NotificationDispatcherScheduler {

    private final NotificationDispatcher dispatcher;
    private final NotificationDispatcherProperties properties;

    public NotificationDispatcherScheduler(
            NotificationDispatcher dispatcher,
            NotificationDispatcherProperties properties) {
        this.dispatcher = java.util.Objects.requireNonNull(
                dispatcher,
                "dispatcher is required");
        this.properties = java.util.Objects.requireNonNull(
                properties,
                "properties is required");
    }

    @Scheduled(
            fixedDelayString =
                    "${safepay.notification-dispatcher.poll-interval}")
    public void dispatchDueNotifications() {
        dispatcher.dispatchDueNotifications(properties.batchSize());
    }
}
