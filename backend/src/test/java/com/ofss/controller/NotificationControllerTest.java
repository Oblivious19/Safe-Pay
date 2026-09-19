package com.ofss.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ofss.beans.NotificationSeverity;
import com.ofss.beans.NotificationType;
import com.ofss.beans.UserStatus;
import com.ofss.common.api.PagedResponse;
import com.ofss.dto.notification.NotificationResponse;
import com.ofss.excp.GlobalExceptionHandler;
import com.ofss.security.SafePayPrincipal;
import com.ofss.services.NotificationService;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    private static final Long USER_ID = 7L;
    private static final OffsetDateTime NOW =
            OffsetDateTime.parse("2026-09-17T07:00:00Z");

    @Mock private NotificationService notificationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(
                        new NotificationController(notificationService))
                .setControllerAdvice(new GlobalExceptionHandler(
                        Clock.fixed(NOW.toInstant(), ZoneOffset.UTC)))
                .build();
    }

    @Test
    void listsAuthenticatedUsersNotificationsWithSafeDefaults()
            throws Exception {
        when(notificationService.listOwnedNotifications(
                USER_ID, 0, 20)).thenReturn(page(false));

        mockMvc.perform(authenticated(
                        get("/api/v1/notifications")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].notificationReference")
                        .value("NOTIFY-501"))
                .andExpect(jsonPath("$.items[0].deduplicationKey")
                        .doesNotExist())
                .andExpect(jsonPath("$.items[0].correlationId")
                        .doesNotExist());
    }

    @Test
    void forwardsBoundedPaginationToOwnedQuery() throws Exception {
        when(notificationService.listOwnedNotifications(
                USER_ID, 2, 10))
                .thenReturn(new PagedResponse<>(
                        List.of(), 2, 10, 21, 3, false, true));

        mockMvc.perform(authenticated(
                        get("/api/v1/notifications")
                                .param("page", "2")
                                .param("size", "10")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2));
    }

    @Test
    void marksOnlyAuthenticatedUsersNotificationRead()
            throws Exception {
        when(notificationService.markOwnedNotificationRead(
                USER_ID, 501L)).thenReturn(response(true));

        mockMvc.perform(authenticated(
                        patch("/api/v1/notifications/501/read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notificationId").value("501"))
                .andExpect(jsonPath("$.read").value(true));

        verify(notificationService)
                .markOwnedNotificationRead(USER_ID, 501L);
    }

    @Test
    void missingAuthenticationIsForbiddenAfterSecurityCompletion()
            throws Exception {
        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode")
                        .value("ACCESS_DENIED"));
        verifyNoInteractions(notificationService);
    }

    @Test
    void malformedNotificationIdNeverReachesService()
            throws Exception {
        mockMvc.perform(authenticated(
                        patch("/api/v1/notifications/not-a-number/read")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(notificationService);
    }

    private static MockHttpServletRequestBuilder authenticated(
            MockHttpServletRequestBuilder request) {
        SafePayPrincipal principal = new SafePayPrincipal(
                USER_ID,
                "customer@example.com",
                "stored-password-hash",
                UserStatus.ACTIVE,
                0L,
                List.of(new SimpleGrantedAuthority("CUSTOMER")));
        return request.principal(new TestingAuthenticationToken(
                principal,
                null,
                principal.getAuthorities()));
    }

    private static PagedResponse<NotificationResponse> page(
            boolean read) {
        return new PagedResponse<>(
                List.of(response(read)),
                0, 20, 1, 1, true, true);
    }

    private static NotificationResponse response(boolean read) {
        return new NotificationResponse(
                "501",
                "NOTIFY-501",
                "101",
                NotificationType.PAYMENT_PROTECTED,
                NotificationSeverity.WARNING,
                "Payment protected",
                "Your payment is protected.",
                read,
                read ? NOW : null,
                NOW.minusSeconds(1));
    }
}
