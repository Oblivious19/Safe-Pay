package com.ofss.security;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ofss.beans.AuditOutcome;
import com.ofss.beans.AuthSession;
import com.ofss.beans.User;
import com.ofss.beans.UserStatus;
import com.ofss.repository.AuthSessionDao;
import com.ofss.repository.UserDao;
import com.ofss.services.AuthenticationAuditService;

@Service
public class RefreshSessionServiceImpl
        implements RefreshSessionService {

    private static final String INVALID_CREDENTIALS =
            "Invalid credentials";
    private static final int TOKEN_GENERATION_ATTEMPTS = 5;

    private final AuthSessionDao authSessionDao;
    private final UserDao userDao;
    private final RefreshTokenCodec tokenCodec;
    private final AuthenticationPolicyProperties policy;
    private final AuthenticationAuditService auditService;

    public RefreshSessionServiceImpl(
            AuthSessionDao authSessionDao,
            UserDao userDao,
            RefreshTokenCodec tokenCodec,
            AuthenticationPolicyProperties policy,
            AuthenticationAuditService auditService) {
        this.authSessionDao = authSessionDao;
        this.userDao = userDao;
        this.tokenCodec = tokenCodec;
        this.policy = policy;
        this.auditService = auditService;
    }

    @Override
    @Transactional
    public RefreshSessionResult issueForLogin(
            Long userId,
            String correlationId) {

        User user = requiredLockedUser(userId);
        OffsetDateTime databaseTime = userDao.currentDatabaseTime();

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BadCredentialsException(INVALID_CREDENTIALS);
        }

        user.recordSuccessfulLogin(databaseTime);
        RefreshSessionResult result = issueSession(
                user,
                UUID.randomUUID().toString(),
                databaseTime);

        auditService.record(
                user,
                "AUTH_LOGIN_SUCCEEDED",
                AuditOutcome.SUCCESS,
                null,
                correlationId,
                databaseTime);
        return result;
    }

    @Override
    @Transactional
    public RefreshRotationResult rotate(
            String rawRefreshToken,
            String correlationId) {

        String tokenHash;
        try {
            tokenHash = tokenCodec.hash(rawRefreshToken);
        } catch (IllegalArgumentException exception) {
            auditService.record(
                    null,
                    "AUTH_REFRESH_FAILED",
                    AuditOutcome.DENIED,
                    "INVALID_REFRESH_TOKEN",
                    correlationId,
                    userDao.currentDatabaseTime());
            return RefreshRotationResult.invalid();
        }

        AuthSession located = authSessionDao
                .findByRefreshTokenHash(tokenHash)
                .orElse(null);

        if (located == null) {
            OffsetDateTime databaseTime = userDao.currentDatabaseTime();
            auditService.record(
                    null,
                    "AUTH_REFRESH_FAILED",
                    AuditOutcome.DENIED,
                    "INVALID_REFRESH_TOKEN",
                    correlationId,
                    databaseTime);
            return RefreshRotationResult.invalid();
        }

        User user = requiredLockedUser(
                located.getUser().getUserId());
        AuthSession current = authSessionDao
                .findByRefreshTokenHashForUpdate(tokenHash)
                .orElseThrow(() -> new IllegalStateException(
                        "Located refresh session disappeared"));
        OffsetDateTime databaseTime = userDao.currentDatabaseTime();

        if (current.getRevokedAt() != null) {
            if (current.getReplacedBySession() != null) {
                revokeFamilyForReplay(
                        current,
                        user,
                        correlationId,
                        databaseTime);
                return RefreshRotationResult.replayDetected();
            }
            auditService.record(
                    user,
                    "AUTH_REFRESH_FAILED",
                    AuditOutcome.DENIED,
                    "SESSION_REVOKED",
                    correlationId,
                    databaseTime);
            return RefreshRotationResult.invalid();
        }

        if (!current.isActiveAt(databaseTime)
                || user.getStatus() != UserStatus.ACTIVE) {
            if (current.getRevokedAt() == null) {
                current.revoke(
                        user.getStatus() == UserStatus.ACTIVE
                                ? "EXPIRED"
                                : "USER_UNAVAILABLE",
                        databaseTime);
            }
            auditService.record(
                    user,
                    "AUTH_REFRESH_FAILED",
                    AuditOutcome.DENIED,
                    "SESSION_UNAVAILABLE",
                    correlationId,
                    databaseTime);
            return RefreshRotationResult.invalid();
        }

        current.markUsedAt(databaseTime);
        RefreshSessionResult replacement = issueSession(
                user,
                current.getTokenFamilyKey(),
                databaseTime);

        AuthSession replacementEntity = authSessionDao
                .findByRefreshTokenHashForUpdate(
                        tokenCodec.hash(
                                replacement.rawRefreshToken()))
                .orElseThrow(() -> new IllegalStateException(
                        "Replacement session was not persisted"));

        current.rotateTo(
                replacementEntity,
                "ROTATED",
                databaseTime);

        auditService.record(
                user,
                "AUTH_REFRESH_ROTATED",
                AuditOutcome.SUCCESS,
                null,
                correlationId,
                databaseTime);
        return RefreshRotationResult.rotated(replacement);
    }

    @Override
    @Transactional
    public void logout(
            Long userId,
            String rawRefreshToken,
            String correlationId) {

        User user = requiredLockedUser(userId);
        OffsetDateTime databaseTime = userDao.currentDatabaseTime();

        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new BadCredentialsException(INVALID_CREDENTIALS);
        }

        String hash = tokenCodec.hash(rawRefreshToken);
        AuthSession session = authSessionDao
                .findByRefreshTokenHashForUpdate(hash)
                .filter(candidate -> candidate.getUser()
                        .getUserId().equals(userId))
                .filter(candidate -> candidate.isActiveAt(databaseTime))
                .orElseThrow(() -> new BadCredentialsException(
                        INVALID_CREDENTIALS));

        session.revoke("LOGOUT", databaseTime);

        auditService.record(
                user,
                "AUTH_LOGOUT",
                AuditOutcome.SUCCESS,
                null,
                correlationId,
                databaseTime);
    }

    private RefreshSessionResult issueSession(
            User user,
            String familyKey,
            OffsetDateTime databaseTime) {

        for (int attempt = 0;
                attempt < TOKEN_GENERATION_ATTEMPTS;
                attempt++) {
            String rawToken = tokenCodec.generate();
            String tokenHash = tokenCodec.hash(rawToken);

            if (authSessionDao.existsByRefreshTokenHash(tokenHash)) {
                continue;
            }

            OffsetDateTime expiresAt = databaseTime.plus(
                    policy.refreshSessionValidity());
            AuthSession session = AuthSession.issue(
                    user,
                    familyKey,
                    tokenHash,
                    databaseTime,
                    expiresAt);
            authSessionDao.save(session);
            return new RefreshSessionResult(
                    user.getUserId(),
                    rawToken,
                    expiresAt);
        }

        throw new IllegalStateException(
                "Unable to allocate a unique refresh token");
    }

    private void revokeFamilyForReplay(
            AuthSession replayedSession,
            User user,
            String correlationId,
            OffsetDateTime databaseTime) {

        List<AuthSession> family = authSessionDao.findFamilyForUpdate(
                replayedSession.getTokenFamilyKey());
        for (AuthSession session : family) {
            if (session.getRevokedAt() == null) {
                session.revoke("TOKEN_REPLAY", databaseTime);
            }
        }

        user.recordSecurityCompromise(databaseTime);
        auditService.record(
                user,
                "AUTH_REFRESH_REPLAY_DETECTED",
                AuditOutcome.DENIED,
                "TOKEN_REPLAY",
                correlationId,
                databaseTime);
    }

    private User requiredLockedUser(Long userId) {
        return userDao.findByIdForUpdate(userId)
                .orElseThrow(() -> new BadCredentialsException(
                        INVALID_CREDENTIALS));
    }
}
