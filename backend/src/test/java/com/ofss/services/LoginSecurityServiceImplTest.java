package com.ofss.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.ofss.beans.AuditOutcome;
import com.ofss.beans.AuthSession;
import com.ofss.beans.User;
import com.ofss.beans.UserStatus;
import com.ofss.repository.AuthSessionDao;
import com.ofss.repository.UserDao;
import com.ofss.security.AuthenticationPolicyProperties;

@ExtendWith(MockitoExtension.class)
class LoginSecurityServiceImplTest {

    private static final String LOGIN = "user@safepay.test";
    private static final String CORRELATION = "login-test";
    private static final OffsetDateTime NOW =
            OffsetDateTime.parse("2026-09-17T08:00:00Z");

    @Mock private UserDao userDao;
    @Mock private AuthSessionDao authSessionDao;
    @Mock private AuthenticationAuditService auditService;

    private LoginSecurityService service;

    @BeforeEach
    void setUp() {
        service = new LoginSecurityServiceImpl(
                userDao,
                authSessionDao,
                auditService,
                new AuthenticationPolicyProperties(
                        5,
                        Duration.ofMinutes(15),
                        Duration.ofDays(7)));
    }

    @Test
    void releasesAnExpiredTemporaryLockBeforeAuthentication() {
        User user = lockedUser();
        when(userDao.findByLoginIdentifierForUpdate(LOGIN))
                .thenReturn(Optional.of(user));
        when(userDao.currentDatabaseTime())
                .thenReturn(user.getLockedUntil());

        service.prepareForAuthentication(LOGIN);

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void unknownUserPreparationPerformsNoDatabaseTimeMutation() {
        when(userDao.findByLoginIdentifierForUpdate(LOGIN))
                .thenReturn(Optional.empty());

        service.prepareForAuthentication(LOGIN);

        verify(userDao, never()).currentDatabaseTime();
    }

    @Test
    void unknownCredentialFailureProducesGenericSystemAudit() {
        when(userDao.currentDatabaseTime()).thenReturn(NOW);
        when(userDao.findByLoginIdentifierForUpdate(LOGIN))
                .thenReturn(Optional.empty());

        service.recordFailure(LOGIN, CORRELATION);

        verify(auditService).record(
                eq(null),
                eq("AUTH_LOGIN_FAILED"),
                eq(AuditOutcome.DENIED),
                eq("INVALID_CREDENTIALS"),
                eq(CORRELATION),
                eq(NOW));
    }

    @Test
    void knownFailureIncrementsCounterWithoutEarlySessionRevocation() {
        User user = activeUser();
        when(userDao.currentDatabaseTime()).thenReturn(NOW);
        when(userDao.findByLoginIdentifierForUpdate(LOGIN))
                .thenReturn(Optional.of(user));

        service.recordFailure(LOGIN, CORRELATION);

        assertThat(user.getFailedLoginCount()).isEqualTo(1);
        verify(authSessionDao, never())
                .findAllByUser_UserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtAsc(
                        any(), any());
    }

    @Test
    void thresholdFailureRevokesEveryActiveSession() {
        User user = activeUser();
        for (int attempt = 1; attempt <= 4; attempt++) {
            user.recordFailedLogin(
                    NOW.minusMinutes(5 - attempt),
                    5,
                    Duration.ofMinutes(15));
        }
        AuthSession first = mock(AuthSession.class);
        AuthSession second = mock(AuthSession.class);

        when(userDao.currentDatabaseTime()).thenReturn(NOW);
        when(userDao.findByLoginIdentifierForUpdate(LOGIN))
                .thenReturn(Optional.of(user));
        when(authSessionDao
                .findAllByUser_UserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtAsc(
                        101L,
                        NOW))
                .thenReturn(List.of(first, second));

        service.recordFailure(LOGIN, CORRELATION);

        verify(first).revoke("FAILED_LOGIN_LOCK", NOW);
        verify(second).revoke("FAILED_LOGIN_LOCK", NOW);
        verify(auditService).record(
                user,
                "AUTH_ACCOUNT_TEMPORARILY_LOCKED",
                AuditOutcome.SUCCESS,
                "FAILED_LOGIN_THRESHOLD",
                CORRELATION,
                NOW);
    }

    @Test
    void failureDuringExistingLockDoesNotRevokeAgain() {
        User user = lockedUser();
        when(userDao.currentDatabaseTime()).thenReturn(NOW);
        when(userDao.findByLoginIdentifierForUpdate(LOGIN))
                .thenReturn(Optional.of(user));

        service.recordFailure(LOGIN, CORRELATION);

        verify(authSessionDao, never())
                .findAllByUser_UserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtAsc(
                        any(), any());
    }

    private static User activeUser() {
        User user = User.createActiveUser(
                "Login User",
                LOGIN,
                null,
                "test-password-hash",
                NOW.minusHours(1));
        ReflectionTestUtils.setField(user, "userId", 101L);
        return user;
    }

    private static User lockedUser() {
        User user = activeUser();
        for (int attempt = 1; attempt <= 5; attempt++) {
            user.recordFailedLogin(
                    NOW.minusMinutes(10 - attempt),
                    5,
                    Duration.ofMinutes(15));
        }
        return user;
    }
}
