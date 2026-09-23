package com.ofss.dto.audit;

import java.time.OffsetDateTime;
import java.util.Map;

/** Read-only aggregate data for the Auditor dashboard. */
public record AuditDashboardResponse(OffsetDateTime observedAt,
        Map<String, Long> ledgerReconciliation, Map<String, Long> reservationReconciliation,
        Map<String, Long> exceptionsByStatus) {
    public AuditDashboardResponse {
        ledgerReconciliation = Map.copyOf(ledgerReconciliation);
        reservationReconciliation = Map.copyOf(reservationReconciliation);
        exceptionsByStatus = Map.copyOf(exceptionsByStatus);
    }
}
