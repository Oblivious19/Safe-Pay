package com.ofss.dto.admin;

import java.time.OffsetDateTime;
import java.util.Map;
import com.ofss.beans.TransactionState;

public record OperationalDashboardResponse(OffsetDateTime observedAt, Map<TransactionState, Long> paymentsByState,
        long pendingReviews, Map<String, Long> exceptionsByStatus, Map<String, Long> notificationsByStatus,
        long expiredProtectedPayments, long dueSettlementPayments) {
    public OperationalDashboardResponse {
        paymentsByState = Map.copyOf(paymentsByState);
        exceptionsByStatus = Map.copyOf(exceptionsByStatus);
        notificationsByStatus = Map.copyOf(notificationsByStatus);
    }
}
