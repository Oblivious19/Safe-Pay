package com.ofss.beans;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RiskReviewTest {

    private static final OffsetDateTime REQUESTED_AT =
            OffsetDateTime.parse("2026-09-16T16:00:00Z");

    private TransactionDb transaction;
    private User customer;
    private User officer;

    @BeforeEach
    void setUp() {
        customer = user(11L);
        officer = user(22L);

        transaction = mock(TransactionDb.class);
        when(transaction.getTransactionId()).thenReturn(101L);
        when(transaction.getCustomer()).thenReturn(customer);
        when(transaction.getState()).thenReturn(
                TransactionState.PENDING_RISK_REVIEW);
        when(transaction.getRiskTier()).thenReturn(RiskTier.VERY_HIGH);
        when(transaction.getVerificationCompletedAt())
                .thenReturn(REQUESTED_AT.minusSeconds(1));
        when(transaction.getAmount())
                .thenReturn(new BigDecimal("125000.00"));
        when(transaction.getReservedAmount())
                .thenReturn(new BigDecimal("125000.00"));
        when(transaction.getReservedAt())
                .thenReturn(REQUESTED_AT.minusMinutes(2));
        when(transaction.getReservationEndedAt()).thenReturn(null);
    }

    @Test
    void opensExactPendingRoundForVerifiedReservedTransaction() {
        RiskReview review = open();

        assertThat(review.getTransaction()).isSameAs(transaction);
        assertThat(review.getCustomer()).isSameAs(customer);
        assertThat(review.getReviewRound()).isEqualTo(1);
        assertThat(review.getStatus()).isEqualTo(RiskReviewStatus.PENDING);
        assertThat(review.getRequestedAt()).isEqualTo(REQUESTED_AT);
        assertThat(review.getUpdatedAt()).isEqualTo(REQUESTED_AT);
        assertThat(review.getAssignedRiskOfficer()).isNull();
        assertThat(review.getDecidedAt()).isNull();
    }

    @Test
    void approvalAtomicallyAssignsOfficerAndAllowsOptionalReason() {
        RiskReview review = open();
        OffsetDateTime decidedAt = REQUESTED_AT.plusMinutes(1);

        review.approve(officer, "  ", decidedAt);

        assertThat(review.getStatus()).isEqualTo(RiskReviewStatus.APPROVED);
        assertThat(review.getAssignedRiskOfficer()).isSameAs(officer);
        assertThat(review.getClaimedAt()).isEqualTo(decidedAt);
        assertThat(review.getDecidedAt()).isEqualTo(decidedAt);
        assertThat(review.getDecidedByUser()).isSameAs(officer);
        assertThat(review.getDecisionReason()).isNull();
    }

    @Test
    void rejectionRequiresReasonAndNormalizesIt() {
        RiskReview review = open();

        assertThatThrownBy(() -> review.reject(
                officer,
                " ",
                REQUESTED_AT.plusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("decisionReason is required");

        review.reject(
                officer,
                "  beneficiary behavior requires investigation  ",
                REQUESTED_AT.plusSeconds(2));

        assertThat(review.getStatus()).isEqualTo(RiskReviewStatus.REJECTED);
        assertThat(review.getDecisionReason())
                .isEqualTo("beneficiary behavior requires investigation");
    }

    @Test
    void reverificationRequiresReason() {
        RiskReview review = open();

        assertThatThrownBy(() -> review.requestReverification(
                officer,
                null,
                REQUESTED_AT.plusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("decisionReason is required");
    }

    @Test
    void customerCancellationUsesCustomerAsTerminalActor() {
        RiskReview review = open();
        OffsetDateTime cancelledAt = REQUESTED_AT.plusSeconds(5);

        review.cancelByCustomer(customer, cancelledAt);

        assertThat(review.getStatus()).isEqualTo(RiskReviewStatus.CANCELLED);
        assertThat(review.getDecidedByUser()).isSameAs(customer);
        assertThat(review.getAssignedRiskOfficer()).isNull();
        assertThat(review.getClaimedAt()).isNull();
        assertThat(review.getDecidedAt()).isEqualTo(cancelledAt);
    }

    @Test
    void refusesCustomerSelfDecisionAndForeignCancellation() {
        RiskReview review = open();

        assertThatThrownBy(() -> review.approve(
                customer,
                null,
                REQUESTED_AT.plusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A customer cannot decide their own review");

        assertThatThrownBy(() -> review.cancelByCustomer(
                officer,
                REQUESTED_AT.plusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Only the review customer may cancel the review");
    }

    @Test
    void terminalReviewCannotBeDecidedTwice() {
        RiskReview review = open();
        review.approve(
                officer,
                null,
                REQUESTED_AT.plusSeconds(1));

        assertThatThrownBy(() -> review.reject(
                user(23L),
                "late rejection",
                REQUESTED_AT.plusSeconds(2)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Only a pending risk review may change");
    }

    @Test
    void rejectsBackdatedDecisionAndOverlongRound() {
        RiskReview review = open();

        assertThatThrownBy(() -> review.approve(
                officer,
                null,
                REQUESTED_AT.minusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Risk review time cannot move backwards");

        assertThatThrownBy(() -> RiskReview.open(
                transaction,
                100_000,
                REQUESTED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("reviewRound must be between 1 and 99999");
    }

    @Test
    void requiresExactVeryHighVerifiedReservationInvariant() {
        when(transaction.getReservedAmount())
                .thenReturn(new BigDecimal("124999.99"));

        assertThatThrownBy(this::open)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "Risk review requires a verified, fully reserved VERY_HIGH transaction");
    }

    private RiskReview open() {
        return RiskReview.open(transaction, 1, REQUESTED_AT);
    }

    private static User user(Long userId) {
        User user = mock(User.class);
        when(user.getUserId()).thenReturn(userId);
        return user;
    }
}
