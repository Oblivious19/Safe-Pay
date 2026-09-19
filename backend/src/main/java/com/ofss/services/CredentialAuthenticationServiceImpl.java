package com.ofss.services;

import java.util.Objects;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.ofss.dto.auth.LoginRequest;
import com.ofss.security.SafePayPrincipal;

@Service
public class CredentialAuthenticationServiceImpl
        implements CredentialAuthenticationService {

    private static final String INVALID_CREDENTIALS_MESSAGE =
            "Invalid credentials";

    private final UserDetailsService userDetailsService;
    private final PasswordHashingService passwordHashingService;

    public CredentialAuthenticationServiceImpl(
            UserDetailsService userDetailsService,
            PasswordHashingService passwordHashingService) {

        this.userDetailsService = userDetailsService;
        this.passwordHashingService = passwordHashingService;
    }

    @Override
    public SafePayPrincipal authenticate(LoginRequest request) {
        Objects.requireNonNull(request, "request is required");

        UserDetails loadedUser;

        try {
            loadedUser = userDetailsService.loadUserByUsername(
                    request.loginIdentifier());
        } catch (UsernameNotFoundException exception) {
            throw new BadCredentialsException(
                    INVALID_CREDENTIALS_MESSAGE,
                    exception);
        }

        if (!(loadedUser instanceof SafePayPrincipal principal)) {
            throw new IllegalStateException(
                    "Unsupported authenticated principal type");
        }

        boolean passwordMatches =
                passwordHashingService.matches(
                        request.password(),
                        principal.getPassword());

        if (!passwordMatches
                || !principal.isEnabled()
                || !principal.isAccountNonLocked()
                || !principal.isAccountNonExpired()
                || !principal.isCredentialsNonExpired()) {

            throw new BadCredentialsException(
                    INVALID_CREDENTIALS_MESSAGE);
        }

        return principal;
    }
}