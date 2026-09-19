package com.ofss.beans;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

@Entity
@Table(
        name = "RISK_REVIEW",
        schema = "SAFEPAY_OWNER",
        uniqueConstraints = @UniqueConstraint(
                name = "UK_RISK_REVIEW_TX_ROUND",
                columnNames = {
                        "TRANSACTION_ID",
                        "REVIEW_ROUND"
                }))
@SequenceGenerator(
        name = "riskReviewSequence",
        sequenceName = "SAFEPAY_OWNER.SEQ_RISK_REVIEW_ID",
        allocationSize = 1)
public class RiskReview {

    @Id
    @GeneratedValue(
            strategy = GenerationType.SEQUENCE,
            generator = "riskReviewSequence")
    @Column(
            name = "APPROVAL_ID",
            nullable = false,
            updatable = false)
    private Long approvalId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "TRANSACTION_ID",
            nullable = false,
            updatable = false)
    private TransactionDb transaction;

    @Column(
            name = "REVIEW_ROUND",
            nullable = false,
            updatable = false,
            precision = 5,
            scale = 0)
    private int reviewRound;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "CUSTOMER_USER_ID",
            nullable = false,
            updatable = false)
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ASSIGNED_RISK_OFFICER_ID")
    private User assignedRiskOfficer;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "STATUS",
            nullable = false,
            length = 30)
    private RiskReviewStatus status;

    @Column(name = "DECISION_REASON", length = 1000)
    private String decisionReason;

    @Column(
            name = "REQUESTED_AT",
            nullable = false,
            updatable = false)
    private OffsetDateTime requestedAt;

    @Column(name = "CLAIMED_AT")
    private OffsetDateTime claimedAt;

    @Column(name = "DECIDED_AT")
    private OffsetDateTime decidedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "DECIDED_BY_USER_ID")
    private User decidedByUser;

    @Column(name = "UPDATED_AT", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(
            name = "VERSION_NO",
            nullable = false,
            precision = 10,
            scale = 0)
    private long versionNo;

    protected RiskReview() {
        // Required by JPA.
    }

    public static RiskReview open(
            TransactionDb transaction,
            int reviewRound,
            OffsetDateTime requestedAt) {

        requireEligibleTransaction(transaction);

        if (reviewRound < 1 || reviewRound > 99_999) {
            throw new IllegalArgumentException(
                    "reviewRound must be between 1 and 99999");
        }

        OffsetDateTime timestamp = requireUtcTimestamp(
                requestedAt,
                "requestedAt");

        RiskReview review = new RiskReview();
        review.transaction = transaction;
        review.reviewRound = reviewRound;
        review.customer = transaction.getCustomer();
        review.status = RiskReviewStatus.PENDING;
        review.requestedAt = timestamp;
        review.updatedAt = timestamp;
        return review;
    }

    public void approve(
            User riskOfficer,
            String optionalReason,
            OffsetDateTime decidedAt) {

        decide(
                riskOfficer,
                RiskReviewStatus.APPROVED,
                normalizeOptionalText(
                        optionalReason,
                        "decisionReason",
                        1000),
                decidedAt);
    }

    public void reject(
            User riskOfficer,
            String reason,
            OffsetDateTime decidedAt) {

        decide(
                riskOfficer,
                RiskReviewStatus.REJECTED,
                requireText(reason, "decisionReason", 1000),
                decidedAt);
    }

    public void requestReverification(
            User riskOfficer,
            String reason,
            OffsetDateTime decidedAt) {

        decide(
                riskOfficer,
                RiskReviewStatus.REVERIFICATION_REQUESTED,
                requireText(reason, "decisionReason", 1000),
                decidedAt);
    }

    public void cancelByCustomer(
            User cancellingCustomer,
            OffsetDateTime cancelledAt) {

        requirePending();

        User actor = requirePersistedUser(
                cancellingCustomer,
                "cancellingCustomer");

        if (!actor.getUserId().equals(customer.getUserId())) {
            throw new IllegalArgumentException(
                    "Only the review customer may cancel the review");
        }

        OffsetDateTime timestamp = requireMutationTime(
                cancelledAt,
                "cancelledAt");

        status = RiskReviewStatus.CANCELLED;
        decisionReason = null;
        decidedAt = timestamp;
        decidedByUser = actor;
        updatedAt = timestamp;
    }

    private void decide(
            User riskOfficer,
            RiskReviewStatus targetStatus,
            String normalizedReason,
            OffsetDateTime decisionTime) {

        requirePending();

        if (targetStatus == RiskReviewStatus.PENDING
                || targetStatus == RiskReviewStatus.CANCELLED) {
            throw new IllegalArgumentException(
                    "targetStatus is not an officer decision");
        }

        User officer = requirePersistedUser(
                riskOfficer,
                "riskOfficer");

        if (officer.getUserId().equals(customer.getUserId())) {
            throw new IllegalArgumentException(
                    "A customer cannot decide their own review");
        }

        OffsetDateTime timestamp = requireMutationTime(
                decisionTime,
                "decidedAt");

        assignedRiskOfficer = officer;
        claimedAt = timestamp;
        status = targetStatus;
        decisionReason = normalizedReason;
        decidedAt = timestamp;
        decidedByUser = officer;
        updatedAt = timestamp;
    }

    private void requirePending() {
        if (status != RiskReviewStatus.PENDING) {
            throw new IllegalStateException(
                    "Only a pending risk review may change");
        }
    }

    private OffsetDateTime requireMutationTime(
            OffsetDateTime value,
            String fieldName) {

        OffsetDateTime timestamp = requireUtcTimestamp(
                value,
                fieldName);

        if (timestamp.isBefore(updatedAt)) {
            throw new IllegalArgumentException(
                    "Risk review time cannot move backwards");
        }

        return timestamp;
    }

    private static void requireEligibleTransaction(
            TransactionDb transaction) {

        Objects.requireNonNull(transaction, "transaction is required");

        if (transaction.getTransactionId() == null
                || transaction.getTransactionId() <= 0L) {
            throw new IllegalArgumentException(
                    "transaction must already be persisted");
        }

        requirePersistedUser(
                transaction.getCustomer(),
                "transaction customer");

        if (transaction.getState()
                        != TransactionState.PENDING_RISK_REVIEW
                || transaction.getRiskTier() != RiskTier.VERY_HIGH
                || transaction.getVerificationCompletedAt() == null
                || transaction.getReservedAmount() == null
                || transaction.getReservedAmount()
                        .compareTo(transaction.getAmount()) != 0
                || transaction.getReservedAt() == null
                || transaction.getReservationEndedAt() != null) {

            throw new IllegalArgumentException(
                    "Risk review requires a verified, fully reserved VERY_HIGH transaction");
        }
    }

    private static User requirePersistedUser(
            User user,
            String fieldName) {

        User persisted = Objects.requireNonNull(
                user,
                fieldName + " is required");

        if (persisted.getUserId() == null
                || persisted.getUserId() <= 0L) {
            throw new IllegalArgumentException(
                    fieldName + " must already be persisted");
        }

        return persisted;
    }

    private static OffsetDateTime requireUtcTimestamp(
            OffsetDateTime value,
            String fieldName) {

        return Objects.requireNonNull(
                        value,
                        fieldName + " is required")
                .withOffsetSameInstant(ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.MICROS);
    }

    private static String requireText(
            String value,
            String fieldName,
            int maximumLength) {

        String normalized = normalizeOptionalText(
                value,
                fieldName,
                maximumLength);

        if (normalized == null) {
            throw new IllegalArgumentException(
                    fieldName + " is required");
        }

        return normalized;
    }

    private static String normalizeOptionalText(
            String value,
            String fieldName,
            int maximumLength) {

        if (value == null) {
            return null;
        }

        if (value.isBlank()) {
            return null;
        }

        String normalized = value.trim();

        if (normalized.length() > maximumLength) {
            throw new IllegalArgumentException(
                    fieldName + " has an invalid length");
        }

        return normalized;
    }

    public Long getApprovalId() {
        return approvalId;
    }

    public TransactionDb getTransaction() {
        return transaction;
    }

    public int getReviewRound() {
        return reviewRound;
    }

    public User getCustomer() {
        return customer;
    }

    public User getAssignedRiskOfficer() {
        return assignedRiskOfficer;
    }

    public RiskReviewStatus getStatus() {
        return status;
    }

    public String getDecisionReason() {
        return decisionReason;
    }

    public OffsetDateTime getRequestedAt() {
        return requestedAt;
    }

    public OffsetDateTime getClaimedAt() {
        return claimedAt;
    }

    public OffsetDateTime getDecidedAt() {
        return decidedAt;
    }

    public User getDecidedByUser() {
        return decidedByUser;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public long getVersionNo() {
        return versionNo;
    }
}
