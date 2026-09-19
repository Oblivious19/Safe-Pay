package com.ofss.services;

import java.time.OffsetDateTime;
import java.util.Set;

import com.ofss.beans.RoleName;
import com.ofss.common.api.PagedResponse;
import com.ofss.dto.audit.AuditLogResponse;
import com.ofss.dto.audit.TransactionAuditResponse;

public interface AuditQueryService {

    PagedResponse<AuditLogResponse> searchGlobalAudit(
            Long auditorUserId,
            Long transactionId,
            String actionCode,
            String outcome,
            String actorType,
            String correlationId,
            OffsetDateTime from,
            OffsetDateTime to,
            int page,
            int size);

    PagedResponse<TransactionAuditResponse> getTransactionTimeline(
            Long requestingUserId,
            Set<RoleName> authorities,
            Long transactionId,
            int page,
            int size);
}
