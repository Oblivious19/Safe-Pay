package com.ofss.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.util.ReflectionTestUtils;

import com.ofss.beans.AuditOutcome;
import com.ofss.beans.AuthSession;
import com.ofss.beans.User;
import com.ofss.repository.AuthSessionDao;
import com.ofss.repository.UserDao;
import com.ofss.services.AuthenticationAuditService;

@ExtendWith(MockitoExtension.class)
class RefreshSessionServiceImplTest {

    private static final OffsetDateTime NOW =
            OffsetDateTime.parse("2026-09-17T10:00:00Z");
    private static final String CORRELATION = "refresh-test";

    @Mock private AuthSessionDao authSessionDao;
    @Mock private UserDao userDao;
    @Mock private RefreshTokenCodec tokenCodec;
    @Mock private AuthenticationAuditService auditService;

    private RefreshSessionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RefreshSessionServiceImpl(
                authSessionDao,
                userDao,
                tokenCodec,
                new AuthenticationPolicyProperties(
                        5,
                        Duration.ofMinutes(15),
                        Duration.ofDays(7)),
                auditService);
    }

    @Test
    void loginStoresOnlyHashAndReturnsRawRefreshToken() {
        User user = user();
        when(userDao.findByIdForUpdate(101L))
                .thenReturn(Optional.of(user));
        when(userDao.currentDatabaseTime()).thenReturn(NOW);
        when(tokenCodec.generate()).thenReturn("raw-token");
        when(tokenCodec.hash("raw-token")).thenReturn("token-hash");
        when(authSessionDao.existsByRefreshTokenHash("token-hash"))
                .thenReturn(false);

        RefreshSessionResult result = service.issueForLogin(
                101L,
                CORRELATION);

        assertThat(result.rawRefreshToken()).isEqualTo("raw-token");
        assertThat(result.expiresAt()).isEqualTo(NOW.plusDays(7));
        verify(authSessionDao).save(any(AuthSession.class));
        verify(auditService).record(
                user,
                "AUTH_LOGIN_SUCCEEDED",
                AuditOutcome.SUCCESS,
                null,
                CORRELATION,
                NOW);
    }

    @Test
    void refusesLoginSessionForUnavailableUser() {
        User user = user();
        ReflectionTestUtils.setField(
                user,
                "status",
                com.ofss.beans.UserStatus.DISABLED);
        when(userDao.findByIdForUpdate(101L))
                .thenReturn(Optional.of(user));
        when(userDao.currentDatabaseTime()).thenReturn(NOW);

        assertThatThrownBy(() -> service.issueForLogin(101L, CORRELATION))
                .isInstanceOf(BadCredentialsException.class);
        verify(authSessionDao, never()).save(any());
    }

    @Test
    void rejectsUnknownRefreshTokenWithoutCreatingSession() {
        when(tokenCodec.hash("unknown")).thenReturn("unknown-hash");
        when(authSessionDao.findByRefreshTokenHash("unknown-hash"))
                .thenReturn(Optional.empty());
        when(userDao.currentDatabaseTime()).thenReturn(NOW);

        RefreshRotationResult result = service.rotate(
                "unknown",
                CORRELATION);

        assertThat(result.status())
                .isEqualTo(RefreshRotationStatus.INVALID);
        verify(authSessionDao, never()).save(any());
    }

    @Test
    void missingRefreshTokenIsAuditedWithoutDatabaseLookup() {
        when(tokenCodec.hash(null))
                .thenThrow(new IllegalArgumentException(
                        "refreshToken is required"));
        when(userDao.currentDatabaseTime()).thenReturn(NOW);

        RefreshRotationResult result = service.rotate(null, CORRELATION);

        assertThat(result.status())
                .isEqualTo(RefreshRotationStatus.INVALID);
        verify(auditService).record(
                null,
                "AUTH_REFRESH_FAILED",
                AuditOutcome.DENIED,
                "INVALID_REFRESH_TOKEN",
                CORRELATION,
                NOW);
        verify(authSessionDao, never())
                .findByRefreshTokenHash(any());
    }

    @Test
    void rotatesActiveSessionInSameFamily() {
        User user = user();
        AuthSession current = session(
                user,
                "family-key",
                "old-hash",
                NOW.minusDays(1),
                NOW.plusDays(6),
                501L);
        AtomicReference<AuthSession> replacement = new AtomicReference<>();

        when(tokenCodec.hash("old-raw")).thenReturn("old-hash");
        when(tokenCodec.generate()).thenReturn("new-raw");
        when(tokenCodec.hash("new-raw")).thenReturn("new-hash");
        when(authSessionDao.findByRefreshTokenHash("old-hash"))
                .thenReturn(Optional.of(current));
        when(userDao.findByIdForUpdate(101L))
                .thenReturn(Optional.of(user));
        when(authSessionDao.findByRefreshTokenHashForUpdate("old-hash"))
                .thenReturn(Optional.of(current));
        when(userDao.currentDatabaseTime()).thenReturn(NOW);
        when(authSessionDao.existsByRefreshTokenHash("new-hash"))
                .thenReturn(false);
        when(authSessionDao.save(any(AuthSession.class)))
                .thenAnswer(invocation -> {
                    AuthSession saved = invocation.getArgument(0);
                    ReflectionTestUtils.setField(saved, "sessionId", 502L);
                    replacement.set(saved);
                    return saved;
                });
        when(authSessionDao.findByRefreshTokenHashForUpdate("new-hash"))
                .thenAnswer(invocation -> Optional.of(replacement.get()));

        RefreshRotationResult result = service.rotate(
                "old-raw",
                CORRELATION);

        assertThat(result.status())
                .isEqualTo(RefreshRotationStatus.ROTATED);
        assertThat(result.session().rawRefreshToken())
                .isEqualTo("new-raw");
        assertThat(current.getRevocationReason()).isEqualTo("ROTATED");
        assertThat(current.getReplacedBySession().getSessionId())
                .isEqualTo(502L);
    }

    @Test
    void replayRevokesFamilyAndIncrementsSecurityVersion() {
        User user = user();
        AuthSession original = session(
                user, "family-key", "old-hash",
                NOW.minusDays(1), NOW.plusDays(6), 501L);
        AuthSession replacement = session(
                user, "family-key", "new-hash",
                NOW.minusHours(1), NOW.plusDays(7), 502L);
        original.rotateTo(replacement, "ROTATED", NOW.minusHours(1));

        when(tokenCodec.hash("old-raw")).thenReturn("old-hash");
        when(authSessionDao.findByRefreshTokenHash("old-hash"))
                .thenReturn(Optional.of(original));
        when(userDao.findByIdForUpdate(101L))
                .thenReturn(Optional.of(user));
        when(authSessionDao.findByRefreshTokenHashForUpdate("old-hash"))
                .thenReturn(Optional.of(original));
        when(userDao.currentDatabaseTime()).thenReturn(NOW);
        when(authSessionDao.findFamilyForUpdate("family-key"))
                .thenReturn(List.of(original, replacement));

        RefreshRotationResult result = service.rotate(
                "old-raw",
                CORRELATION);

        assertThat(result.status())
                .isEqualTo(RefreshRotationStatus.REPLAY_DETECTED);
        assertThat(replacement.getRevocationReason())
                .isEqualTo("TOKEN_REPLAY");
        assertThat(user.getSecurityVersion()).isEqualTo(1L);
    }

    @Test
    void expiredSessionIsRevokedAndRejected() {
        User user = user();
        AuthSession expired = session(
                user, "family-key", "old-hash",
                NOW.minusDays(8), NOW.minusDays(1), 501L);
        prepareLocatedSession(user, expired);

        RefreshRotationResult result = service.rotate(
                "old-raw",
                CORRELATION);

        assertThat(result.status())
                .isEqualTo(RefreshRotationStatus.INVALID);
        assertThat(expired.getRevocationReason()).isEqualTo("EXPIRED");
    }

    @Test
    void logoutRevokesOnlyOwnedActiveSession() {
        User user = user();
        AuthSession session = session(
                user, "family-key", "hash",
                NOW.minusDays(1), NOW.plusDays(6), 501L);
        when(userDao.findByIdForUpdate(101L))
                .thenReturn(Optional.of(user));
        when(userDao.currentDatabaseTime()).thenReturn(NOW);
        when(tokenCodec.hash("raw")).thenReturn("hash");
        when(authSessionDao.findByRefreshTokenHashForUpdate("hash"))
                .thenReturn(Optional.of(session));

        service.logout(101L, "raw", CORRELATION);

        assertThat(session.getRevocationReason()).isEqualTo("LOGOUT");
        verify(auditService).record(
                user,
                "AUTH_LOGOUT",
                AuditOutcome.SUCCESS,
                null,
                CORRELATION,
                NOW);
    }

    @Test
    void logoutRejectsMissingCookieRatherThanLeavingSessionActive() {
        User user = user();
        when(userDao.findByIdForUpdate(101L))
                .thenReturn(Optional.of(user));
        when(userDao.currentDatabaseTime()).thenReturn(NOW);

        assertThatThrownBy(() -> service.logout(
                101L, null, CORRELATION))
                .isInstanceOf(BadCredentialsException.class);
        verify(auditService, never()).record(
                any(), any(), any(), any(), any(), any());
    }

    private void prepareLocatedSession(
            User user,
            AuthSession session) {
        when(tokenCodec.hash("old-raw")).thenReturn("old-hash");
        when(authSessionDao.findByRefreshTokenHash("old-hash"))
                .thenReturn(Optional.of(session));
        when(userDao.findByIdForUpdate(101L))
                .thenReturn(Optional.of(user));
        when(authSessionDao.findByRefreshTokenHashForUpdate("old-hash"))
                .thenReturn(Optional.of(session));
        when(userDao.currentDatabaseTime()).thenReturn(NOW);
    }

    private static AuthSession session(
            User user,
            String family,
            String hash,
            OffsetDateTime created,
            OffsetDateTime expires,
            Long id) {
        AuthSession session = AuthSession.issue(
                user, family, hash, created, expires);
        ReflectionTestUtils.setField(session, "sessionId", id);
        return session;
    }

    private static User user() {
        User user = User.createActiveUser(
                "Refresh User",
                "refresh.user@safepay.test",
                null,
                "test-password-hash",
                NOW.minusDays(10));
        ReflectionTestUtils.setField(user, "userId", 101L);
        return user;
    }
}
