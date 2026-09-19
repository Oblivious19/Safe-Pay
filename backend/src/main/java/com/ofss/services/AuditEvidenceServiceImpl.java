package com.ofss.services;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ofss.beans.RiskReviewStatus;
import com.ofss.beans.RoleName;
import com.ofss.beans.TransactionDb;
import com.ofss.beans.TransactionRiskFactor;
import com.ofss.beans.TransactionState;
import com.ofss.common.api.PagedResponse;
import com.ofss.dto.audit.AuditTransactionDetailResponse;
import com.ofss.dto.riskreview.RiskReviewDetailResponse;
import com.ofss.dto.riskreview.RiskReviewSummaryResponse;
import com.ofss.dto.transaction.TransactionResponse;
import com.ofss.dto.transaction.TransactionRiskExplanationResponse;
import com.ofss.dto.transaction.TransactionSummaryResponse;
import com.ofss.excp.ResourceNotFoundExcp;
import com.ofss.repository.RiskReviewDao;
import com.ofss.repository.TransactionDao;
import com.ofss.repository.TransactionRiskFactorDao;
import com.ofss.security.StaffReadAccess;
import com.ofss.beans.*;
import com.ofss.dto.audit.*;
import com.ofss.repository.*;

@Service
@Transactional(readOnly = true)
public class AuditEvidenceServiceImpl implements AuditEvidenceService {
    private final StaffReadAccess access;
    private final RiskReviewDao reviews;
    private final TransactionDao transactions;
    private final TransactionRiskFactorDao factors;
    private final LedgerPostingDao postings;
    private final LedgerEntryDao entries;
    private final TransactionExceptionDao exceptions;
    private final RiskPolicyDao policies;
    private final RiskPolicyBandDao bands;
    private final ReportingReadRepository reporting;

    public AuditEvidenceServiceImpl(StaffReadAccess access, RiskReviewDao reviews,
            TransactionDao transactions, TransactionRiskFactorDao factors,
            LedgerPostingDao postings, LedgerEntryDao entries, TransactionExceptionDao exceptions,
            RiskPolicyDao policies, RiskPolicyBandDao bands, ReportingReadRepository reporting) {
        this.access = Objects.requireNonNull(access, "access is required");
        this.reviews = Objects.requireNonNull(reviews, "reviews is required");
        this.transactions = Objects.requireNonNull(transactions, "transactions is required");
        this.factors = Objects.requireNonNull(factors, "factors is required");
        this.postings = Objects.requireNonNull(postings, "postings is required");
        this.entries = Objects.requireNonNull(entries, "entries is required");
        this.exceptions = Objects.requireNonNull(exceptions, "exceptions is required");
        this.policies = Objects.requireNonNull(policies, "policies is required");
        this.bands = Objects.requireNonNull(bands, "bands is required");
        this.reporting = Objects.requireNonNull(reporting, "reporting is required");
    }

    @Override
    public PagedResponse<RiskReviewSummaryResponse> listReviews(
            Long auditorId, RiskReviewStatus status, int page, int size) {
        access.requireActiveRole(auditorId, RoleName.AUDITOR);
        if (status == null) {
            throw new IllegalArgumentException("status is required");
        }
        return PagedResponse.from(reviews.findAllByStatusOrderByRequestedAtAscApprovalIdAsc(
                status, pageRequest(page, size)), RiskReviewSummaryResponse::from);
    }

    @Override
    public RiskReviewDetailResponse getReview(Long auditorId, Long reviewId) {
        access.requireActiveRole(auditorId, RoleName.AUDITOR);
        requirePositive(reviewId, "reviewId");
        return RiskReviewDetailResponse.from(reviews.findByApprovalId(reviewId).orElseThrow(() ->
                new ResourceNotFoundExcp("RISK_REVIEW_NOT_FOUND", "Risk Review was not found")));
    }

    @Override
    public PagedResponse<TransactionSummaryResponse> searchTransactions(Long auditorId, Long customerId,
            TransactionState state, OffsetDateTime from, OffsetDateTime to, int page, int size) {
        access.requireActiveRole(auditorId, RoleName.AUDITOR);
        if (customerId != null) {
            requirePositive(customerId, "customerId");
        }
        if (from != null && to != null && !from.isBefore(to)) {
            throw new IllegalArgumentException("from must be earlier than to");
        }
        return PagedResponse.from(transactions.searchForAudit(customerId, state,
                from == null ? null : from.withOffsetSameInstant(ZoneOffset.UTC),
                to == null ? null : to.withOffsetSameInstant(ZoneOffset.UTC), pageRequest(page, size)),
                TransactionSummaryResponse::from);
    }

