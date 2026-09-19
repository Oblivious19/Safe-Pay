package com.ofss.services;

import java.time.OffsetDateTime;

import com.ofss.beans.RiskReview;
import com.ofss.beans.TransactionState;
import com.ofss.beans.User;
import com.ofss.dto.riskreview.RiskReviewNoteResponse;

public interface RiskReviewAuditService {

    void appendDecision(
            RiskReview review,
            User riskOfficer,
            TransactionState previousState,
            TransactionState resultingState,
            String correlationId,
            String idempotencyKey,
            OffsetDateTime occurredAt);

    RiskReviewNoteResponse appendNote(
            RiskReview review,
            User riskOfficer,
            String note,
            String correlationId,
            String idempotencyKey,
            OffsetDateTime occurredAt);
}
