package com.ofss.controller;

import java.time.OffsetDateTime;
import java.util.Objects;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ofss.beans.RiskReviewStatus;
import com.ofss.beans.TransactionState;
import com.ofss.common.api.PagedResponse;
import com.ofss.dto.audit.AuditTransactionDetailResponse;
import com.ofss.dto.riskreview.RiskReviewDetailResponse;
import com.ofss.dto.riskreview.RiskReviewSummaryResponse;
import com.ofss.dto.transaction.TransactionSummaryResponse;
import com.ofss.security.AuthenticatedUser;
import com.ofss.services.AuditEvidenceService;
import com.ofss.beans.*;
import com.ofss.dto.audit.*;

@RestController
@RequestMapping("/api/v1/audit")
@PreAuthorize("hasAuthority('AUDITOR')")
public class AuditEvidenceController {

    @GetMapping("/ledger-postings")
    public PagedResponse<AuditLedgerPostingResponse.Header> searchLedgerPostings(Authentication authentication,
            @RequestParam(required = false) Long transactionId,
            @RequestParam(required = false) LedgerPostingStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.searchLedgerPostings(AuthenticatedUser.userId(authentication), transactionId, status, from, to, page, size);
    }

    @GetMapping("/ledger-postings/{postingId}")
    public AuditLedgerPostingResponse getLedgerPosting(Authentication authentication, @PathVariable Long postingId) {
        return service.getLedgerPosting(AuthenticatedUser.userId(authentication), postingId);
    }

    @GetMapping("/reconciliation/ledger")
    public PagedResponse<AuditReconciliationResponse.Ledger> ledgerReconciliation(Authentication authentication,
            @RequestParam(required = false) Long postingId, @RequestParam(required = false) String reconciliationStatus,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.ledgerReconciliation(AuthenticatedUser.userId(authentication), postingId, reconciliationStatus, page, size);
    }

    @GetMapping("/reconciliation/reservations")
    public PagedResponse<AuditReconciliationResponse.Reservation> reservationReconciliation(Authentication authentication,
            @RequestParam(required = false) Long accountId, @RequestParam(required = false) String reconciliationStatus,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.reservationReconciliation(AuthenticatedUser.userId(authentication), accountId, reconciliationStatus, page, size);
    }

    @GetMapping("/exceptions")
    public PagedResponse<AuditExceptionResponse> searchExceptions(Authentication authentication,
            @RequestParam(required = false) Long transactionId,
            @RequestParam(required = false) TransactionProcessingStage processingStage,
            @RequestParam(required = false) TransactionExceptionStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.searchExceptions(AuthenticatedUser.userId(authentication), transactionId, processingStage, status, from, to, page, size);
    }

    @GetMapping("/exceptions/{exceptionId}")
    public AuditExceptionResponse getException(Authentication authentication, @PathVariable Long exceptionId) {
        return service.getException(AuthenticatedUser.userId(authentication), exceptionId);
    }

    @GetMapping("/risk-policies")
    public PagedResponse<AuditRiskPolicyResponse.Header> listPolicies(Authentication authentication,
            @RequestParam(required = false) RiskPolicyStatus status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.listPolicies(AuthenticatedUser.userId(authentication), status, page, size);
    }

    @GetMapping("/risk-policies/{policyVersion}")
    public AuditRiskPolicyResponse getPolicy(Authentication authentication, @PathVariable String policyVersion) {
        return service.getPolicy(AuthenticatedUser.userId(authentication), policyVersion);
    }
    private final AuditEvidenceService service;

    public AuditEvidenceController(AuditEvidenceService service) {
        this.service = Objects.requireNonNull(service, "service is required");
    }

    @GetMapping("/risk-reviews")
    public PagedResponse<RiskReviewSummaryResponse> listReviews(Authentication authentication,
            @RequestParam(defaultValue = "PENDING") RiskReviewStatus status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.listReviews(AuthenticatedUser.userId(authentication), status, page, size);
    }

    @GetMapping("/risk-reviews/{reviewId}")
    public RiskReviewDetailResponse getReview(Authentication authentication, @PathVariable("reviewId") Long reviewId) {
        return service.getReview(AuthenticatedUser.userId(authentication), reviewId);
    }

    @GetMapping("/transactions")
    public PagedResponse<TransactionSummaryResponse> searchTransactions(Authentication authentication,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) TransactionState state,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.searchTransactions(AuthenticatedUser.userId(authentication), customerId, state, from, to, page, size);
    }

    @GetMapping("/transactions/{transactionId}")
    public AuditTransactionDetailResponse getTransactionEvidence(Authentication authentication,
            @PathVariable("transactionId") Long transactionId) {
        return service.getTransactionEvidence(AuthenticatedUser.userId(authentication), transactionId);
    }
}
