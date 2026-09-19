package com.ofss.services;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.ofss.beans.NotificationSeverity;
import com.ofss.beans.NotificationType;
import com.ofss.beans.RoleName;
import com.ofss.beans.UserStatus;
import com.ofss.dto.notification.NotificationRealtimeEvent;
import com.ofss.repository.UserRoleDao;

@ExtendWith(MockitoExtension.class)
class StompNotificationEventPublisherTest {

    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private UserRoleDao userRoleDao;

    private StompNotificationEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new StompNotificationEventPublisher(
                messagingTemplate,
                userRoleDao);
    }

    @Test
    void everyEventUsesPrivateNotificationDestination() {
        NotificationRealtimeEvent event = event(
                NotificationType.PAYMENT_CANCELLED,
                "101");

        publisher.publish(" customer@example.com ", event);

        verify(messagingTemplate).convertAndSendToUser(
                "customer@example.com",
                "/queue/notifications",
                event);
    }

    @Test
    void transactionEventAlsoRefreshesPrivateTransactionQueue() {
        NotificationRealtimeEvent event = event(
                NotificationType.PAYMENT_SETTLED,
                "101");

        publisher.publish("customer@example.com", event);

        verify(messagingTemplate).convertAndSendToUser(
                "customer@example.com",
                "/queue/transactions",
                event);
    }

    @Test
    void reviewEventAlsoRefreshesPrivateRiskReviewQueue() {
        NotificationRealtimeEvent event = event(
                NotificationType.RISK_REVIEW_PENDING,
                "101");
        org.mockito.Mockito.when(
                userRoleDao.findPrincipalNamesByRoleAndStatus(
                        RoleName.RISK_OFFICER,
                        UserStatus.ACTIVE))
                .thenReturn(java.util.List.of(
                        "officer.one@example.com",
                        "officer.two@example.com"));

        publisher.publish("customer@example.com", event);

        verify(messagingTemplate).convertAndSendToUser(
                "officer.one@example.com",
                "/queue/risk-reviews",
                event);
        verify(messagingTemplate).convertAndSendToUser(
                "officer.two@example.com",
                "/queue/risk-reviews",
                event);
    }

    @Test
    void nonReviewEventNeverTouchesReviewQueue() {
        NotificationRealtimeEvent event = event(
                NotificationType.OTP_VERIFIED,
                "101");

        publisher.publish("customer@example.com", event);

        verify(messagingTemplate, never()).convertAndSendToUser(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq("/queue/risk-reviews"),
                org.mockito.ArgumentMatchers.any());
        verify(userRoleDao, never())
                .findPrincipalNamesByRoleAndStatus(anyRole(), anyStatus());
    }

    @Test
    void rejectsBlankPrincipalAndNullEvent() {
        assertThatThrownBy(() -> publisher.publish(" ", event(
                NotificationType.PAYMENT_SETTLED, "101")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                publisher.publish("customer@example.com", null))
                .isInstanceOf(NullPointerException.class);
    }

    private static NotificationRealtimeEvent event(
            NotificationType type,
            String transactionId) {
        return new NotificationRealtimeEvent(
                "NOTIFICATION_AVAILABLE",
                "501",
                "NOTIFY-501",
                type,
                NotificationSeverity.INFO,
                transactionId,
                OffsetDateTime.parse("2026-09-17T07:00:00Z"));
    }

    private static RoleName anyRole() {
        return org.mockito.ArgumentMatchers.any(RoleName.class);
    }

    private static UserStatus anyStatus() {
        return org.mockito.ArgumentMatchers.any(UserStatus.class);
    }
}
