package com.ofss.controller;
import java.time.OffsetDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.ofss.common.api.PagedResponse;
import com.ofss.dto.admin.OperationalDashboardResponse;
import com.ofss.dto.admin.OperationalFailureResponse;
import com.ofss.security.AuthenticatedUser;
import com.ofss.services.AdminOperationsService;

@RestController
@RequestMapping("/api/v1/admin/operations")
@PreAuthorize("hasAuthority('SYSTEM_ADMIN')")
public class AdminOperationsController {
    private final AdminOperationsService service;
    public AdminOperationsController(AdminOperationsService service) {
        this.service = java.util.Objects.requireNonNull(service, "service is required");
    }
    @GetMapping("/stats")
    public OperationalDashboardResponse getStatistics(Authentication authentication) {
        return service.getStatistics(AuthenticatedUser.userId(authentication));
    }
    @GetMapping("/failures")
    public PagedResponse<OperationalFailureResponse> searchFailures(Authentication authentication,
            @RequestParam(required = false) OperationalFailureResponse.Source source,
            @RequestParam(required = false) Long transactionId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.searchFailures(AuthenticatedUser.userId(authentication), source, transactionId, from, to, page, size);
    }
}
