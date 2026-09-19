package com.ofss.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ofss.beans.AuditActorType;
import com.ofss.beans.AuditLog;
import com.ofss.beans.AuditOutcome;
import com.ofss.beans.RiskReview;
import com.ofss.beans.RiskReviewStatus;
import com.ofss.beans.TransactionDb;
import com.ofss.beans.TransactionState;
import com.ofss.beans.User;
import com.ofss.dto.riskreview.RiskReviewNoteResponse;
import com.ofss.repository.AuditLogDao;

@ExtendWith(MockitoExtension.class)
class RiskReviewAuditServiceTest {

    private static final OffsetDateTime NOW =
            OffsetDateTime.parse("2026-09-16T18:00:00Z");

    @Mock private AuditLogDao auditLogDao;
    @Mock private RiskReview review;
    @Mock private TransactionDb transaction;
    @Mock private User officer;

    private RiskReviewAuditService service;

    @BeforeEach
    void setUp() {
        service = new RiskReviewAuditServiceImpl(
                auditLogDao,
                new ObjectMapper());

        when(review.getApprovalId()).thenReturn(501L);
        when(review.getTransaction()).thenReturn(transaction);
    }

    @Test
    void appendsCompleteOfficerDecisionEvidence() {
        stubCompleteAuditContext();
        when(review.getStatus()).thenReturn(RiskReviewStatus.REJECTED);
        when(review.getDecisionReason())
                .thenReturn("Confirmed mule pattern");

        service.appendDecision(
                review,
                officer,
                TransactionState.PENDING_RISK_REVIEW,
                TransactionState.CANCELLED,
                "corr-501",
                "key-501",
                NOW);

        AuditLog event = capturedEvent();
        assertThat(event.getEventReference())
                .isEqualTo("RISK-REVIEW-DECISION-501");
        assertThat(event.getActorType()).isEqualTo(AuditActorType.USER);
        assertThat(event.getActorUser()).isSameAs(officer);
        assertThat(event.getActorRoleCode()).isEqualTo("RISK_OFFICER");
        assertThat(event.getActionCode()).isEqualTo("RISK_REVIEW_REJECTED");
        assertThat(event.getEntityType()).isEqualTo("RISK_REVIEW");
        assertThat(event.getEntityId()).isEqualTo(501L);
        assertThat(event.getPreviousState())
                .isEqualTo("PENDING_RISK_REVIEW");
        assertThat(event.getNewState()).isEqualTo("CANCELLED");
        assertThat(event.getOutcome()).isEqualTo(AuditOutcome.SUCCESS);
        assertThat(event.getReasonCode()).isEqualTo("REJECTED");
        assertThat(event.getCorrelationId()).isEqualTo("corr-501");
        assertThat(event.getIdempotencyKey()).isEqualTo("key-501");
        assertThat(event.getDetailsJson())
                .contains("\"reviewRound\":2")
                .contains("Confirmed mule pattern");
    }

    @Test
    void approvalAuditSafelyRepresentsAbsentOptionalReason() {
        stubCompleteAuditContext();
        when(review.getStatus()).thenReturn(RiskReviewStatus.APPROVED);

        service.appendDecision(
                review,
                officer,
                TransactionState.PENDING_RISK_REVIEW,
                TransactionState.RELEASED,
                "corr-approve",
                "key-approve",
                NOW);

        assertThat(capturedEvent().getDetailsJson())
                .contains("\"decisionReason\":null");
    }

    @Test
    void noteIsAppendOnlyAuditAndResponseContainsNoNoteText() {
        stubCompleteAuditContext();
        when(review.getStatus()).thenReturn(RiskReviewStatus.PENDING);

        RiskReviewNoteResponse response = service.appendNote(
                review,
                officer,
                "  Check device history  ",
                "corr-note",
                "key-note",
                NOW);

        AuditLog event = capturedEvent();
        assertThat(event.getActionCode())
                .isEqualTo("RISK_REVIEW_NOTE_ADDED");
        assertThat(event.getPreviousState())
                .isEqualTo("PENDING_RISK_REVIEW");
        assertThat(event.getNewState())
                .isEqualTo("PENDING_RISK_REVIEW");
        assertThat(event.getDetailsJson())
                .contains("Check device history")
                .doesNotContain("otp", "accountNumber");
        assertThat(response.reviewId()).isEqualTo("501");
        assertThat(response.transactionId()).isEqualTo("101");
        assertThat(response.eventReference())
                .startsWith("RISK-REVIEW-NOTE-");
    }

    @Test
    void terminalReviewCannotReceiveInternalNote() {
        when(review.getStatus()).thenReturn(RiskReviewStatus.APPROVED);

        assertThatThrownBy(() -> service.appendNote(
                review,
                officer,
                "Too late",
                "corr-note",
                "key-note",
                NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "Internal notes may be added only to pending reviews");

        verify(auditLogDao, never()).save(any());
    }

    @Test
    void pendingReviewCannotBeWrittenAsDecisionEvidence() {
        when(review.getStatus()).thenReturn(RiskReviewStatus.PENDING);

        assertThatThrownBy(() -> service.appendDecision(
                review,
                officer,
                TransactionState.PENDING_RISK_REVIEW,
                TransactionState.RELEASED,
                "corr",
                "key",
                NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "Officer decision audit requires a completed officer review");
    }

    @Test
    void customerCancellationIsNotMislabelledAsOfficerDecision() {
        when(review.getStatus()).thenReturn(RiskReviewStatus.CANCELLED);

        assertThatThrownBy(() -> service.appendDecision(
                review,
                officer,
                TransactionState.PENDING_RISK_REVIEW,
                TransactionState.CANCELLED,
                "corr",
                "key",
                NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "Officer decision audit requires a completed officer review");
    }

    private AuditLog capturedEvent() {
        ArgumentCaptor<AuditLog> captor =
                ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogDao).save(captor.capture());
        return captor.getValue();
    }

    private void stubCompleteAuditContext() {
        when(review.getReviewRound()).thenReturn(2);
        when(transaction.getTransactionId()).thenReturn(101L);
        when(officer.getUserId()).thenReturn(22L);
    }
}
