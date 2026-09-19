package com.ofss.services;
import java.time.OffsetDateTime;
import com.ofss.common.api.PagedResponse;
import com.ofss.dto.admin.OperationalDashboardResponse;
import com.ofss.dto.admin.OperationalFailureResponse;

public interface AdminOperationsService {
    OperationalDashboardResponse getStatistics(Long administratorId);
    PagedResponse<OperationalFailureResponse> searchFailures(Long administratorId,
            OperationalFailureResponse.Source source, Long transactionId,
            OffsetDateTime from, OffsetDateTime to, int page, int size);
}
