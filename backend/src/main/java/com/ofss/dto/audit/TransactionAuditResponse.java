package com.ofss.dto.audit;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ofss.beans.AuditActorType;
import com.ofss.beans.AuditOutcome;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TransactionAuditResponse(
        String auditLogId,
        String eventReference,
        String actionCode,
        String previousState,
        String newState,
        AuditOutcome outcome,
        String reasonCode,
        AuditActorType actorType,
        String actorRoleCode,
        AuditSafeDetails details,
        OffsetDateTime occurredAt) {
}
