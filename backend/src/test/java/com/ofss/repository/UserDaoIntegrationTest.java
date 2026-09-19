package com.ofss.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import com.ofss.beans.User;
import com.ofss.beans.UserStatus;

@DataJpaTest
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE)
class UserDaoIntegrationTest {

    @Autowired
    private UserDao userDao;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void directorySearchEscapesWildcardsAndPaginatesWithMatchingCount() {
        var now = OffsetDateTime.parse("2026-09-19T10:00:00Z");
        User first = userDao.save(User.createActiveUser("Directory%_! A", "directory-a@safepay.test", null, "test-hash", now));
        User second = userDao.save(User.createActiveUser("Directory%_! B", "directory-b@safepay.test", null, "test-hash", now));
        userDao.save(User.createActiveUser("Directory other", "directory-c@safepay.test", null, "test-hash", now));
        entityManager.flush();
        var page = userDao.searchUsers("%directory!%!_!!%", null, UserStatus.ACTIVE,
                org.springframework.data.domain.PageRequest.of(0, 1));
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).extracting(User::getUserId).containsExactly(first.getUserId());
        assertThat(userDao.searchUsers("%directory!%!_!!%", null, UserStatus.ACTIVE,
                org.springframework.data.domain.PageRequest.of(1, 1)).getContent())
                .extracting(User::getUserId).containsExactly(second.getUserId());
        assertThat(userDao.searchUsers("%directory!%!_!!%", null, UserStatus.DISABLED,
                org.springframework.data.domain.PageRequest.of(0, 1))).isEmpty();
    }

    @Test
    void persistsAndReloadsUserThroughOracleSequence() {
        OffsetDateTime createdAt =
                OffsetDateTime.parse("2026-09-14T12:00:00Z");

        User user = User.createActiveUser(
                "Phase Two User",
                "phase22.user@safepay.test",
                "+919000000001",
                "test-only-password-hash",
                createdAt);

        User savedUser = userDao.save(user);

        entityManager.flush();
        entityManager.clear();

        User reloadedUser = userDao
                .findById(savedUser.getUserId())
                .orElseThrow();

        assertThat(reloadedUser.getUserId()).isPositive();
        assertThat(reloadedUser.getFullName())
                .isEqualTo("Phase Two User");
        assertThat(reloadedUser.getEmail())
                .isEqualTo("phase22.user@safepay.test");
        assertThat(reloadedUser.getMobileNumber())
                .isEqualTo("+919000000001");
        assertThat(reloadedUser.getStatus())
                .isEqualTo(UserStatus.ACTIVE);
        assertThat(reloadedUser.getFailedLoginCount())
                .isZero();
        assertThat(reloadedUser.getSecurityVersion())
                .isZero();
        assertThat(reloadedUser.getVersionNo())
                .isZero();
        assertThat(reloadedUser.getCreatedAt())
                .isEqualTo(createdAt);
        assertThat(reloadedUser.getUpdatedAt())
                .isEqualTo(createdAt);
    }

    @Test
    void normalizesEmailAndSupportsEmailLookup() {
        User user = User.createActiveUser(
                "  Normalized User  ",
                "  NORMALIZED.USER@SAFEPAY.TEST  ",
                null,
                "test-only-password-hash",
                OffsetDateTime.parse(
                        "2026-09-14T12:05:00Z"));

        userDao.save(user);

        entityManager.flush();
        entityManager.clear();

        User reloadedUser = userDao
                .findByEmail(
                        "normalized.user@safepay.test")
                .orElseThrow();

        assertThat(reloadedUser.getFullName())
                .isEqualTo("Normalized User");

        assertThat(reloadedUser.getEmail())
                .isEqualTo(
                        "normalized.user@safepay.test");

        assertThat(reloadedUser.getMobileNumber())
                .isNull();

        assertThat(
                userDao.existsByEmail(
                        "normalized.user@safepay.test"))
                .isTrue();
    }
}
