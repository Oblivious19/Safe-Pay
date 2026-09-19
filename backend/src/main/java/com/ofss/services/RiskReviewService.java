package com.ofss.services;

import java.time.OffsetDateTime;

import com.ofss.beans.TransactionDb;
import com.ofss.beans.PaymentCategory;
import com.ofss.common.api.PagedResponse;
import com.ofss.dto.riskreview.RiskReviewDetailResponse;
import com.ofss.dto.riskreview.RiskReviewNoteResponse;
import com.ofss.dto.riskreview.RiskReviewSummaryResponse;

public interface RiskReviewService {

    void openNextRound(
            TransactionDb transaction,
            OffsetDateTime requestedAt);

    void cancelPendingForCustomer(
            TransactionDb transaction,
            OffsetDateTime cancelledAt);

    PagedResponse<RiskReviewSummaryResponse> listPending(
            Long riskOfficerUserId,
            int page,
            int size);

    PagedResponse<RiskReviewSummaryResponse> listPending(
            Long riskOfficerUserId, PaymentCategory category, String sort, int page, int size);

    RiskReviewDetailResponse getReview(
            Long riskOfficerUserId,
            Long reviewId);

    RiskReviewDetailResponse approve(
            Long riskOfficerUserId,
            Long reviewId,
            String optionalReason,
            String correlationId,
            String idempotencyKey);

    RiskReviewDetailResponse reject(
            Long riskOfficerUserId,
            Long reviewId,
            String reason,
            String correlationId,
            String idempotencyKey);

    RiskReviewDetailResponse requestReverification(
            Long riskOfficerUserId,
            Long reviewId,
            String reason,
            String correlationId,
            String idempotencyKey);

    RiskReviewNoteResponse addNote(
            Long riskOfficerUserId,
            Long reviewId,
            String note,
            String correlationId,
            String idempotencyKey);
}
