package com.ofss.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import com.ofss.beans.Role;
import com.ofss.beans.User;
import com.ofss.beans.UserStatus;
import com.ofss.repository.UserDao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class LoginLockoutTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-13T10:00:00Z"), ZoneOffset.UTC);
    private final LocalDateTime now = LocalDateTime.now(clock);
    private UserDao users;
    private User user;
    private LoginService service;

    @BeforeEach
    void setup() {
        users = mock(UserDao.class);
        service = new LoginService(users, clock);
        user = new User();
        user.setUserId(103L);
        user.setEmail("test@example.com");
        user.setPhone("9876543210");
        user.setName("Test");
        Role role = new Role();
        role.setRoleName("CUSTOMER");
        user.setRole(role);
        user.setPasswordHash(new BCryptPasswordEncoder().encode("correct"));
        when(users.findForLoginByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(users.findForLoginByPhone(user.getPhone())).thenReturn(Optional.of(user));
    }

    @Test
    void fourFailuresStayActiveAndFifthLocksForExactlyFifteenMinutes() {
        for (int attempt = 1; attempt <= 4; attempt++) {
            assertThrows(BadCredentialsException.class, () -> service.login(user.getEmail(), "wrong"));
            assertEquals(attempt, user.getFailedLoginAttempts());
            assertEquals(UserStatus.ACTIVE, user.getStatus());
            assertNull(user.getLockedUntil());
        }
        assertThrows(LockedException.class, () -> service.login(user.getEmail(), "wrong"));
        assertEquals(5, user.getFailedLoginAttempts());
        assertEquals(UserStatus.LOCKED, user.getStatus());
        assertEquals(now.plusMinutes(15), user.getLockedUntil());
        assertNull(user.getLastLoginAt());
        verify(users, times(5)).save(user);
    }

    @ParameterizedTest
    @ValueSource(strings = {"correct", "wrong"})
    void activeLockRejectsPasswordsWithoutExtendingOrSaving(String password) {
        user.setStatus(UserStatus.LOCKED);
        user.setFailedLoginAttempts(5);
        user.setLockedUntil(now.plusSeconds(1));
        assertThrows(LockedException.class, () -> service.login(user.getEmail(), password));
        assertEquals(now.plusSeconds(1), user.getLockedUntil());
        assertEquals(5, user.getFailedLoginAttempts());
        assertNull(user.getLastLoginAt());
        verify(users, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void atOrAfterExpiryCorrectPasswordRestoresActiveUser(int elapsedSeconds) {
        user.setStatus(UserStatus.LOCKED);
        user.setFailedLoginAttempts(5);
        user.setLockedUntil(now.minusSeconds(elapsedSeconds));
        var principal = service.login(user.getEmail(), "correct");
        assertEquals(UserStatus.ACTIVE, principal.status());
        assertEquals(UserStatus.ACTIVE, user.getStatus());
        assertEquals(0, user.getFailedLoginAttempts());
        assertNull(user.getLockedUntil());
        assertEquals(now, user.getLastLoginAt());
        verify(users).save(user);
    }

    @Test
    void expiredLockWrongPasswordStartsFreshFailureWindowWithoutLogin() {
        user.setStatus(UserStatus.LOCKED);
        user.setFailedLoginAttempts(5);
        user.setLockedUntil(now.minusSeconds(1));
        assertThrows(BadCredentialsException.class, () -> service.login(user.getEmail(), "wrong"));
        assertEquals(UserStatus.ACTIVE, user.getStatus());
        assertEquals(1, user.getFailedLoginAttempts());
        assertNull(user.getLockedUntil());
        assertNull(user.getLastLoginAt());
        verify(users).save(user);
    }

    @ParameterizedTest
    @EnumSource(value = UserStatus.class, names = {"SUSPENDED", "INACTIVE"})
    void expiredTimestampNeverReactivatesSuspendedOrInactiveUser(UserStatus status) {
        user.setStatus(status);
        user.setFailedLoginAttempts(5);
        user.setLockedUntil(now.minusMinutes(1));
        assertThrows(DisabledException.class, () -> service.login(user.getEmail(), "correct"));
        assertEquals(status, user.getStatus());
        assertEquals(5, user.getFailedLoginAttempts());
        assertEquals(now.minusMinutes(1), user.getLockedUntil());
        verify(users, never()).save(any());
    }

    @Test
    void lockWithoutExpiryIsNotAutomaticallyReleased() {
        user.setStatus(UserStatus.LOCKED);
        assertThrows(LockedException.class, () -> service.login(user.getEmail(), "correct"));
        assertEquals(UserStatus.LOCKED, user.getStatus());
        verify(users, never()).save(any());
    }

    @Test
    void switchingEmailAndPhoneCannotBypassTheSharedFiveAttemptLock() {
        for (int attempt = 1; attempt <= 4; attempt++) {
            final boolean phone = attempt % 2 == 0;
            assertThrows(BadCredentialsException.class, () -> {
                if (phone) service.loginByPhone(user.getPhone(), "wrong");
                else service.login(user.getEmail(), "wrong");
            });
            assertEquals(attempt, user.getFailedLoginAttempts());
        }
        assertThrows(LockedException.class, () -> service.loginByPhone(user.getPhone(), "wrong"));
        assertEquals(now.plusMinutes(15), user.getLockedUntil());
        assertThrows(LockedException.class, () -> service.login(user.getEmail(), "correct"));
        assertThrows(LockedException.class, () -> service.loginByPhone(user.getPhone(), "correct"));
        assertEquals(5, user.getFailedLoginAttempts());
        verify(users, times(5)).save(user);
    }

    @Test
    void successfulPhoneLoginResetsFailuresMadeThroughEmail() {
        assertThrows(BadCredentialsException.class, () -> service.login(user.getEmail(), "wrong"));
        var principal = service.loginByPhone(user.getPhone(), "correct");
        assertEquals(user.getUserId(), principal.userId());
        assertEquals(user.getEmail(), principal.email());
        assertEquals(0, user.getFailedLoginAttempts());
        assertEquals(now, user.getLastLoginAt());
    }

    @Test
    void phoneLoginAfterExpiryStillRequiresTheCorrectPassword() {
        user.setStatus(UserStatus.LOCKED);
        user.setFailedLoginAttempts(5);
        user.setLockedUntil(now);
        assertThrows(BadCredentialsException.class, () -> service.loginByPhone(user.getPhone(), "wrong"));
        assertEquals(UserStatus.ACTIVE, user.getStatus());
        assertEquals(1, user.getFailedLoginAttempts());
        assertNull(user.getLastLoginAt());
    }
}
