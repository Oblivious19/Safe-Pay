package com.ofss.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.security.access.AccessDeniedException;

import com.ofss.beans.RoleName;
import com.ofss.beans.User;
import com.ofss.beans.UserStatus;
import com.ofss.repository.UserDao;
import com.ofss.repository.UserRoleDao;

class StaffReadAccessTest {
    private final UserDao users = mock(UserDao.class);
    private final UserRoleDao roles = mock(UserRoleDao.class);
    private final StaffReadAccess access = new StaffReadAccess(users, roles);

    @Test
    void rejectsMissingIdentityBeforeQuerying() {
        assertThatThrownBy(() -> access.requireActiveRole(null, RoleName.SYSTEM_ADMIN))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> access.requireActiveRole(0L, RoleName.SYSTEM_ADMIN))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(users, roles);
    }

    @Test
    void rejectsMissingUser() {
        when(users.findById(10L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> access.requireActiveRole(10L, RoleName.SYSTEM_ADMIN))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(roles);
    }

    @ParameterizedTest
    @EnumSource(value = UserStatus.class, names = {"LOCKED", "DISABLED"})
    void rejectsInactiveUser(UserStatus status) {
        User user = mock(User.class);
        when(user.getStatus()).thenReturn(status);
        when(users.findById(10L)).thenReturn(Optional.of(user));
        assertThatThrownBy(() -> access.requireActiveRole(10L, RoleName.SYSTEM_ADMIN))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(roles);
    }

    @Test
    void rejectsRevokedRoleAndAllowsCurrentExactRole() {
        User user = mock(User.class);
        when(user.getStatus()).thenReturn(UserStatus.ACTIVE);
        when(users.findById(10L)).thenReturn(Optional.of(user));
        assertThatThrownBy(() -> access.requireActiveRole(10L, RoleName.SYSTEM_ADMIN))
                .isInstanceOf(AccessDeniedException.class);
        when(roles.existsByUser_UserIdAndRole_RoleCode(10L, RoleName.SYSTEM_ADMIN)).thenReturn(true);
        access.requireActiveRole(10L, RoleName.SYSTEM_ADMIN);
    }
}
