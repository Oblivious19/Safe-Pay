package com.ofss.beans;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AdministrativeAuditModelTest {

    private static final OffsetDateTime NOW =
            OffsetDateTime.parse("2026-09-17T11:00:00Z");

    @Test
    void recordsSystemAdministratorAndTargetWithoutSecrets() {
        AuditLog event = AuditLog.administrativeUserEvent(
                "event-reference",
                user(10L, "admin@safepay.test"),
                user(20L, "target@safepay.test"),
                "user_role_assigned",
                AuditOutcome.SUCCESS,
                "role_auditor",
                "correlation",
                NOW);
        assertThat(event.getActorRoleCode())
                .isEqualTo(RoleName.SYSTEM_ADMIN.name());
        assertThat(event.getEntityType()).isEqualTo("APP_USER");
        assertThat(event.getEntityId()).isEqualTo(20L);
        assertThat(event.getActionCode()).isEqualTo("USER_ROLE_ASSIGNED");
        assertThat(event.getDetailsJson()).isNull();
    }

    @Test
    void administrativeEvidenceIsAUserAuthoredSuccess() {
        AuditLog event = AuditLog.administrativeUserEvent(
                "event-reference",
                user(10L, "admin@safepay.test"),
                user(20L, "target@safepay.test"),
                "auth_sessions_revoked",
                AuditOutcome.SUCCESS,
                "admin_request",
                "correlation",
                NOW);
        assertThat(event.getActorType()).isEqualTo(AuditActorType.USER);
        assertThat(event.getOutcome()).isEqualTo(AuditOutcome.SUCCESS);
        assertThat(event.getReasonCode()).isEqualTo("ADMIN_REQUEST");
    }

    private static User user(Long id, String email) {
        User user = User.createActiveUser(
                "Audit User",
                email,
                null,
                "hash",
                NOW.minusDays(1));
        ReflectionTestUtils.setField(user, "userId", id);
        return user;
    }
}
