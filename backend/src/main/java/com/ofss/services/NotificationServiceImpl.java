package com.ofss.services;

import java.time.OffsetDateTime;
import java.util.Objects;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ofss.beans.AppNotification;
import com.ofss.common.api.PagedResponse;
import com.ofss.dto.notification.NotificationResponse;
import com.ofss.excp.ResourceNotFoundExcp;
import com.ofss.repository.AppNotificationDao;

@Service
@Transactional(readOnly = true)
public class NotificationServiceImpl implements NotificationService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final Sort NEWEST_FIRST = Sort.by(
            Sort.Order.desc("createdAt"),
            Sort.Order.desc("notificationId"));

    private final AppNotificationDao notificationDao;

    public NotificationServiceImpl(AppNotificationDao notificationDao) {
        this.notificationDao = Objects.requireNonNull(
                notificationDao,
                "notificationDao is required");
    }

    @Override
    public PagedResponse<NotificationResponse> listOwnedNotifications(
            Long recipientUserId,
            int page,
            int size) {
        requirePositiveId(recipientUserId, "recipientUserId");
        requirePage(page, size);

        return PagedResponse.from(
                notificationDao.findAllByRecipient_UserId(
                        recipientUserId,
                        PageRequest.of(page, size, NEWEST_FIRST)),
                NotificationResponse::from);
    }

    @Override
    @Transactional
    public NotificationResponse markOwnedNotificationRead(
            Long recipientUserId,
            Long notificationId) {
        requirePositiveId(recipientUserId, "recipientUserId");
        requirePositiveId(notificationId, "notificationId");

        AppNotification notification = notificationDao
                .findOwnedByIdForUpdate(
                        notificationId,
                        recipientUserId)
                .orElseThrow(
                        NotificationServiceImpl::notificationNotFound);

        if (!notification.isRead()) {
            OffsetDateTime databaseTime = Objects.requireNonNull(
                    notificationDao.currentDatabaseTime(),
                    "database time is required");
            notification.markRead(databaseTime);
            notificationDao.save(notification);
        }
        return NotificationResponse.from(notification);
    }

    private static void requirePage(int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException(
                    "page must be zero or greater");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException(
                    "size must be between 1 and " + MAX_PAGE_SIZE);
        }
    }

    private static void requirePositiveId(Long value, String fieldName) {
        if (value == null || value <= 0L) {
            throw new IllegalArgumentException(
                    fieldName + " must be positive");
        }
    }

    private static ResourceNotFoundExcp notificationNotFound() {
        return new ResourceNotFoundExcp(
                "NOTIFICATION_NOT_FOUND",
                "Notification was not found");
    }
}
