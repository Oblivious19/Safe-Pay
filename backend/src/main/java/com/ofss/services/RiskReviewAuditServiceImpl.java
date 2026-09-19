package com.ofss.services;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ofss.beans.AuditLog;
import com.ofss.beans.RiskReview;
import com.ofss.beans.RiskReviewStatus;
import com.ofss.beans.TransactionState;
import com.ofss.beans.User;
import com.ofss.dto.riskreview.RiskReviewNoteResponse;
import com.ofss.repository.AuditLogDao;

@Service
public class RiskReviewAuditServiceImpl
        implements RiskReviewAuditService {

    private static final String DECISION_PREFIX =
            "RISK-REVIEW-DECISION-";
    private static final String NOTE_PREFIX =
            "RISK-REVIEW-NOTE-";

    private final AuditLogDao auditLogDao;
    private final ObjectMapper objectMapper;

    public RiskReviewAuditServiceImpl(
            AuditLogDao auditLogDao,
            ObjectMapper objectMapper) {

        this.auditLogDao = Objects.requireNonNull(
                auditLogDao,
                "auditLogDao is required");
        this.objectMapper = Objects.requireNonNull(
                objectMapper,
                "objectMapper is required");
    }

    @Override
    public void appendDecision(
            RiskReview review,
            User riskOfficer,
            TransactionState previousState,
            TransactionState resultingState,
            String correlationId,
            String idempotencyKey,
            OffsetDateTime occurredAt) {

        RiskReview validated = requirePersistedReview(review);

        if (!validated.getStatus().isTerminal()
                || validated.getStatus() == RiskReviewStatus.CANCELLED) {
            throw new IllegalArgumentException(
                    "Officer decision audit requires a completed officer review");
        }

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("reviewId", validated.getApprovalId().toString());
        details.put("reviewRound", validated.getReviewRound());
        details.put("reviewStatus", validated.getStatus().name());
        details.put("decisionReason", validated.getDecisionReason());

        auditLogDao.save(AuditLog.userRiskReviewEvent(
                DECISION_PREFIX + validated.getApprovalId(),
                riskOfficer,
                "RISK_REVIEW_" + validated.getStatus().name(),
                validated.getApprovalId(),
                validated.getTransaction(),
                previousState,
                resultingState,
                validated.getStatus().name(),
                correlationId,
                idempotencyKey,
                json(details),
                occurredAt));
    }

    @Override
    public RiskReviewNoteResponse appendNote(
            RiskReview review,
            User riskOfficer,
            String note,
            String correlationId,
            String idempotencyKey,
            OffsetDateTime occurredAt) {

        RiskReview validated = requirePersistedReview(review);

        if (validated.getStatus() != RiskReviewStatus.PENDING) {
            throw new IllegalArgumentException(
                    "Internal notes may be added only to pending reviews");
        }

        String normalizedNote = requireNote(note);
        String eventReference = NOTE_PREFIX
                + UUID.randomUUID()
                        .toString()
                        .replace("-", "");

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("reviewId", validated.getApprovalId().toString());
        details.put("reviewRound", validated.getReviewRound());
        details.put("note", normalizedNote);

        auditLogDao.save(AuditLog.userRiskReviewEvent(
                eventReference,
                riskOfficer,
                "RISK_REVIEW_NOTE_ADDED",
                validated.getApprovalId(),
                validated.getTransaction(),
                TransactionState.PENDING_RISK_REVIEW,
                TransactionState.PENDING_RISK_REVIEW,
                null,
                correlationId,
                idempotencyKey,
                json(details),
                occurredAt));

        return new RiskReviewNoteResponse(
                validated.getApprovalId().toString(),
                validated.getTransaction()
                        .getTransactionId()
                        .toString(),
                eventReference,
                occurredAt);
    }

    private RiskReview requirePersistedReview(RiskReview review) {
        RiskReview validated = Objects.requireNonNull(
                review,
                "review is required");

        if (validated.getApprovalId() == null
                || validated.getApprovalId() <= 0L) {
            throw new IllegalArgumentException(
                    "review must already be persisted");
        }

        Objects.requireNonNull(
                validated.getTransaction(),
                "review transaction is required");
        return validated;
    }

    private String json(Map<String, Object> details) {
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Risk Review audit details could not be serialized",
                    exception);
        }
    }

    private static String requireNote(String note) {
        if (note == null || note.isBlank()) {
            throw new IllegalArgumentException("note is required");
        }

        return note.trim();
    }
}
