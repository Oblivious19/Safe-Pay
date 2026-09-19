package com.ofss.dto.riskreview;

import java.time.OffsetDateTime;
import java.util.Objects;

import com.ofss.beans.RiskReview;

public record RiskReviewDetailResponse(
        RiskReviewSummaryResponse review,
        String assignedRiskOfficerId,
        String assignedRiskOfficerName,
        String decisionReason,
        OffsetDateTime claimedAt,
        OffsetDateTime decidedAt,
        String decidedByUserId,
        String decidedByUserName,
        long version) {

    public static RiskReviewDetailResponse from(
            RiskReview review) {

        Objects.requireNonNull(review, "review is required");

        var assigned = review.getAssignedRiskOfficer();
        var decidedBy = review.getDecidedByUser();

        return new RiskReviewDetailResponse(
                RiskReviewSummaryResponse.from(review),
                id(assigned),
                assigned == null ? null : assigned.getFullName(),
                review.getDecisionReason(),
                review.getClaimedAt(),
                review.getDecidedAt(),
                id(decidedBy),
                decidedBy == null ? null : decidedBy.getFullName(),
                review.getVersionNo());
    }

    private static String id(com.ofss.beans.User user) {
        if (user == null) {
            return null;
        }

        if (user.getUserId() == null || user.getUserId() <= 0L) {
            throw new IllegalArgumentException(
                    "review actor must already be persisted");
        }

        return user.getUserId().toString();
    }
}
