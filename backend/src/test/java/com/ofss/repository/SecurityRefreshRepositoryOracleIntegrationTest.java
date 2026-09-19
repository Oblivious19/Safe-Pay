package com.ofss.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import com.ofss.beans.AuthSession;
import com.ofss.beans.User;

@DataJpaTest
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE)
class SecurityRefreshRepositoryOracleIntegrationTest {

    @Autowired private UserDao userDao;
    @Autowired private AuthSessionDao authSessionDao;

    @Test
    void locksSessionByItsStoredHash() {
        AuthSession session = createSession("security-refresh-lock-hash");
        assertThat(authSessionDao.findByRefreshTokenHashForUpdate(
                "security-refresh-lock-hash"))
                .hasValueSatisfying(found ->
                        assertThat(found.getSessionId())
                                .isEqualTo(session.getSessionId()));
    }

    @Test
    void locksTokenFamilyInCreationOrder() {
        AuthSession first = createSession("security-family-hash-1");
        AuthSession second = AuthSession.issue(
                first.getUser(),
                first.getTokenFamilyKey(),
                "security-family-hash-2",
                first.getCreatedAt().plusMinutes(1),
                first.getExpiresAt().plusMinutes(1));
        authSessionDao.save(second);

        assertThat(authSessionDao.findFamilyForUpdate(
                first.getTokenFamilyKey()))
                .extracting(AuthSession::getRefreshTokenHash)
                .containsExactly(
                        "security-family-hash-1",
                        "security-family-hash-2");
    }

    private AuthSession createSession(String hash) {
        OffsetDateTime createdAt = OffsetDateTime.parse(
                "2026-09-17T07:00:00Z");
        User user = userDao.save(User.createActiveUser(
                "Refresh Repository User",
                hash + "@safepay.test",
                null,
                "test-password-hash",
                createdAt.minusMinutes(1)));
        return authSessionDao.save(AuthSession.issue(
                user,
                "99999999-9999-9999-9999-999999999999",
                hash,
                createdAt,
                createdAt.plusDays(7)));
    }
}
