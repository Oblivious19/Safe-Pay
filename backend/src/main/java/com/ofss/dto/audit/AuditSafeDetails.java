package com.ofss.dto.audit;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuditSafeDetails(
        String reviewId,
        Integer reviewRound,
        String reviewStatus,
        String decisionReason,
        String internalNote) {
}
