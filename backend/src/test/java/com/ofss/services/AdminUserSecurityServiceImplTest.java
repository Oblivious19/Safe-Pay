package com.ofss.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import com.ofss.beans.AuditLog;
import com.ofss.beans.AuthSession;
import com.ofss.beans.Role;
import com.ofss.beans.RoleName;
import com.ofss.beans.User;
import com.ofss.beans.UserRole;
import com.ofss.beans.UserRoleId;
import com.ofss.beans.UserStatus;
import com.ofss.repository.AuditLogDao;
import com.ofss.repository.AuthSessionDao;
import com.ofss.repository.RoleDao;
import com.ofss.repository.UserDao;
import com.ofss.repository.UserRoleDao;

@ExtendWith(MockitoExtension.class)
class AdminUserSecurityServiceImplTest {

    private static final OffsetDateTime NOW =
            OffsetDateTime.parse("2026-09-17T11:00:00Z");
    private static final String CORRELATION = "admin-security-test";

    @Mock private UserDao userDao;
    @Mock private UserRoleDao userRoleDao;
    @Mock private RoleDao roleDao;
    @Mock private AuthSessionDao authSessionDao;
    @Mock private AuditLogDao auditLogDao;
    @Mock private AuthSession session;

    private User administrator;
    private User target;
    private AdminUserSecurityService service;

    @BeforeEach
    void setUp() {
        administrator = user(10L, "administrator@safepay.test");
        target = user(20L, "target@safepay.test");
        service = new AdminUserSecurityServiceImpl(
                userDao,
                userRoleDao,
                roleDao,
                authSessionDao,
                auditLogDao);
        org.mockito.Mockito.lenient().when(userDao.findByIdForUpdate(10L))
                .thenReturn(Optional.of(administrator));
        org.mockito.Mockito.lenient().when(userDao.findByIdForUpdate(20L))
                .thenReturn(Optional.of(target));
        org.mockito.Mockito.lenient().when(
                userRoleDao.existsByUser_UserIdAndRole_RoleCode(
                10L,
                RoleName.SYSTEM_ADMIN)).thenReturn(true);
    }

