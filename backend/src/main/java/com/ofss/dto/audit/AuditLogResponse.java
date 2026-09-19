package com.ofss.dto.audit;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ofss.beans.AuditActorType;
import com.ofss.beans.AuditOutcome;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuditLogResponse(
        String auditLogId,
        String eventReference,
        String actorUserId,
        AuditActorType actorType,
        String actorRoleCode,
        String actionCode,
        String entityType,
        String entityId,
        String transactionId,
        String previousState,
        String newState,
        AuditOutcome outcome,
        String reasonCode,
        String correlationId,
        AuditSafeDetails details,
        OffsetDateTime occurredAt) {
}
