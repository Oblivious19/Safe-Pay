package com.ofss.services;

import com.ofss.beans.RoleName;
import com.ofss.beans.UserStatus;
import com.ofss.dto.admin.AdminUserSecurityResponse;
import com.ofss.common.api.PagedResponse;

public interface AdminUserSecurityService {

    PagedResponse<AdminUserSecurityResponse> searchUsers(
            Long administratorId, String q, RoleName role, UserStatus status,
            int page, int size);

    AdminUserSecurityResponse getUserDetails(Long administratorId, Long targetUserId);

    AdminUserSecurityResponse updateStatus(
            Long administratorId,
            Long targetUserId,
            UserStatus status,
            String correlationId);

    AdminUserSecurityResponse assignRole(
            Long administratorId,
            Long targetUserId,
            RoleName role,
            String correlationId);

    AdminUserSecurityResponse removeRole(
            Long administratorId,
            Long targetUserId,
            RoleName role,
            String correlationId);

    AdminUserSecurityResponse revokeSessions(
            Long administratorId,
            Long targetUserId,
            String correlationId);
}
