package com.ofss.controller;

import java.time.OffsetDateTime;
import java.util.Set;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ofss.beans.RoleName;
import com.ofss.common.api.PagedResponse;
import com.ofss.dto.audit.AuditLogResponse;
import com.ofss.dto.audit.TransactionAuditResponse;
import com.ofss.security.AuthenticatedUser;
import com.ofss.services.AuditQueryService;

@RestController
@RequestMapping("/api/v1")
public class AuditController {

    private final AuditQueryService auditQueryService;

    public AuditController(AuditQueryService auditQueryService) {
        this.auditQueryService = java.util.Objects.requireNonNull(
                auditQueryService,
                "auditQueryService is required");
    }

    @GetMapping("/audit-logs")
    @PreAuthorize("hasAuthority('AUDITOR')")
    public ResponseEntity<PagedResponse<AuditLogResponse>> search(
            Authentication authentication,
            @RequestParam(required = false) Long transactionId,
            @RequestParam(required = false) String actionCode,
            @RequestParam(required = false) String outcome,
            @RequestParam(required = false) String actorType,
            @RequestParam(required = false) String correlationId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    OffsetDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    OffsetDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PrincipalAccess access = principalAccess(authentication);
        if (!access.roles().contains(RoleName.AUDITOR)) {
            throw new AccessDeniedException(
                    "AUDITOR authority is required");
        }

        return ResponseEntity.ok(
                auditQueryService.searchGlobalAudit(
                        access.userId(),
                        transactionId,
                        actionCode,
                        outcome,
                        actorType,
                        correlationId,
                        from,
                        to,
                        page,
                        size));
    }

    @GetMapping("/transactions/{transactionId}/audit")
    @PreAuthorize("hasAnyAuthority('CUSTOMER','RISK_OFFICER','AUDITOR')")
    public ResponseEntity<PagedResponse<TransactionAuditResponse>> timeline(
            Authentication authentication,
            @PathVariable("transactionId") Long transactionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PrincipalAccess access = principalAccess(authentication);
        return ResponseEntity.ok(
                auditQueryService.getTransactionTimeline(
                        access.userId(),
                        access.roles(),
                        transactionId,
                        page,
                        size));
    }

    private static PrincipalAccess principalAccess(
            Authentication authentication) {
        return new PrincipalAccess(
                AuthenticatedUser.userId(authentication),
                AuthenticatedUser.roles(authentication));
    }

    private record PrincipalAccess(
            Long userId,
            Set<RoleName> roles) {
    }
}
