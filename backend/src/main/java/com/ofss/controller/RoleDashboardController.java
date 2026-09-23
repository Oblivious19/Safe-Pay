package com.ofss.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ofss.beans.RoleName;
import com.ofss.dto.audit.AuditDashboardResponse;
import com.ofss.dto.riskreview.RiskDashboardResponse;
import com.ofss.repository.ReportingReadRepository;
import com.ofss.security.AuthenticatedUser;
import com.ofss.security.StaffReadAccess;

/** Role-protected, read-only aggregate data for future dashboard visualisations. */
@RestController
@RequestMapping("/api/v1")
public class RoleDashboardController {
    private final ReportingReadRepository reporting;
    private final StaffReadAccess access;

    public RoleDashboardController(ReportingReadRepository reporting, StaffReadAccess access) {
        this.reporting = java.util.Objects.requireNonNull(reporting, "reporting is required");
        this.access = java.util.Objects.requireNonNull(access, "access is required");
    }

    @GetMapping("/risk/dashboard")
    @PreAuthorize("hasAuthority('RISK_OFFICER')")
    public RiskDashboardResponse risk(Authentication authentication) {
        access.requireActiveRole(AuthenticatedUser.userId(authentication), RoleName.RISK_OFFICER);
        return reporting.riskDashboard();
    }

    @GetMapping("/audit/dashboard")
    @PreAuthorize("hasAuthority('AUDITOR')")
    public AuditDashboardResponse audit(Authentication authentication) {
        access.requireActiveRole(AuthenticatedUser.userId(authentication), RoleName.AUDITOR);
        return reporting.auditDashboard();
    }
}
