package com.ofss.beans;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AuthenticationAuditModelTest {

    private static final OffsetDateTime NOW =
            OffsetDateTime.parse("2026-09-17T12:00:00Z");

    @Test
    void knownUserAuthenticationEventUsesUserActorBoundary() {
        User user = user();
        AuditLog event = AuditLog.authenticationEvent(
                "auth-event-1",
                user,
                "auth_login_succeeded",
                AuditOutcome.SUCCESS,
                null,
                "auth-audit-test",
                NOW);

        assertThat(event.getActorType()).isEqualTo(AuditActorType.USER);
        assertThat(event.getActorUser()).isSameAs(user);
        assertThat(event.getEntityType()).isEqualTo("APP_USER");
        assertThat(event.getEntityId()).isEqualTo(101L);
    }

    @Test
    void unknownCredentialFailureUsesSystemActorWithoutIdentifier() {
        AuditLog event = AuditLog.authenticationEvent(
                "auth-event-2",
                null,
                "AUTH_LOGIN_FAILED",
                AuditOutcome.DENIED,
                "INVALID_CREDENTIALS",
                "auth-audit-test",
                NOW);

        assertThat(event.getActorType()).isEqualTo(AuditActorType.SYSTEM);
        assertThat(event.getActorUser()).isNull();
        assertThat(event.getEntityType()).isEqualTo("AUTHENTICATION");
        assertThat(event.getEntityId()).isNull();
    }

    @Test
    void authenticationEvidenceContainsNoCredentialDetails() {
        AuditLog event = AuditLog.authenticationEvent(
                "auth-event-3",
                user(),
                "AUTH_REFRESH_ROTATED",
                AuditOutcome.SUCCESS,
                null,
                "auth-audit-test",
                NOW);

        assertThat(event.getDetailsJson()).isNull();
        assertThat(event.getIdempotencyKey()).isNull();
    }

    private static User user() {
        User user = User.createActiveUser(
                "Audit User",
                "audit.user@safepay.test",
                null,
                "test-password-hash",
                NOW.minusDays(1));
        ReflectionTestUtils.setField(user, "userId", 101L);
        return user;
    }
}
