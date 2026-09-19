package com.ofss.services;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

import org.springframework.data.domain.PageRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ofss.beans.NotificationDeliveryStatus;
import com.ofss.repository.AppNotificationDao;
import com.ofss.scheduler.NotificationDispatcherProperties;

@Service
@Transactional(readOnly = true)
public class NotificationDispatcherImpl implements NotificationDispatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            NotificationDispatcherImpl.class);

    private final AppNotificationDao notificationDao;
    private final NotificationDispatchWorker worker;
    private final NotificationDispatcherProperties properties;

    public NotificationDispatcherImpl(
            AppNotificationDao notificationDao,
            NotificationDispatchWorker worker,
            NotificationDispatcherProperties properties) {
        this.notificationDao = Objects.requireNonNull(
                notificationDao,
                "notificationDao is required");
        this.worker = Objects.requireNonNull(
                worker,
                "worker is required");
        this.properties = Objects.requireNonNull(
                properties,
                "properties is required");
    }

    @Override
    public int dispatchDueNotifications(int maximumBatchSize) {
        if (maximumBatchSize < 1
                || maximumBatchSize > properties.batchSize()) {
            throw new IllegalArgumentException(
                    "maximumBatchSize must be between 1 and configured batchSize");
        }

        OffsetDateTime databaseTime = Objects.requireNonNull(
                notificationDao.currentDatabaseTime(),
                "database time is required");
        List<Long> dueIds = notificationDao.findDueNotificationIds(
                NotificationDeliveryStatus.PENDING,
                NotificationDeliveryStatus.RETRY_PENDING,
                databaseTime,
                PageRequest.of(0, maximumBatchSize));

        int attempted = 0;
        for (Long notificationId : dueIds) {
            try {
                worker.dispatch(notificationId);
            } catch (RuntimeException dispatchFailure) {
                LOGGER.error(
                        "Notification dispatch transaction failed for notificationId={}",
                        notificationId,
                        dispatchFailure);
            }
            attempted++;
        }
        return attempted;
    }
}
