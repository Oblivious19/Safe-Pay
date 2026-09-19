package com.ofss.dto.audit;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public final class AuditReconciliationResponse {
    private AuditReconciliationResponse() {}
    public record Ledger(String postingId, String postingReference, String postingType, String transactionId,
            String postingStatus, BigDecimal postingAmount, String currencyCode, long expectedEntryCount,
            long actualEntryCount, long debitCount, long creditCount, BigDecimal debitTotal,
            BigDecimal creditTotal, long distinctAccountCount, String reconciliationStatus,
            OffsetDateTime createdAt, OffsetDateTime postedAt, OffsetDateTime updatedAt) {}
    public record Reservation(String accountId, String maskedAccountNumber, String currencyCode,
            BigDecimal storedReservedAmount, BigDecimal calculatedReservedAmount, BigDecimal reservationDifference,
            String reconciliationStatus, OffsetDateTime updatedAt) {}
}
