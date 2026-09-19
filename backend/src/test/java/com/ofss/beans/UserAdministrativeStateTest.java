package com.ofss.beans;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;

class UserAdministrativeStateTest {

    private static final OffsetDateTime NOW =
            OffsetDateTime.parse("2026-09-17T11:00:00Z");

    @Test
    void administrativeLockIsIndefiniteAndInvalidatesTokens() {
        User user = user();
        assertThat(user.applyAdministrativeStatus(UserStatus.LOCKED, NOW))
                .isTrue();
        assertThat(user.getStatus()).isEqualTo(UserStatus.LOCKED);
        assertThat(user.getLockedUntil()).isNull();
        assertThat(user.getSecurityVersion()).isEqualTo(1L);
    }

    @Test
    void unlockClearsTemporaryFailureState() {
        User user = user();
        for (int attempt = 0; attempt < 5; attempt++) {
            user.recordFailedLogin(
                    NOW.minusMinutes(5 - attempt),
                    5,
                    Duration.ofMinutes(15));
        }
        assertThat(user.applyAdministrativeStatus(UserStatus.ACTIVE, NOW))
                .isTrue();
        assertThat(user.getFailedLoginCount()).isZero();
        assertThat(user.getLockedUntil()).isNull();
        assertThat(user.getSecurityVersion()).isEqualTo(2L);
    }

    @Test
    void disableInvalidatesTokens() {
        User user = user();
        user.applyAdministrativeStatus(UserStatus.DISABLED, NOW);
        assertThat(user.getStatus()).isEqualTo(UserStatus.DISABLED);
        assertThat(user.getSecurityVersion()).isEqualTo(1L);
    }

    @Test
    void repeatedCanonicalStatusIsNaturallyIdempotent() {
        User user = user();
        assertThat(user.applyAdministrativeStatus(UserStatus.ACTIVE, NOW))
                .isFalse();
        assertThat(user.getSecurityVersion()).isZero();
    }

    private static User user() {
        return User.createActiveUser(
                "Administrative Target",
                "admin.target@safepay.test",
                null,
                "hash",
                NOW.minusDays(1));
    }
}