    @Override
    public AuditTransactionDetailResponse getTransactionEvidence(Long auditorId, Long transactionId) {
        access.requireActiveRole(auditorId, RoleName.AUDITOR);
        requirePositive(transactionId, "transactionId");
        TransactionDb payment = transactions.findForAuditById(transactionId).orElseThrow(() ->
                new ResourceNotFoundExcp("TRANSACTION_NOT_FOUND", "Transaction was not found"));
        List<TransactionRiskFactor> evidence = factors.findAllForAuditByTransactionId(transactionId);
        if (payment.getRiskAssessedAt() == null) {
            if (!evidence.isEmpty()) {
                throw new IllegalStateException("Unassessed transaction has unexpected risk evidence");
            }
        } else {
            // The canonical amount-only engine records exactly one immutable PAYMENT_AMOUNT factor.
            if (evidence.size() != 1) {
                throw new IllegalStateException("Assessed transaction must have one PAYMENT_AMOUNT evidence record");
            }
            TransactionRiskExplanationResponse.from(payment, evidence.getFirst());
        }
        return new AuditTransactionDetailResponse(payment.getCustomer().getUserId().toString(),
                TransactionResponse.from(payment), evidence.stream()
                        .map(AuditTransactionDetailResponse.RiskEvidence::from).toList());
    }

    @Override
    public PagedResponse<AuditLedgerPostingResponse.Header> searchLedgerPostings(Long auditorId, Long transactionId,
            LedgerPostingStatus status, OffsetDateTime from, OffsetDateTime to, int page, int size) {
        access.requireActiveRole(auditorId, RoleName.AUDITOR);
        ReportingReadRepository.validateRange(transactionId, from, to);
        return PagedResponse.from(postings.searchForAudit(transactionId, status, utc(from), utc(to),
                pageRequest(page, size)), AuditLedgerPostingResponse.Header::from);
    }

    @Override
    public AuditLedgerPostingResponse getLedgerPosting(Long auditorId, Long postingId) {
        access.requireActiveRole(auditorId, RoleName.AUDITOR);
        requirePositive(postingId, "postingId");
        var posting = postings.findByPostingId(postingId).orElseThrow(() ->
                new ResourceNotFoundExcp("LEDGER_POSTING_NOT_FOUND", "Ledger posting was not found"));
        return new AuditLedgerPostingResponse(AuditLedgerPostingResponse.Header.from(posting),
                entries.findAllByPosting_PostingIdOrderByLineNumberAsc(postingId).stream()
                        .map(AuditLedgerPostingResponse.Entry::from).toList());
    }

    @Override
    public PagedResponse<AuditReconciliationResponse.Ledger> ledgerReconciliation(
            Long auditorId, Long postingId, String status, int page, int size) {
        access.requireActiveRole(auditorId, RoleName.AUDITOR);
        return reporting.ledger(postingId, status, page, size);
    }

    @Override
    public PagedResponse<AuditReconciliationResponse.Reservation> reservationReconciliation(
            Long auditorId, Long accountId, String status, int page, int size) {
        access.requireActiveRole(auditorId, RoleName.AUDITOR);
        return reporting.reservations(accountId, status, page, size);
    }

    @Override
    public PagedResponse<AuditExceptionResponse> searchExceptions(Long auditorId, Long transactionId,
            TransactionProcessingStage stage, TransactionExceptionStatus status,
            OffsetDateTime from, OffsetDateTime to, int page, int size) {
        access.requireActiveRole(auditorId, RoleName.AUDITOR);
        ReportingReadRepository.validateRange(transactionId, from, to);
        return PagedResponse.from(exceptions.searchForAudit(transactionId, stage, status, utc(from), utc(to),
                pageRequest(page, size)), AuditExceptionResponse::from);
    }

    @Override
    public AuditExceptionResponse getException(Long auditorId, Long exceptionId) {
        access.requireActiveRole(auditorId, RoleName.AUDITOR);
        requirePositive(exceptionId, "exceptionId");
        return AuditExceptionResponse.from(exceptions.findByTransactionExceptionId(exceptionId).orElseThrow(() ->
                new ResourceNotFoundExcp("TRANSACTION_EXCEPTION_NOT_FOUND", "Transaction exception was not found")));
    }

    @Override
    public PagedResponse<AuditRiskPolicyResponse.Header> listPolicies(
            Long auditorId, RiskPolicyStatus status, int page, int size) {
        access.requireActiveRole(auditorId, RoleName.AUDITOR);
        return PagedResponse.from(policies.searchForAudit(status, pageRequest(page, size)),
                AuditRiskPolicyResponse.Header::from);
    }

    @Override
    public AuditRiskPolicyResponse getPolicy(Long auditorId, String policyVersion) {
        access.requireActiveRole(auditorId, RoleName.AUDITOR);
        if (policyVersion == null || policyVersion.isBlank() || policyVersion.length() > 50)
            throw new IllegalArgumentException("policyVersion must contain 1 to 50 characters");
        var policy = policies.findByPolicyVersion(policyVersion).orElseThrow(() ->
                new ResourceNotFoundExcp("RISK_POLICY_NOT_FOUND", "Risk policy was not found"));
        return new AuditRiskPolicyResponse(AuditRiskPolicyResponse.Header.from(policy),
                bands.findAllForPolicy(policy.getRiskPolicyId()).stream()
                        .map(AuditRiskPolicyResponse.Band::from).toList());
    }

    private static OffsetDateTime utc(OffsetDateTime value) {
        return value == null ? null : value.withOffsetSameInstant(ZoneOffset.UTC);
    }

    private static PageRequest pageRequest(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("page must be non-negative and size between 1 and 100");
        }
        return PageRequest.of(page, size);
    }

    private static void requirePositive(Long value, String field) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
    }
}
