package com.ofss.beans;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;

class UserAuthenticationStateTest {

    private static final OffsetDateTime CREATED_AT =
            OffsetDateTime.parse("2026-09-17T06:00:00Z");

    @Test
    void recordsFailureWithoutEarlyLock() {
        User user = user();
        boolean locked = user.recordFailedLogin(
                CREATED_AT.plusMinutes(1),
                5,
                Duration.ofMinutes(15));
        assertThat(locked).isFalse();
        assertThat(user.getFailedLoginCount()).isEqualTo(1);
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void fifthFailureLocksAndInvalidatesExistingTokens() {
        User user = user();
        for (int attempt = 1; attempt <= 4; attempt++) {
            user.recordFailedLogin(
                    CREATED_AT.plusMinutes(attempt),
                    5,
                    Duration.ofMinutes(15));
        }
        boolean locked = user.recordFailedLogin(
                CREATED_AT.plusMinutes(5),
                5,
                Duration.ofMinutes(15));
        assertThat(locked).isTrue();
        assertThat(user.getStatus()).isEqualTo(UserStatus.LOCKED);
        assertThat(user.getLockedUntil())
                .isEqualTo(CREATED_AT.plusMinutes(20));
        assertThat(user.getSecurityVersion()).isEqualTo(1);
    }

    @Test
    void failureDuringActiveLockDoesNotExtendLock() {
        User user = lockedUser();
        OffsetDateTime lockedUntil = user.getLockedUntil();
        boolean newlyLocked = user.recordFailedLogin(
                CREATED_AT.plusMinutes(6),
                5,
                Duration.ofMinutes(15));
        assertThat(newlyLocked).isFalse();
        assertThat(user.getLockedUntil()).isEqualTo(lockedUntil);
        assertThat(user.getFailedLoginCount()).isEqualTo(5);
    }

    @Test
    void releasesOnlyExpiredTemporaryLock() {
        User user = lockedUser();
        assertThat(user.releaseExpiredTemporaryLock(
                user.getLockedUntil())).isTrue();
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getFailedLoginCount()).isZero();
        assertThat(user.getLockedUntil()).isNull();
    }

    @Test
    void doesNotReleaseLockBeforeDeadline() {
        User user = lockedUser();
        assertThat(user.releaseExpiredTemporaryLock(
                user.getLockedUntil().minusNanos(1))).isFalse();
        assertThat(user.getStatus()).isEqualTo(UserStatus.LOCKED);
    }

    @Test
    void successfulLoginResetsFailureState() {
        User user = user();
        user.recordFailedLogin(
                CREATED_AT.plusMinutes(1),
                5,
                Duration.ofMinutes(15));
        user.recordSuccessfulLogin(CREATED_AT.plusMinutes(2));
        assertThat(user.getFailedLoginCount()).isZero();
        assertThat(user.getLastSuccessfulLoginAt())
                .isEqualTo(CREATED_AT.plusMinutes(2));
    }

    @Test
    void refusesSuccessfulLoginForLockedUser() {
        User user = lockedUser();
        assertThatThrownBy(() -> user.recordSuccessfulLogin(
                CREATED_AT.plusMinutes(6)))
                .isInstanceOf(IllegalStateException.class);
    }

    private static User lockedUser() {
        User user = user();
        for (int attempt = 1; attempt <= 5; attempt++) {
            user.recordFailedLogin(
                    CREATED_AT.plusMinutes(attempt),
                    5,
                    Duration.ofMinutes(15));
        }
        return user;
    }

    private static User user() {
        return User.createActiveUser(
                "Security User",
                "security.user@safepay.test",
                null,
                "test-only-password-hash",
                CREATED_AT);
    }
}
