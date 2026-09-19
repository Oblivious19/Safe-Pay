package com.ofss.dto.admin;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

import com.ofss.beans.RoleName;
import com.ofss.beans.User;

public record AdminUserSecurityResponse(
        String userId,
        String fullName,
        String email,
        String mobileNumber,
        String status,
        List<RoleName> roles,
        long securityVersion,
        OffsetDateTime updatedAt) {

    public static AdminUserSecurityResponse from(
            User user,
            Collection<RoleName> roles) {
        java.util.Objects.requireNonNull(user, "user is required");
        List<RoleName> sortedRoles = roles.stream()
                .distinct()
                .sorted()
                .toList();
        return new AdminUserSecurityResponse(
                user.getUserId().toString(),
                user.getFullName(),
                user.getEmail(),
                user.getMobileNumber(),
                user.getStatus().name(),
                sortedRoles,
                user.getSecurityVersion(),
                user.getUpdatedAt());
    }
}
