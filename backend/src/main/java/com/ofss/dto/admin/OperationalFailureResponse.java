package com.ofss.dto.admin;

import java.time.OffsetDateTime;

public record OperationalFailureResponse(Source source, String recordId, String transactionId,
        String processingStage, String status, String errorCode, String displayExplanation,
        boolean retryable, int attemptCount, OffsetDateTime nextAttemptAt,
        OffsetDateTime occurredAt, OffsetDateTime resolvedAt, String correlationId) {
    public enum Source { TRANSACTION, NOTIFICATION }
}
