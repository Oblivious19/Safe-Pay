package com.ofss.services;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import com.ofss.beans.NotificationType;
import com.ofss.beans.RoleName;
import com.ofss.beans.UserStatus;
import com.ofss.dto.notification.NotificationRealtimeEvent;
import com.ofss.repository.UserRoleDao;

@Service
public class StompNotificationEventPublisher
        implements NotificationEventPublisher {

    public static final String NOTIFICATION_DESTINATION =
            "/queue/notifications";
    public static final String TRANSACTION_DESTINATION =
            "/queue/transactions";
    public static final String RISK_REVIEW_DESTINATION =
            "/queue/risk-reviews";

    private static final Set<NotificationType> REVIEW_TYPES = EnumSet.of(
            NotificationType.RISK_REVIEW_PENDING,
            NotificationType.RISK_REVIEW_APPROVED,
            NotificationType.RISK_REVIEW_REJECTED);

    private final SimpMessagingTemplate messagingTemplate;
    private final UserRoleDao userRoleDao;

    public StompNotificationEventPublisher(
            SimpMessagingTemplate messagingTemplate,
            UserRoleDao userRoleDao) {
        this.messagingTemplate = Objects.requireNonNull(
                messagingTemplate,
                "messagingTemplate is required");
        this.userRoleDao = Objects.requireNonNull(
                userRoleDao,
                "userRoleDao is required");
    }

    @Override
    public void publish(
            String recipientPrincipalName,
            NotificationRealtimeEvent event) {
        String principalName = requirePrincipalName(
                recipientPrincipalName);
        NotificationRealtimeEvent safeEvent = Objects.requireNonNull(
                event,
                "event is required");

        messagingTemplate.convertAndSendToUser(
                principalName,
                NOTIFICATION_DESTINATION,
                safeEvent);

        if (safeEvent.transactionId() != null) {
            messagingTemplate.convertAndSendToUser(
                    principalName,
                    TRANSACTION_DESTINATION,
                    safeEvent);
        }

        if (REVIEW_TYPES.contains(safeEvent.notificationType())) {
            userRoleDao.findPrincipalNamesByRoleAndStatus(
                            RoleName.RISK_OFFICER,
                            UserStatus.ACTIVE)
                    .stream()
                    .map(String::trim)
                    .filter(name -> !name.isEmpty())
                    .distinct()
                    .forEach(officerPrincipal ->
                            messagingTemplate.convertAndSendToUser(
                                    officerPrincipal,
                                    RISK_REVIEW_DESTINATION,
                                    safeEvent));
        }
    }

    private static String requirePrincipalName(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "recipientPrincipalName is required");
        }
        String normalized = value.trim();
        if (normalized.length() > 320) {
            throw new IllegalArgumentException(
                    "recipientPrincipalName has an invalid length");
        }
        return normalized;
    }
}
