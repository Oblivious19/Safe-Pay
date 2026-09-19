package com.ofss.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.ofss.beans.RoleName;
import com.ofss.beans.UserStatus;

class AuthenticatedUserTest {

    @Test
    void extractsCanonicalUserIdAndRoles() {
        var authentication = authentication(
                "CUSTOMER",
                "UNKNOWN_EXTERNAL_AUTHORITY");
        assertThat(AuthenticatedUser.userId(authentication)).isEqualTo(101L);
        assertThat(AuthenticatedUser.roles(authentication))
                .containsExactly(RoleName.CUSTOMER);
    }

    @Test
    void rejectsMissingAuthentication() {
        assertThatThrownBy(() -> AuthenticatedUser.userId(null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void rejectsAnonymousAuthentication() {
        var anonymous = new AnonymousAuthenticationToken(
                "key",
                "anonymousUser",
                Set.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
        assertThatThrownBy(() -> AuthenticatedUser.userId(anonymous))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void rejectsPrincipalWithoutCanonicalRole() {
        assertThatThrownBy(() -> AuthenticatedUser.roles(
                authentication("UNKNOWN")))
                .isInstanceOf(AccessDeniedException.class);
    }

    private static UsernamePasswordAuthenticationToken authentication(
            String... authorities) {
        SafePayPrincipal principal = new SafePayPrincipal(
                101L,
                "customer@example.com",
                "hash",
                UserStatus.ACTIVE,
                0L,
                java.util.Arrays.stream(authorities)
                        .map(SimpleGrantedAuthority::new)
                        .toList());
        return new UsernamePasswordAuthenticationToken(
                principal,
                null,
                principal.getAuthorities());
    }
}
