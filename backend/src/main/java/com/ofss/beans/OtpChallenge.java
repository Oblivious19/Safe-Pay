package com.ofss.beans;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import com.ofss.services.OtpPolicyProperties;

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
import jakarta.persistence.Version;

@Entity
@Table(
        name = "PAYMENT_OTP_CHALLENGE",
        schema = "SAFEPAY_OWNER")
@SequenceGenerator(
        name = "paymentOtpChallengeSequence",
        sequenceName =
                "SAFEPAY_OWNER.SEQ_PAYMENT_OTP_CHALLENGE_ID",
        allocationSize = 1)
public class OtpChallenge {

    @Id
    @GeneratedValue(
            strategy = GenerationType.SEQUENCE,
            generator = "paymentOtpChallengeSequence")
    @Column(
            name = "OTP_CHALLENGE_ID",
            nullable = false,
            updatable = false)
    private Long otpChallengeId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "TRANSACTION_ID",
            nullable = false,
            updatable = false)
    private TransactionDb transaction;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "CUSTOMER_USER_ID",
            nullable = false,
            updatable = false)
    private User customer;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "CHALLENGE_PURPOSE",
            nullable = false,
            updatable = false,
            length = 40)
    private OtpChallengePurpose challengePurpose;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "DELIVERY_CHANNEL",
            nullable = false,
            updatable = false,
            length = 20)
    private OtpDeliveryChannel deliveryChannel;

    @Column(
            name = "OTP_HASH",
            nullable = false,
            updatable = false,
            length = 255)
    private String otpHash;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "STATUS",
            nullable = false,
            length = 20)
    private OtpChallengeStatus status;

    @Column(
            name = "ATTEMPT_COUNT",
            nullable = false,
            precision = 3,
            scale = 0)
    private int attemptCount;

    @Column(
            name = "MAX_ATTEMPTS",
            nullable = false,
            updatable = false,
            precision = 3,
            scale = 0)
    private int maxAttempts;

    @Column(
            name = "EXPIRES_AT",
            nullable = false,
            updatable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "VERIFIED_AT")
    private OffsetDateTime verifiedAt;

    @Column(name = "INVALIDATED_AT")
    private OffsetDateTime invalidatedAt;

    @Column(
            name = "CREATED_AT",
            nullable = false,
            updatable = false)
    private OffsetDateTime createdAt;

    @Column(
            name = "UPDATED_AT",
            nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(
            name = "VERSION_NO",
            nullable = false,
            precision = 10,
            scale = 0)
    private long versionNo;

    protected OtpChallenge() {
        // Required by JPA.
    }

    public static OtpChallenge issue(
            TransactionDb transaction,
            String otpHash,
            OtpPolicyProperties policy,
            OffsetDateTime issuedAt) {

        requireEligibleTransaction(transaction);
        Objects.requireNonNull(policy, "policy is required");

        OffsetDateTime timestamp = requireUtcTimestamp(
                issuedAt,
                "issuedAt");

        OtpChallenge challenge = new OtpChallenge();
        challenge.transaction = transaction;
        challenge.customer = transaction.getCustomer();
        challenge.challengePurpose =
                OtpChallengePurpose.VERY_HIGH_PAYMENT;
        challenge.deliveryChannel =
                OtpDeliveryChannel.SIMULATED;
        challenge.otpHash = requireText(
                otpHash,
                "otpHash",
                255);
        challenge.status = OtpChallengeStatus.PENDING;
        challenge.attemptCount = 0;
        challenge.maxAttempts = policy.maxAttempts();
        challenge.createdAt = timestamp;
        challenge.updatedAt = timestamp;
        challenge.expiresAt = timestamp.plus(
                policy.validity());

        return challenge;
    }

    public OtpChallengeStatus recordFailedAttempt(
            OffsetDateTime attemptedAt) {

        OffsetDateTime timestamp =
                requirePendingMutationTime(attemptedAt);
        requireBeforeExpiry(timestamp);

        attemptCount++;
        updatedAt = timestamp;

        if (attemptCount == maxAttempts) {
            status = OtpChallengeStatus.LOCKED;
            invalidatedAt = timestamp;
        }

        return status;
    }

    public void markVerified(OffsetDateTime verifiedAt) {
        OffsetDateTime timestamp =
                requirePendingMutationTime(verifiedAt);
        requireBeforeExpiry(timestamp);

        status = OtpChallengeStatus.VERIFIED;
        this.verifiedAt = timestamp;
        updatedAt = timestamp;
    }

    public void expire(OffsetDateTime expiredAt) {
        OffsetDateTime timestamp =
                requirePendingMutationTime(expiredAt);

        if (timestamp.isBefore(expiresAt)) {
            throw new IllegalArgumentException(
                    "expiredAt cannot be before expiresAt");
        }

        status = OtpChallengeStatus.EXPIRED;
        invalidatedAt = timestamp;
        updatedAt = timestamp;
    }

    public void cancel(OffsetDateTime cancelledAt) {
        OffsetDateTime timestamp =
                requirePendingMutationTime(cancelledAt);

        status = OtpChallengeStatus.CANCELLED;
        invalidatedAt = timestamp;
        updatedAt = timestamp;
    }

    public boolean isExpiredAt(OffsetDateTime timestamp) {
        OffsetDateTime normalized = requireUtcTimestamp(
                timestamp,
                "timestamp");
        return !normalized.isBefore(expiresAt);
    }

    private OffsetDateTime requirePendingMutationTime(
            OffsetDateTime value) {

        if (status != OtpChallengeStatus.PENDING) {
            throw new IllegalStateException(
                    "A terminal OTP challenge cannot change");
        }

        OffsetDateTime timestamp = requireUtcTimestamp(
                value,
                "timestamp");

        if (timestamp.isBefore(updatedAt)) {
            throw new IllegalArgumentException(
                    "OTP challenge time cannot move backwards");
        }

        return timestamp;
    }

    private void requireBeforeExpiry(OffsetDateTime timestamp) {
        if (!timestamp.isBefore(expiresAt)) {
            throw new IllegalStateException(
                    "The OTP challenge has expired");
        }
    }

    private static void requireEligibleTransaction(
            TransactionDb transaction) {

        Objects.requireNonNull(
                transaction,
                "transaction is required");

        if (transaction.getTransactionId() == null
                || transaction.getTransactionId() <= 0L) {
            throw new IllegalArgumentException(
                    "transaction must already be persisted");
        }

        User customer = transaction.getCustomer();

        if (customer == null
                || customer.getUserId() == null
                || customer.getUserId() <= 0L) {
            throw new IllegalArgumentException(
                    "transaction customer must already be persisted");
        }

        if (transaction.getState()
                != TransactionState.VERIFICATION_REQUIRED
                || transaction.getRiskTier()
                        != RiskTier.VERY_HIGH) {
            throw new IllegalArgumentException(
                    "OTP challenge requires a VERY_HIGH transaction in VERIFICATION_REQUIRED");
        }
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

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + " is required");
        }

        String normalized = value.trim();

        if (normalized.length() > maximumLength) {
            throw new IllegalArgumentException(
                    fieldName + " has an invalid length");
        }

        return normalized;
    }

    public Long getOtpChallengeId() {
        return otpChallengeId;
    }

    public TransactionDb getTransaction() {
        return transaction;
    }

    public User getCustomer() {
        return customer;
    }

    public OtpChallengePurpose getChallengePurpose() {
        return challengePurpose;
    }

    public OtpDeliveryChannel getDeliveryChannel() {
        return deliveryChannel;
    }

    public String getOtpHash() {
        return otpHash;
    }

    public OtpChallengeStatus getStatus() {
        return status;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public OffsetDateTime getVerifiedAt() {
        return verifiedAt;
    }

    public OffsetDateTime getInvalidatedAt() {
        return invalidatedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public long getVersionNo() {
        return versionNo;
    }
}
