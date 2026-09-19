package com.ofss.security;

import java.io.Serial;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.ofss.beans.UserStatus;

public final class SafePayPrincipal implements UserDetails {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Long userId;
    private final String username;
    private final String passwordHash;
    private final UserStatus status;
    private final long securityVersion;
    private final Set<GrantedAuthority> authorities;

    public SafePayPrincipal(
            Long userId,
            String username,
            String passwordHash,
            UserStatus status,
            long securityVersion,
            Collection<? extends GrantedAuthority> authorities) {

        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException(
                    "userId must be positive");
        }

        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException(
                    "username is required");
        }

        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException(
                    "passwordHash is required");
        }

        this.userId = userId;
        this.username = username;
        this.passwordHash = passwordHash;
        this.status = Objects.requireNonNull(
                status,
                "status is required");
        this.securityVersion = securityVersion;

        LinkedHashSet<GrantedAuthority> copiedAuthorities =
                new LinkedHashSet<>(
                        Objects.requireNonNull(
                                authorities,
                                "authorities are required"));

        if (copiedAuthorities.isEmpty()) {
            throw new IllegalArgumentException(
                    "at least one authority is required");
        }

        this.authorities =
                Collections.unmodifiableSet(copiedAuthorities);
    }

    public Long getUserId() {
        return userId;
    }

    public long getSecurityVersion() {
        return securityVersion;
    }

    public UserStatus getStatus() {
        return status;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public Set<GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return status != UserStatus.LOCKED;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return status != UserStatus.DISABLED;
    }
}