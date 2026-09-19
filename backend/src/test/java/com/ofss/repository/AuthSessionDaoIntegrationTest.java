package com.ofss.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import com.ofss.beans.AuthSession;
import com.ofss.beans.User;

@DataJpaTest
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE)
class AuthSessionDaoIntegrationTest {

    @Autowired
    private UserDao userDao;

    @Autowired
    private AuthSessionDao authSessionDao;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void persistsUsageAndIncrementsOptimisticVersion() {
        User user = createUser(
                "Session User",
                "phase22d.session@safepay.test");

        OffsetDateTime createdAt =
                OffsetDateTime.parse(
                        "2026-09-14T14:00:00Z");

        OffsetDateTime usedAt =
                createdAt.plusMinutes(1);

        AuthSession session = AuthSession.issue(
                user,
                "11111111-1111-1111-1111-111111111111",
                "hashed-refresh-token-phase22d-1",
                createdAt,
                createdAt.plusDays(7));

        authSessionDao.save(session);
        entityManager.flush();

        assertThat(session.getSessionId()).isPositive();
        assertThat(session.getVersionNo()).isZero();

        session.markUsedAt(usedAt);
        authSessionDao.save(session);

        entityManager.flush();
        entityManager.clear();

        AuthSession reloaded = authSessionDao
                .findByRefreshTokenHash(
                        "hashed-refresh-token-phase22d-1")
                .orElseThrow();

        assertThat(reloaded.getUser().getUserId())
                .isEqualTo(user.getUserId());

        assertThat(reloaded.getLastUsedAt())
                .isEqualTo(usedAt);

        assertThat(reloaded.getVersionNo())
                .isEqualTo(1);

        assertThat(reloaded.isActiveAt(
                createdAt.plusMinutes(2)))
                .isTrue();

        assertThat(
                authSessionDao.existsByRefreshTokenHash(
                        "hashed-refresh-token-phase22d-1"))
                .isTrue();
    }

    @Test
    void rotatesSessionWithinSameTokenFamily() {
        User user = createUser(
                "Rotation User",
                "phase22d.rotation@safepay.test");

        String familyKey =
                "22222222-2222-2222-2222-222222222222";

        OffsetDateTime originalCreatedAt =
                OffsetDateTime.parse(
                        "2026-09-14T15:00:00Z");

        OffsetDateTime rotationTime =
                originalCreatedAt.plusHours(1);

        AuthSession original = AuthSession.issue(
                user,
                familyKey,
                "hashed-refresh-token-original",
                originalCreatedAt,
                originalCreatedAt.plusDays(7));

        authSessionDao.save(original);
        entityManager.flush();

        AuthSession replacement = AuthSession.issue(
                user,
                familyKey,
                "hashed-refresh-token-replacement",
                rotationTime,
                rotationTime.plusDays(7));

        authSessionDao.save(replacement);
        entityManager.flush();

        Long originalId = original.getSessionId();
        Long replacementId = replacement.getSessionId();

        original.rotateTo(
                replacement,
                "ROTATED",
                rotationTime);

        authSessionDao.save(original);

        entityManager.flush();
        entityManager.clear();

        AuthSession reloadedOriginal = authSessionDao
                .findById(originalId)
                .orElseThrow();

        assertThat(reloadedOriginal.getRevokedAt())
                .isEqualTo(rotationTime);

        assertThat(reloadedOriginal.getRevocationReason())
                .isEqualTo("ROTATED");

        assertThat(
                reloadedOriginal
                        .getReplacedBySession()
                        .getSessionId())
                .isEqualTo(replacementId);

        assertThat(reloadedOriginal.isActiveAt(
                rotationTime.plusSeconds(1)))
                .isFalse();

        List<AuthSession> familySessions =
                authSessionDao
                        .findAllByTokenFamilyKeyOrderByCreatedAtAsc(
                                familyKey);

        assertThat(familySessions)
                .extracting(AuthSession::getSessionId)
                .containsExactly(
                        originalId,
                        replacementId);

        List<AuthSession> activeSessions =
                authSessionDao
                        .findAllByUser_UserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtAsc(
                                user.getUserId(),
                                rotationTime.plusSeconds(1));

        assertThat(activeSessions)
                .extracting(AuthSession::getSessionId)
                .containsExactly(replacementId);
    }

    private User createUser(
            String fullName,
            String email) {

        return userDao.save(
                User.createActiveUser(
                        fullName,
                        email,
                        null,
                        "test-only-password-hash",
                        OffsetDateTime.parse(
                                "2026-09-14T13:55:00Z")));
    }
}