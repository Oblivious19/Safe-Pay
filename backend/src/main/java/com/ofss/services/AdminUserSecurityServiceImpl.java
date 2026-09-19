package com.ofss.services;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ofss.beans.AuditLog;
import com.ofss.beans.AuditOutcome;
import com.ofss.beans.AuthSession;
import com.ofss.beans.Role;
import com.ofss.beans.RoleName;
import com.ofss.beans.User;
import com.ofss.beans.UserRole;
import com.ofss.beans.UserRoleId;
import com.ofss.beans.UserStatus;
import com.ofss.common.api.PagedResponse;
import com.ofss.dto.admin.AdminUserSecurityResponse;
import com.ofss.excp.ResourceNotFoundExcp;
import com.ofss.repository.AuditLogDao;
import com.ofss.repository.AuthSessionDao;
import com.ofss.repository.RoleDao;
import com.ofss.repository.UserDao;
import com.ofss.repository.UserRoleDao;

@Service
public class AdminUserSecurityServiceImpl
        implements AdminUserSecurityService {

    private final UserDao userDao;
    private final UserRoleDao userRoleDao;
    private final RoleDao roleDao;
    private final AuthSessionDao authSessionDao;
    private final AuditLogDao auditLogDao;

    public AdminUserSecurityServiceImpl(
            UserDao userDao,
            UserRoleDao userRoleDao,
            RoleDao roleDao,
            AuthSessionDao authSessionDao,
            AuditLogDao auditLogDao) {
        this.userDao = userDao;
        this.userRoleDao = userRoleDao;
        this.roleDao = roleDao;
        this.authSessionDao = authSessionDao;
        this.auditLogDao = auditLogDao;
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<AdminUserSecurityResponse> searchUsers(
            Long administratorId, String q, RoleName role, UserStatus status,
            int page, int size) {
        requireReadAdministrator(administratorId);
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("page must be non-negative and size between 1 and 100");
        }
        String query = q == null ? null : q.trim();
        if (query != null && query.length() > 120) {
            throw new IllegalArgumentException("q must not exceed 120 characters");
        }
        if (query != null) {
            query = query.isEmpty() ? null : "%" + query.toLowerCase(Locale.ROOT)
                    .replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        }
        Page<User> users = userDao.searchUsers(query, role, status, PageRequest.of(page, size));
        List<Long> userIds = users.getContent().stream().map(User::getUserId).toList();
        Map<Long, List<RoleName>> roles = userIds.isEmpty() ? Map.of()
                : userRoleDao.findAllForUsers(userIds).stream().collect(Collectors.groupingBy(
                        assignment -> assignment.getUser().getUserId(),
                        Collectors.mapping(assignment -> assignment.getRole().getRoleCode(),
                                Collectors.toList())));
        return PagedResponse.from(users, user -> AdminUserSecurityResponse.from(
                user, roles.getOrDefault(user.getUserId(), List.of())));
    }

    @Override
    @Transactional(readOnly = true)
    public AdminUserSecurityResponse getUserDetails(Long administratorId, Long targetUserId) {
        requireReadAdministrator(administratorId);
        requirePositive(targetUserId, "targetUserId");
        User user = userDao.findById(targetUserId).orElseThrow(() ->
                new ResourceNotFoundExcp("USER_NOT_FOUND", "User was not found"));
        return response(user);
    }

    private void requireReadAdministrator(Long administratorId) {
        requirePositive(administratorId, "administratorId");
        User administrator = userDao.findById(administratorId).orElseThrow(() ->
                new AccessDeniedException("An active SYSTEM_ADMIN is required"));
        requireAdministrator(administrator);
    }

    @Override
    @Transactional
    public AdminUserSecurityResponse updateStatus(
            Long administratorId,
            Long targetUserId,
            UserStatus status,
            String correlationId) {
        Participants participants = lockParticipants(
                administratorId,
                targetUserId);
        requireAdministrator(participants.administrator());
        OffsetDateTime now = userDao.currentDatabaseTime();
        boolean changed = participants.target()
                .applyAdministrativeStatus(status, now);
        if (changed) {
            revokeActiveSessions(
                    participants.target(),
                    now,
                    "ADMIN_STATUS_CHANGE");
            audit(
                    participants,
                    "USER_STATUS_CHANGED",
                    "STATUS_" + status.name(),
                    correlationId,
                    now);
        }
        return response(participants.target());
    }

    @Override
    @Transactional
    public AdminUserSecurityResponse assignRole(
            Long administratorId,
            Long targetUserId,
            RoleName roleName,
            String correlationId) {
        Participants participants = lockParticipants(
                administratorId,
                targetUserId);
        requireAdministrator(participants.administrator());
        RoleName requiredRole = java.util.Objects.requireNonNull(
                roleName,
                "role is required");
        Role role = roleDao.findByRoleCode(requiredRole)
                .orElseThrow(() -> new IllegalStateException(
                        "Canonical application role is missing"));
        UserRoleId assignmentId = new UserRoleId(
                targetUserId,
                role.getRoleId());
        if (!userRoleDao.existsById(assignmentId)) {
            OffsetDateTime now = userDao.currentDatabaseTime();
            userRoleDao.save(UserRole.assign(
                    participants.target(),
                    role,
                    participants.administrator(),
                    now));
            participants.target().recordSecurityPolicyChange(now);
            revokeActiveSessions(
                    participants.target(),
                    now,
                    "ADMIN_ROLE_CHANGE");
            audit(
                    participants,
                    "USER_ROLE_ASSIGNED",
                    "ROLE_" + requiredRole.name(),
                    correlationId,
                    now);
        }
        return response(participants.target());
    }

    @Override
    @Transactional
    public AdminUserSecurityResponse removeRole(
            Long administratorId,
            Long targetUserId,
            RoleName roleName,
            String correlationId) {
        Participants participants = lockParticipants(
                administratorId,
                targetUserId);
        requireAdministrator(participants.administrator());
        RoleName requiredRole = java.util.Objects.requireNonNull(
                roleName,
                "role is required");
        Role role = roleDao.findByRoleCode(requiredRole)
                .orElseThrow(() -> new IllegalStateException(
                        "Canonical application role is missing"));
        UserRoleId assignmentId = new UserRoleId(
                targetUserId,
                role.getRoleId());
        if (!userRoleDao.existsById(assignmentId)) {
            return response(participants.target());
        }
        OffsetDateTime now = userDao.currentDatabaseTime();
        userRoleDao.deleteById(assignmentId);
        participants.target().recordSecurityPolicyChange(now);
        revokeActiveSessions(
                participants.target(),
                now,
                "ADMIN_ROLE_CHANGE");
        audit(
                participants,
                "USER_ROLE_REMOVED",
                "ROLE_" + requiredRole.name(),
                correlationId,
                now);
        return responseExcluding(participants.target(), requiredRole);
    }

    @Override
    @Transactional
    public AdminUserSecurityResponse revokeSessions(
            Long administratorId,
            Long targetUserId,
            String correlationId) {
        Participants participants = lockParticipants(
                administratorId,
                targetUserId);
        requireAdministrator(participants.administrator());
        OffsetDateTime now = userDao.currentDatabaseTime();
        participants.target().recordSecurityPolicyChange(now);
        revokeActiveSessions(
                participants.target(),
                now,
                "ADMIN_SESSION_REVOCATION");
        audit(
                participants,
                "AUTH_SESSIONS_REVOKED",
                "ADMIN_REQUEST",
                correlationId,
                now);
        return response(participants.target());
    }

    private Participants lockParticipants(
            Long administratorId,
            Long targetUserId) {
        requirePositive(administratorId, "administratorId");
        requirePositive(targetUserId, "targetUserId");
        Long firstId = Math.min(administratorId, targetUserId);
        Long secondId = Math.max(administratorId, targetUserId);
        User first = requiredLockedUser(firstId);
        User second = firstId.equals(secondId)
                ? first
                : requiredLockedUser(secondId);
        return administratorId.equals(firstId)
                ? new Participants(first, second)
                : new Participants(second, first);
    }

    private User requiredLockedUser(Long userId) {
        return userDao.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundExcp(
                        "USER_NOT_FOUND",
                        "User was not found"));
    }

    private void requireAdministrator(User administrator) {
        if (administrator.getStatus() != UserStatus.ACTIVE
                || !userRoleDao.existsByUser_UserIdAndRole_RoleCode(
                        administrator.getUserId(),
                        RoleName.SYSTEM_ADMIN)) {
            throw new AccessDeniedException(
                    "An active SYSTEM_ADMIN is required");
        }
    }

    private void revokeActiveSessions(
            User target,
            OffsetDateTime now,
            String reason) {
        List<AuthSession> sessions = authSessionDao
                .findAllByUser_UserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtAsc(
                        target.getUserId(),
                        now);
        sessions.forEach(session -> session.revoke(reason, now));
    }

    private void audit(
            Participants participants,
            String actionCode,
            String reasonCode,
            String correlationId,
            OffsetDateTime now) {
        auditLogDao.save(AuditLog.administrativeUserEvent(
                UUID.randomUUID().toString(),
                participants.administrator(),
                participants.target(),
                actionCode,
                AuditOutcome.SUCCESS,
                reasonCode,
                correlationId,
                now));
    }

    private AdminUserSecurityResponse response(User user) {
        return AdminUserSecurityResponse.from(
                user,
                userRoleDao.findAllByUser_UserIdOrderByAssignedAtAsc(
                                user.getUserId())
                        .stream()
                        .map(assignment -> assignment.getRole().getRoleCode())
                        .toList());
    }

    private AdminUserSecurityResponse responseExcluding(
            User user,
            RoleName excludedRole) {
        return AdminUserSecurityResponse.from(
                user,
                userRoleDao.findAllByUser_UserIdOrderByAssignedAtAsc(
                                user.getUserId())
                        .stream()
                        .map(assignment -> assignment.getRole().getRoleCode())
                        .filter(role -> role != excludedRole)
                        .toList());
    }

    private static void requirePositive(Long value, String fieldName) {
        if (value == null || value <= 0L) {
            throw new IllegalArgumentException(
                    fieldName + " must be positive");
        }
    }

    private record Participants(User administrator, User target) {
    }
}
