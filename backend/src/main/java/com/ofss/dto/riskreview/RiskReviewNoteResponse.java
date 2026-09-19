package com.ofss.dto.riskreview;

import java.time.OffsetDateTime;

public record RiskReviewNoteResponse(
        String reviewId,
        String transactionId,
        String eventReference,
        OffsetDateTime recordedAt) {
}