    @Test
    void searchesLiteralTextAndBulkLoadsRolesWithoutWritesOrLocks() {
        when(userDao.findById(10L)).thenReturn(Optional.of(administrator));
        var page = org.springframework.data.domain.PageRequest.of(0, 20);
        when(userDao.searchUsers("%a!%!_!!%", RoleName.CUSTOMER, UserStatus.ACTIVE, page))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(target), page, 1));
        UserRole assignment = UserRole.assign(target, role(30L, RoleName.CUSTOMER), administrator, NOW);
        when(userRoleDao.findAllForUsers(List.of(20L))).thenReturn(List.of(assignment));
        var result = service.searchUsers(10L, " A%_! ", RoleName.CUSTOMER, UserStatus.ACTIVE, 0, 20);
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().roles()).containsExactly(RoleName.CUSTOMER);
        verify(userDao, never()).findByIdForUpdate(any());
        verify(userRoleDao, never()).findAllByUser_UserIdOrderByAssignedAtAsc(any());
        org.mockito.Mockito.verifyNoInteractions(authSessionDao, auditLogDao);
    }

    @Test
    void emptyDirectorySkipsRoleFetch() {
        when(userDao.findById(10L)).thenReturn(Optional.of(administrator));
        var page = org.springframework.data.domain.PageRequest.of(0, 20);
        when(userDao.searchUsers(null, null, null, page))
                .thenReturn(org.springframework.data.domain.Page.empty(page));
        assertThat(service.searchUsers(10L, "  ", null, null, 0, 20).items()).isEmpty();
        verify(userRoleDao, never()).findAllForUsers(any());
    }

    @Test
    void directoryRejectsOversizedSearchAndPage() {
        when(userDao.findById(10L)).thenReturn(Optional.of(administrator));
        assertThatThrownBy(() -> service.searchUsers(10L, "x".repeat(121), null, null, 0, 20))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.searchUsers(10L, null, null, null, 0, 101))
                .isInstanceOf(IllegalArgumentException.class);
        verify(userDao, never()).searchUsers(any(), any(), any(), any());
    }

    @Test
    void directoryRejectsInactiveAdministratorBeforeSearching() {
        administrator.applyAdministrativeStatus(UserStatus.DISABLED, NOW);
        when(userDao.findById(10L)).thenReturn(Optional.of(administrator));
        assertThatThrownBy(() -> service.searchUsers(10L, null, null, null, 0, 20))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.getUserDetails(10L, 20L)).isInstanceOf(AccessDeniedException.class);
        verify(userDao, never()).findById(20L);
        verify(userDao, never()).searchUsers(any(), any(), any(), any());
    }

    @Test
    void detailsReuseSafeDtoAndStableNotFoundWithoutLocking() {
        when(userDao.findById(10L)).thenReturn(Optional.of(administrator));
        when(userDao.findById(20L)).thenReturn(Optional.of(target));
        responseRoles(RoleName.CUSTOMER);
        assertThat(service.getUserDetails(10L, 20L).userId()).isEqualTo("20");
        assertThatThrownBy(() -> service.getUserDetails(10L, 999L))
                .isInstanceOf(com.ofss.excp.ResourceNotFoundExcp.class);
        verify(userDao, never()).findByIdForUpdate(any());
        org.mockito.Mockito.verifyNoInteractions(authSessionDao, auditLogDao);
    }

    @Test
    void lockInvalidatesTokensRevokesSessionsAndAudits() {
        responseRoles(RoleName.CUSTOMER);
        when(userDao.currentDatabaseTime()).thenReturn(NOW);
        when(authSessionDao
                .findAllByUser_UserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtAsc(
                        20L,
                        NOW)).thenReturn(List.of(session));

        var response = service.updateStatus(
                10L, 20L, UserStatus.LOCKED, CORRELATION);

        assertThat(response.status()).isEqualTo("LOCKED");
        assertThat(response.securityVersion()).isEqualTo(1L);
        verify(session).revoke("ADMIN_STATUS_CHANGE", NOW);
        assertAudit("USER_STATUS_CHANGED", "STATUS_LOCKED");
    }

    @Test
    void repeatedActiveStatusPerformsNoSecurityMutation() {
        responseRoles(RoleName.CUSTOMER);
        when(userDao.currentDatabaseTime()).thenReturn(NOW);

        service.updateStatus(10L, 20L, UserStatus.ACTIVE, CORRELATION);

        verify(auditLogDao, never()).save(any());
    }

    @Test
    void assignsRoleOnceAndInvalidatesExistingTokens() {
        Role auditor = role(40L, RoleName.AUDITOR);
        when(roleDao.findByRoleCode(RoleName.AUDITOR))
                .thenReturn(Optional.of(auditor));
        when(userRoleDao.existsById(new UserRoleId(20L, 40L)))
                .thenReturn(false);
        when(userDao.currentDatabaseTime()).thenReturn(NOW);
        responseRoles(RoleName.CUSTOMER, RoleName.AUDITOR);

        var response = service.assignRole(
                10L, 20L, RoleName.AUDITOR, CORRELATION);

        assertThat(response.roles())
                .containsExactly(RoleName.CUSTOMER, RoleName.AUDITOR);
        verify(userRoleDao).save(any(UserRole.class));
        assertAudit("USER_ROLE_ASSIGNED", "ROLE_AUDITOR");
    }

    @Test
    void repeatedRoleAssignmentIsNaturallyIdempotent() {
        Role auditor = role(40L, RoleName.AUDITOR);
        when(roleDao.findByRoleCode(RoleName.AUDITOR))
                .thenReturn(Optional.of(auditor));
        when(userRoleDao.existsById(new UserRoleId(20L, 40L)))
                .thenReturn(true);
        responseRoles(RoleName.CUSTOMER, RoleName.AUDITOR);

        service.assignRole(10L, 20L, RoleName.AUDITOR, CORRELATION);

        verify(userRoleDao, never()).save(any(UserRole.class));
        verify(auditLogDao, never()).save(any());
    }

    @Test
    void removesAnExplicitlySelectedSoleRoleWithoutInventingPolicy() {
        Role customer = role(30L, RoleName.CUSTOMER);
        when(roleDao.findByRoleCode(RoleName.CUSTOMER))
                .thenReturn(Optional.of(customer));
        when(userRoleDao.existsById(new UserRoleId(20L, 30L)))
                .thenReturn(true);
        when(userDao.currentDatabaseTime()).thenReturn(NOW);
        responseRoles(RoleName.CUSTOMER);

        var response = service.removeRole(
                10L, 20L, RoleName.CUSTOMER, CORRELATION);

        verify(userRoleDao).deleteById(new UserRoleId(20L, 30L));
        assertThat(response.roles()).isEmpty();
        assertAudit("USER_ROLE_REMOVED", "ROLE_CUSTOMER");
    }

    @Test
    void removesRoleAndInvalidatesExistingTokens() {
        Role auditor = role(40L, RoleName.AUDITOR);
        when(roleDao.findByRoleCode(RoleName.AUDITOR))
                .thenReturn(Optional.of(auditor));
        when(userRoleDao.existsById(new UserRoleId(20L, 40L)))
                .thenReturn(true);
        when(userDao.currentDatabaseTime()).thenReturn(NOW);
        responseRoles(RoleName.CUSTOMER, RoleName.AUDITOR);

        var response = service.removeRole(
                10L, 20L, RoleName.AUDITOR, CORRELATION);

        verify(userRoleDao).deleteById(new UserRoleId(20L, 40L));
        assertThat(response.roles()).containsExactly(RoleName.CUSTOMER);
        assertAudit("USER_ROLE_REMOVED", "ROLE_AUDITOR");
    }

    @Test
    void globalSessionRevocationAlwaysInvalidatesAccessTokens() {
        when(userDao.currentDatabaseTime()).thenReturn(NOW);
        when(authSessionDao
                .findAllByUser_UserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtAsc(
                        20L,
                        NOW)).thenReturn(List.of(session));
        responseRoles(RoleName.CUSTOMER);

        var response = service.revokeSessions(
                10L, 20L, CORRELATION);

        assertThat(response.securityVersion()).isEqualTo(1L);
        verify(session).revoke("ADMIN_SESSION_REVOCATION", NOW);
        assertAudit("AUTH_SESSIONS_REVOKED", "ADMIN_REQUEST");
    }

    @Test
    void serviceLayerRejectsInactiveAdministrator() {
        administrator.applyAdministrativeStatus(UserStatus.DISABLED, NOW);
        assertThatThrownBy(() -> service.revokeSessions(
                10L, 20L, CORRELATION))
                .isInstanceOf(AccessDeniedException.class);
        verify(userDao, never()).currentDatabaseTime();
    }

    private void responseRoles(RoleName... roles) {
        List<UserRole> assignments = java.util.Arrays.stream(roles)
                .map(roleName -> UserRole.assign(
                        target,
                        role(100L + roleName.ordinal(), roleName),
                        administrator,
                        NOW.minusMinutes(1)))
                .toList();
        when(userRoleDao.findAllByUser_UserIdOrderByAssignedAtAsc(20L))
                .thenReturn(assignments);
    }

    private void assertAudit(String actionCode, String reasonCode) {
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(
                AuditLog.class);
        verify(auditLogDao).save(captor.capture());
        assertThat(captor.getValue().getActionCode()).isEqualTo(actionCode);
        assertThat(captor.getValue().getReasonCode()).isEqualTo(reasonCode);
    }

    private static User user(Long id, String email) {
        User user = User.createActiveUser(
                "Security User",
                email,
                null,
                "hash",
                NOW.minusDays(1));
        ReflectionTestUtils.setField(user, "userId", id);
        return user;
    }

    private static Role role(Long id, RoleName roleName) {
        Role role = org.mockito.Mockito.mock(Role.class);
        when(role.getRoleId()).thenReturn(id);
        org.mockito.Mockito.lenient()
                .when(role.getRoleCode())
                .thenReturn(roleName);
        return role;
    }
}
