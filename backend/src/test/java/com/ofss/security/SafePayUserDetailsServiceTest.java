package com.ofss.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import com.ofss.beans.Role;
import com.ofss.beans.RoleName;
import com.ofss.beans.User;
import com.ofss.beans.UserRole;
import com.ofss.beans.UserStatus;
import com.ofss.repository.UserRoleDao;
import com.ofss.services.UserService;

@ExtendWith(MockitoExtension.class)
class SafePayUserDetailsServiceTest {

    @Mock
    private UserService userService;

    @Mock
    private UserRoleDao userRoleDao;

    @Mock
    private User user;

    private SafePayUserDetailsService userDetailsService;

    @BeforeEach
    void setUp() {
        userDetailsService =
                new SafePayUserDetailsService(
                        userService,
                        userRoleDao);
    }

    @Test
    void loadsCustomerWithCustomerAuthority() {
        prepareActiveUser(
                101L,
                "customer@example.com");

        UserRole customerAssignment =
        assignment(RoleName.CUSTOMER);

        when(userRoleDao
                .findAllByUser_UserIdOrderByAssignedAtAsc(101L))
                .thenReturn(List.of(customerAssignment));

        UserDetails details =
                userDetailsService.loadUserByUsername(
                        "customer@example.com");

        assertThat(details).isInstanceOf(
                SafePayPrincipal.class);
        assertThat(details.getUsername())
                .isEqualTo("customer@example.com");
        assertThat(details.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("CUSTOMER");
        assertThat(details.isEnabled()).isTrue();
        assertThat(details.isAccountNonLocked()).isTrue();
    }

    @Test
    void loadsCombinedAdminWithThreeCanonicalAuthorities() {
        prepareActiveUser(
                201L,
                "admin@example.com");

        UserRole riskOfficerAssignment =
        assignment(RoleName.RISK_OFFICER);

        UserRole systemAdminAssignment =
                assignment(RoleName.SYSTEM_ADMIN);

        UserRole auditorAssignment =
                assignment(RoleName.AUDITOR);

        when(userRoleDao
                .findAllByUser_UserIdOrderByAssignedAtAsc(201L))
                .thenReturn(List.of(
                        riskOfficerAssignment,
                        systemAdminAssignment,
                        auditorAssignment));

        UserDetails details =
                userDetailsService.loadUserByUsername(
                        "admin@example.com");

        assertThat(details.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder(
                        "RISK_OFFICER",
                        "SYSTEM_ADMIN",
                        "AUDITOR");

        assertThat(details.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .doesNotContain("ADMIN", "CUSTOMER");
    }

    @Test
    void returnsGenericFailureForUnknownLogin() {
        when(userService.findByLoginIdentifier(
                "unknown@example.com"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(
                () -> userDetailsService.loadUserByUsername(
                        "unknown@example.com"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessage("Invalid credentials");

        verifyNoInteractions(userRoleDao);
    }

    @Test
    void rejectsUserWithoutAssignedAuthority() {
        when(user.getUserId()).thenReturn(301L);

        when(userService.findByLoginIdentifier(
                "unassigned@example.com"))
                .thenReturn(Optional.of(user));

        when(userRoleDao
                .findAllByUser_UserIdOrderByAssignedAtAsc(301L))
                .thenReturn(List.of());

        assertThatThrownBy(
                () -> userDetailsService.loadUserByUsername(
                        "unassigned@example.com"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessage("Invalid credentials");
    }

    @Test
    void mapsLockedAndDisabledStatusFlags() {
        Set<GrantedAuthority> authorities = Set.of(
                new SimpleGrantedAuthority("CUSTOMER"));

        SafePayPrincipal lockedPrincipal =
                new SafePayPrincipal(
                        401L,
                        "locked@example.com",
                        "stored-hash",
                        UserStatus.LOCKED,
                        0L,
                        authorities);

        SafePayPrincipal disabledPrincipal =
                new SafePayPrincipal(
                        402L,
                        "disabled@example.com",
                        "stored-hash",
                        UserStatus.DISABLED,
                        0L,
                        authorities);

        assertThat(lockedPrincipal.isEnabled()).isTrue();
        assertThat(lockedPrincipal.isAccountNonLocked())
                .isFalse();

        assertThat(disabledPrincipal.isEnabled()).isFalse();
        assertThat(disabledPrincipal.isAccountNonLocked())
                .isTrue();
    }

    private void prepareActiveUser(
            Long userId,
            String email) {

        when(userService.findByLoginIdentifier(email))
                .thenReturn(Optional.of(user));
        when(user.getUserId()).thenReturn(userId);
        when(user.getEmail()).thenReturn(email);
        when(user.getPasswordHash())
                .thenReturn("stored-password-hash");
        when(user.getStatus())
                .thenReturn(UserStatus.ACTIVE);
        when(user.getSecurityVersion()).thenReturn(0L);
    }

    private UserRole assignment(RoleName roleName) {
        Role role = mock(Role.class);
        UserRole assignment = mock(UserRole.class);

        when(role.getRoleCode()).thenReturn(roleName);
        when(assignment.getRole()).thenReturn(role);

        return assignment;
    }
}