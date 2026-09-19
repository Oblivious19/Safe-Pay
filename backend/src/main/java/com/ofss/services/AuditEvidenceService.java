package com.ofss.services;

import java.time.OffsetDateTime;
import com.ofss.beans.*;
import com.ofss.dto.audit.*;
import com.ofss.beans.RiskReviewStatus;
import com.ofss.beans.TransactionState;
import com.ofss.common.api.PagedResponse;
import com.ofss.dto.audit.AuditTransactionDetailResponse;
import com.ofss.dto.riskreview.RiskReviewDetailResponse;
import com.ofss.dto.riskreview.RiskReviewSummaryResponse;
import com.ofss.dto.transaction.TransactionSummaryResponse;

public interface AuditEvidenceService {
    PagedResponse<AuditLedgerPostingResponse.Header> searchLedgerPostings(Long auditorId, Long transactionId,
            LedgerPostingStatus status, OffsetDateTime from, OffsetDateTime to, int page, int size);
    AuditLedgerPostingResponse getLedgerPosting(Long auditorId, Long postingId);
    PagedResponse<AuditReconciliationResponse.Ledger> ledgerReconciliation(
            Long auditorId, Long postingId, String reconciliationStatus, int page, int size);
    PagedResponse<AuditReconciliationResponse.Reservation> reservationReconciliation(
            Long auditorId, Long accountId, String reconciliationStatus, int page, int size);
    PagedResponse<AuditExceptionResponse> searchExceptions(Long auditorId, Long transactionId,
            TransactionProcessingStage processingStage, TransactionExceptionStatus status,
            OffsetDateTime from, OffsetDateTime to, int page, int size);
    AuditExceptionResponse getException(Long auditorId, Long exceptionId);
    PagedResponse<AuditRiskPolicyResponse.Header> listPolicies(Long auditorId, RiskPolicyStatus status, int page, int size);
    AuditRiskPolicyResponse getPolicy(Long auditorId, String policyVersion);
    PagedResponse<RiskReviewSummaryResponse> listReviews(Long auditorId, RiskReviewStatus status, int page, int size);
    RiskReviewDetailResponse getReview(Long auditorId, Long reviewId);
    PagedResponse<TransactionSummaryResponse> searchTransactions(Long auditorId, Long customerId,
            TransactionState state, OffsetDateTime from, OffsetDateTime to, int page, int size);
    AuditTransactionDetailResponse getTransactionEvidence(Long auditorId, Long transactionId);
}
