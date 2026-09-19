package com.ofss.dto.audit;

import java.time.OffsetDateTime;
import com.ofss.beans.TransactionExceptionLog;
import com.ofss.beans.TransactionExceptionStatus;
import com.ofss.beans.TransactionProcessingStage;

public record AuditExceptionResponse(String exceptionId, String exceptionReference, String transactionId,
        String postingId, TransactionProcessingStage processingStage, String errorCode, String displayExplanation,
        boolean retryable, TransactionExceptionStatus status, int retryCount, OffsetDateTime nextRetryAt,
        OffsetDateTime firstOccurredAt, OffsetDateTime lastOccurredAt, OffsetDateTime resolvedAt,
        String resolvedByUserId, String correlationId) {
    public static AuditExceptionResponse from(TransactionExceptionLog e) {
        return new AuditExceptionResponse(e.getTransactionExceptionId().toString(), e.getExceptionReference(),
                e.getTransaction() == null ? null : e.getTransaction().getTransactionId().toString(),
                e.getPosting() == null ? null : e.getPosting().getPostingId().toString(),
                e.getProcessingStage(), e.getErrorCode(), safeExplanation(e.getErrorCode()), e.isRetryable(),
                e.getStatus(), e.getRetryCount(), e.getNextRetryAt(), e.getFirstOccurredAt(), e.getLastOccurredAt(),
                e.getResolvedAt(), e.getResolvedByUser() == null ? null : e.getResolvedByUser().getUserId().toString(),
                e.getCorrelationId());
    }
    // Controlled text only; never copy exception messages or resolution notes into reporting.
    public static String safeExplanation(String code) {
        if (code == null) return "Processing issue recorded.";
        return switch (code) {
            case "SOURCE_ACCOUNT_INACTIVE", "ACCOUNT_INACTIVE" -> "The source account is inactive.";
            case "INVALID_SETTLEMENT_SOURCE", "CUSTOMER_ACCOUNT_REQUIRED" -> "The source account is not eligible.";
            case "SETTLEMENT_CURRENCY_MISMATCH" -> "Settlement currencies do not match.";
            default -> "Processing issue recorded; consult authorized operational evidence.";
        };
    }
}
