package com.ofss.controller;

import java.util.Objects;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ofss.common.api.PagedResponse;
import com.ofss.dto.notification.NotificationResponse;
import com.ofss.security.AuthenticatedUser;
import com.ofss.services.NotificationService;

@RestController
@RequestMapping("/api/v1/notifications")
@PreAuthorize("hasAuthority('CUSTOMER')")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = Objects.requireNonNull(
                notificationService,
                "notificationService is required");
    }

    @GetMapping
    public ResponseEntity<PagedResponse<NotificationResponse>> list(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(
                notificationService.listOwnedNotifications(
                        authenticatedUserId(authentication),
                        page,
                        size));
    }

    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<NotificationResponse> markRead(
            Authentication authentication,
            @PathVariable("notificationId") Long notificationId) {
        return ResponseEntity.ok(
                notificationService.markOwnedNotificationRead(
                        authenticatedUserId(authentication),
                        notificationId));
    }

    private static Long authenticatedUserId(
            Authentication authentication) {
        return AuthenticatedUser.userId(authentication);
    }
}
