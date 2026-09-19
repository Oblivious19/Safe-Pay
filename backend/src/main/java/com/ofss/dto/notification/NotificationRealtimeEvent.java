package com.ofss.dto.notification;

import java.time.OffsetDateTime;
import java.util.Objects;

import com.ofss.beans.AppNotification;
import com.ofss.beans.NotificationSeverity;
import com.ofss.beans.NotificationType;

public record NotificationRealtimeEvent(
        String eventType,
        String notificationId,
        String notificationReference,
        NotificationType notificationType,
        NotificationSeverity severity,
        String transactionId,
        OffsetDateTime occurredAt) {

    public static NotificationRealtimeEvent from(
            AppNotification notification) {
        Objects.requireNonNull(
                notification,
                "notification is required");
        return new NotificationRealtimeEvent(
                "NOTIFICATION_AVAILABLE",
                notification.getNotificationId().toString(),
                notification.getNotificationReference(),
                notification.getNotificationType(),
                notification.getSeverity(),
                notification.getTransaction() == null
                        ? null
                        : notification.getTransaction()
                                .getTransactionId()
                                .toString(),
                notification.getCreatedAt());
    }
}
