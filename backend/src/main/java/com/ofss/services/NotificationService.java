package com.ofss.services;

import com.ofss.common.api.PagedResponse;
import com.ofss.dto.notification.NotificationResponse;

public interface NotificationService {

    PagedResponse<NotificationResponse> listOwnedNotifications(
            Long recipientUserId,
            int page,
            int size);

    NotificationResponse markOwnedNotificationRead(
            Long recipientUserId,
            Long notificationId);
}
