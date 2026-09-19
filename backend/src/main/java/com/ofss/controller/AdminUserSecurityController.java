package com.ofss.controller;

import java.util.Objects;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ofss.beans.RoleName;
import com.ofss.beans.UserStatus;
import com.ofss.common.CorrelationIdFilter;
import com.ofss.common.api.PagedResponse;
import com.ofss.dto.admin.AdminUserSecurityResponse;
import com.ofss.dto.admin.UpdateUserStatusRequest;
import com.ofss.security.AuthenticatedUser;
import com.ofss.services.AdminUserSecurityService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasAuthority('SYSTEM_ADMIN')")
public class AdminUserSecurityController {

    private final AdminUserSecurityService service;

    public AdminUserSecurityController(
            AdminUserSecurityService service) {
        this.service = Objects.requireNonNull(
                service,
                "service is required");
    }

    @GetMapping
    public ResponseEntity<PagedResponse<AdminUserSecurityResponse>> searchUsers(
            Authentication authentication,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) RoleName role,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(service.searchUsers(AuthenticatedUser.userId(authentication),
                q, role, status, page, size));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<AdminUserSecurityResponse> getUserDetails(
            Authentication authentication, @PathVariable("userId") Long userId) {
        return ResponseEntity.ok(service.getUserDetails(
                AuthenticatedUser.userId(authentication), userId));
    }

    @PatchMapping("/{userId}/status")
    public ResponseEntity<AdminUserSecurityResponse> updateStatus(
            Authentication authentication,
            @PathVariable("userId") Long userId,
            @Valid @RequestBody UpdateUserStatusRequest request,
            HttpServletRequest servletRequest) {
        return ResponseEntity.ok(service.updateStatus(
                AuthenticatedUser.userId(authentication),
                userId,
                request.status(),
                correlationId(servletRequest)));
    }

    @PutMapping("/{userId}/roles/{roleCode}")
    public ResponseEntity<AdminUserSecurityResponse> assignRole(
            Authentication authentication,
            @PathVariable("userId") Long userId,
            @PathVariable("roleCode") RoleName roleCode,
            HttpServletRequest servletRequest) {
        return ResponseEntity.ok(service.assignRole(
                AuthenticatedUser.userId(authentication),
                userId,
                roleCode,
                correlationId(servletRequest)));
    }

    @DeleteMapping("/{userId}/roles/{roleCode}")
    public ResponseEntity<AdminUserSecurityResponse> removeRole(
            Authentication authentication,
            @PathVariable("userId") Long userId,
            @PathVariable("roleCode") RoleName roleCode,
            HttpServletRequest servletRequest) {
        return ResponseEntity.ok(service.removeRole(
                AuthenticatedUser.userId(authentication),
                userId,
                roleCode,
                correlationId(servletRequest)));
    }

    @PostMapping("/{userId}/sessions/revoke")
    public ResponseEntity<AdminUserSecurityResponse> revokeSessions(
            Authentication authentication,
            @PathVariable("userId") Long userId,
            HttpServletRequest servletRequest) {
        return ResponseEntity.ok(service.revokeSessions(
                AuthenticatedUser.userId(authentication),
                userId,
                correlationId(servletRequest)));
    }

    private static String correlationId(HttpServletRequest request) {
        Object value = request.getAttribute(
                CorrelationIdFilter.REQUEST_ATTRIBUTE);
        if (value instanceof String correlationId
                && !correlationId.isBlank()) {
            return correlationId;
        }
        throw new IllegalStateException(
                "Request correlation ID is unavailable");
    }
}
