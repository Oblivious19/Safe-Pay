package com.ofss.security;

import java.util.EnumSet;
import java.util.Set;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

import com.ofss.beans.RoleName;

public final class AuthenticatedUser {

    private AuthenticatedUser() {
        // Utility class; instantiation is prohibited.
    }

    public static SafePayPrincipal principal(
            Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal()
                        instanceof SafePayPrincipal principal)) {
            throw new AccessDeniedException(
                    "Authenticated SafePay principal is required");
        }
        return principal;
    }

    public static Long userId(Authentication authentication) {
        return principal(authentication).getUserId();
    }

    public static Set<RoleName> roles(Authentication authentication) {
        EnumSet<RoleName> roles = EnumSet.noneOf(RoleName.class);
        principal(authentication).getAuthorities().forEach(authority -> {
            try {
                roles.add(RoleName.valueOf(authority.getAuthority()));
            } catch (IllegalArgumentException ignoredUnknownAuthority) {
                // An unknown authority never grants SafePay access.
            }
        });
        if (roles.isEmpty()) {
            throw new AccessDeniedException(
                    "A SafePay authority is required");
        }
        return Set.copyOf(roles);
    }
}
