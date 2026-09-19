package com.ofss.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import com.ofss.beans.User;

@DataJpaTest
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE)
class SecurityUserRepositoryOracleIntegrationTest {

    @Autowired private UserDao userDao;

    @Test
    void locksUserByNormalizedEmailForLoginMutation() {
        User user = userDao.save(User.createActiveUser(
                "Security Repository User",
                "security.repo@safepay.test",
                null,
                "test-password-hash",
                OffsetDateTime.parse("2026-09-17T07:00:00Z")));

        assertThat(userDao.findByLoginIdentifierForUpdate(
                "security.repo@safepay.test"))
                .hasValueSatisfying(found ->
                        assertThat(found.getUserId())
                                .isEqualTo(user.getUserId()));
    }

    @Test
    void obtainsOracleAuthoritativeTimestamp() {
        assertThat(userDao.currentDatabaseTime()).isNotNull();
    }
}
