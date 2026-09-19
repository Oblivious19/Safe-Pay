package com.ofss.services;

import com.ofss.dto.notification.NotificationRealtimeEvent;

public interface NotificationEventPublisher {

    void publish(
            String recipientPrincipalName,
            NotificationRealtimeEvent event);
}
