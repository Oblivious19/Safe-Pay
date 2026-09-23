package com.ofss.dto.riskreview;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

/** Read-only aggregate data for the Risk Officer workbench. */
public record RiskDashboardResponse(OffsetDateTime observedAt, long pendingReviews,
        BigDecimal pendingAmount, Map<String, Long> reviewsByStatus,
        Map<String, Long> paymentsByRiskTier) {
    public RiskDashboardResponse {
        pendingAmount = pendingAmount == null ? BigDecimal.ZERO : pendingAmount;
        reviewsByStatus = Map.copyOf(reviewsByStatus);
        paymentsByRiskTier = Map.copyOf(paymentsByRiskTier);
    }
}
