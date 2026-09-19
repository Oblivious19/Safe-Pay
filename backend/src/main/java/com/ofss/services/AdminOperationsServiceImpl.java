package com.ofss.services;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ofss.beans.RoleName;
import com.ofss.common.api.PagedResponse;
import com.ofss.dto.admin.OperationalDashboardResponse;
import com.ofss.dto.admin.OperationalFailureResponse;
import com.ofss.repository.ReportingReadRepository;
import com.ofss.security.StaffReadAccess;

@Service
@Transactional(readOnly = true)
public class AdminOperationsServiceImpl implements AdminOperationsService {
    private final StaffReadAccess access;
    private final ReportingReadRepository reporting;
    public AdminOperationsServiceImpl(StaffReadAccess access, ReportingReadRepository reporting) {
        this.access = java.util.Objects.requireNonNull(access, "access is required");
        this.reporting = java.util.Objects.requireNonNull(reporting, "reporting is required");
    }
    @Override
    public OperationalDashboardResponse getStatistics(Long administratorId) {
        access.requireActiveRole(administratorId, RoleName.SYSTEM_ADMIN);
        return reporting.statistics();
    }
    @Override
    public PagedResponse<OperationalFailureResponse> searchFailures(Long administratorId,
            OperationalFailureResponse.Source source, Long transactionId,
            OffsetDateTime from, OffsetDateTime to, int page, int size) {
        access.requireActiveRole(administratorId, RoleName.SYSTEM_ADMIN);
        ReportingReadRepository.validateRange(transactionId, from, to);
        ReportingReadRepository.pageRequest(page, size);
        return reporting.failures(source, transactionId, from, to, page, size);
    }
}
