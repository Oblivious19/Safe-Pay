package com.ofss.dto.notification;

import java.time.OffsetDateTime;

import com.ofss.beans.AppNotification;
import com.ofss.beans.NotificationSeverity;
import com.ofss.beans.NotificationType;

public record NotificationResponse(
        String notificationId,
        String notificationReference,
        String transactionId,
        NotificationType type,
        NotificationSeverity severity,
        String title,
        String message,
        boolean read,
        OffsetDateTime readAt,
        OffsetDateTime createdAt) {

    public static NotificationResponse from(AppNotification notification) {
        java.util.Objects.requireNonNull(
                notification,
                "notification is required");
        return new NotificationResponse(
                notification.getNotificationId().toString(),
                notification.getNotificationReference(),
                notification.getTransaction() == null
                        ? null
                        : notification.getTransaction()
                                .getTransactionId()
                                .toString(),
                notification.getNotificationType(),
                notification.getSeverity(),
                notification.getTitle(),
                notification.getMessage(),
                notification.isRead(),
                notification.getReadAt(),
                notification.getCreatedAt());
    }
}
