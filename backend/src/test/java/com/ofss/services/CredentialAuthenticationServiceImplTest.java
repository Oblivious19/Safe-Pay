package com.ofss.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import com.ofss.beans.UserStatus;
import com.ofss.dto.auth.LoginRequest;
import com.ofss.security.SafePayPrincipal;

@ExtendWith(MockitoExtension.class)
class CredentialAuthenticationServiceImplTest {

    @Mock
    private UserDetailsService userDetailsService;

    @Mock
    private PasswordHashingService passwordHashingService;

    private CredentialAuthenticationService authenticationService;

    @BeforeEach
    void setUp() {
        authenticationService =
                new CredentialAuthenticationServiceImpl(
                        userDetailsService,
                        passwordHashingService);
    }

    @Test
    void authenticatesActiveUserWithCorrectPassword() {
        LoginRequest request = loginRequest();
        SafePayPrincipal principal =
                principal(UserStatus.ACTIVE);

        when(userDetailsService.loadUserByUsername(
                "customer@example.com"))
                .thenReturn(principal);

        when(passwordHashingService.matches(
                "SafePay@2026",
                "stored-password-hash"))
                .thenReturn(true);

        SafePayPrincipal authenticated =
                authenticationService.authenticate(request);

        assertThat(authenticated).isSameAs(principal);

        verify(passwordHashingService).matches(
                "SafePay@2026",
                "stored-password-hash");
    }

    @Test
    void rejectsIncorrectPasswordWithGenericMessage() {
        LoginRequest request = loginRequest();
        SafePayPrincipal principal =
                principal(UserStatus.ACTIVE);

        when(userDetailsService.loadUserByUsername(
                "customer@example.com"))
                .thenReturn(principal);

        when(passwordHashingService.matches(
                "SafePay@2026",
                "stored-password-hash"))
                .thenReturn(false);

        assertInvalidCredentials(request);
    }

    @Test
    void rejectsUnknownUserWithGenericMessage() {
        LoginRequest request = loginRequest();

        when(userDetailsService.loadUserByUsername(
                "customer@example.com"))
                .thenThrow(new UsernameNotFoundException(
                        "Invalid credentials"));

        assertInvalidCredentials(request);
        verifyNoInteractions(passwordHashingService);
    }

    @ParameterizedTest
    @EnumSource(
            value = UserStatus.class,
            names = {"LOCKED", "DISABLED"})
    void rejectsUnavailableAccountWithGenericMessage(
            UserStatus status) {

        LoginRequest request = loginRequest();
        SafePayPrincipal principal = principal(status);

        when(userDetailsService.loadUserByUsername(
                "customer@example.com"))
                .thenReturn(principal);

        when(passwordHashingService.matches(
                "SafePay@2026",
                "stored-password-hash"))
                .thenReturn(true);

        assertInvalidCredentials(request);
    }

    private void assertInvalidCredentials(
            LoginRequest request) {

        assertThatThrownBy(
                () -> authenticationService.authenticate(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid credentials");
    }

    private LoginRequest loginRequest() {
        return new LoginRequest(
                "customer@example.com",
                "SafePay@2026");
    }

    private SafePayPrincipal principal(UserStatus status) {
        return new SafePayPrincipal(
                101L,
                "customer@example.com",
                "stored-password-hash",
                status,
                0L,
                Set.of(new SimpleGrantedAuthority("CUSTOMER")));
    }
}