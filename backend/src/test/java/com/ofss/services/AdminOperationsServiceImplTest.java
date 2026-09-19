package com.ofss.services;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import com.ofss.beans.RoleName;
import com.ofss.repository.ReportingReadRepository;
import com.ofss.security.StaffReadAccess;
import com.ofss.dto.admin.OperationalFailureResponse;

class AdminOperationsServiceImplTest {
    private final StaffReadAccess access = mock(StaffReadAccess.class);
    private final ReportingReadRepository reporting = mock(ReportingReadRepository.class);
    private final AdminOperationsService service = new AdminOperationsServiceImpl(access, reporting);
    @Test void deniesReadsBeforeReportingAccess() {
        doThrow(new AccessDeniedException("denied")).when(access).requireActiveRole(10L, RoleName.SYSTEM_ADMIN);
        assertThatThrownBy(() -> service.getStatistics(10L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.searchFailures(10L, null, null, null, null, 0, 20)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(reporting);
    }
    @Test void invalidBoundsNeverReachReporting() {
        var now = OffsetDateTime.now();
        assertThatThrownBy(() -> service.searchFailures(10L, null, 0L, null, null, 0, 20)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.searchFailures(10L, null, null, now, now, 0, 20)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.searchFailures(10L, null, null, null, null, -1, 20)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.searchFailures(10L, null, null, null, null, 0, 101)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(reporting);
    }
    @Test void statisticsChecksLiveAdministratorRole() {
        service.getStatistics(10L);
        var order = inOrder(access, reporting);
        order.verify(access).requireActiveRole(10L, RoleName.SYSTEM_ADMIN);
        order.verify(reporting).statistics();
    }
    @Test void failureSearchPreservesSourceIdentityAndFilters() {
        var from = OffsetDateTime.parse("2026-09-19T00:00:00Z");
        service.searchFailures(10L, OperationalFailureResponse.Source.NOTIFICATION, 99L, from, from.plusDays(1), 1, 5);
        verify(reporting).failures(OperationalFailureResponse.Source.NOTIFICATION, 99L, from, from.plusDays(1), 1, 5);
    }
}
