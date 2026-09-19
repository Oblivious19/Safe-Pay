package com.ofss.services;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ofss.beans.AppNotification;
import com.ofss.beans.NotificationDeliveryStatus;
import com.ofss.dto.notification.NotificationRealtimeEvent;
import com.ofss.repository.AppNotificationDao;
import com.ofss.scheduler.NotificationDispatcherProperties;

import jakarta.persistence.EntityManager;

@Service
public class NotificationDispatchWorkerImpl
        implements NotificationDispatchWorker {

    private static final String DEFAULT_ERROR_CODE =
            "REALTIME_PUBLISH_FAILED";

    private final AppNotificationDao notificationDao;
    private final NotificationEventPublisher eventPublisher;
    private final NotificationDispatcherProperties properties;
    private final EntityManager entityManager;

    public NotificationDispatchWorkerImpl(
            AppNotificationDao notificationDao,
            NotificationEventPublisher eventPublisher,
            NotificationDispatcherProperties properties,
            EntityManager entityManager) {
        this.notificationDao = Objects.requireNonNull(
                notificationDao,
                "notificationDao is required");
        this.eventPublisher = Objects.requireNonNull(
                eventPublisher,
                "eventPublisher is required");
        this.properties = Objects.requireNonNull(
                properties,
                "properties is required");
        this.entityManager = Objects.requireNonNull(
                entityManager,
                "entityManager is required");
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public NotificationDispatchOutcome dispatch(Long notificationId) {
        requirePositiveId(notificationId);

        AppNotification notification = notificationDao
                .findByIdForDispatch(notificationId)
                .orElse(null);
        if (notification == null) {
            return NotificationDispatchOutcome.NOT_FOUND;
        }
        if (notification.getDeliveryStatus()
                    == NotificationDeliveryStatus.DELIVERED
                || notification.getDeliveryStatus()
                    == NotificationDeliveryStatus.FAILED) {
            return NotificationDispatchOutcome.TERMINAL;
        }

        OffsetDateTime databaseTime = Objects.requireNonNull(
                notificationDao.currentDatabaseTime(),
                "database time is required");
        if (!notification.isDueForDelivery(databaseTime)) {
            return NotificationDispatchOutcome.NOT_DUE;
        }

        try {
            eventPublisher.publish(
                    notification.getRecipient().getEmail(),
                    NotificationRealtimeEvent.from(notification));
        } catch (RuntimeException publishFailure) {
            int failedAttemptCount = notification.getAttemptCount() + 1;
            Duration retryDelay = failedAttemptCount
                    < properties.maxAttempts()
                            ? properties.retryDelayAfterFailure(
                                    failedAttemptCount)
                            : null;
            notification.recordDeliveryFailure(
                    safeErrorCode(publishFailure),
                    databaseTime,
                    retryDelay);
            notificationDao.save(notification);
            entityManager.flush();
            return notification.getDeliveryStatus()
                    == NotificationDeliveryStatus.FAILED
                            ? NotificationDispatchOutcome.FAILED
                            : NotificationDispatchOutcome.RETRY_SCHEDULED;
        }

        notification.recordDeliverySuccess(databaseTime);
        notificationDao.save(notification);
        entityManager.flush();
        return NotificationDispatchOutcome.DELIVERED;
    }

    private static String safeErrorCode(RuntimeException failure) {
        String simpleName = failure.getClass().getSimpleName();
        if (simpleName == null || simpleName.isBlank()) {
            return DEFAULT_ERROR_CODE;
        }
        String normalized = simpleName
                .replaceAll("([a-z])([A-Z])", "$1_$2")
                .replaceAll("[^A-Za-z0-9_]", "_")
                .toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return DEFAULT_ERROR_CODE;
        }
        return normalized.length() <= 100
                ? normalized
                : normalized.substring(0, 100);
    }

    private static void requirePositiveId(Long notificationId) {
        if (notificationId == null || notificationId <= 0L) {
            throw new IllegalArgumentException(
                    "notificationId must be positive");
        }
    }
}
