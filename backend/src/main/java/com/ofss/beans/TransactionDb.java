package com.ofss.beans;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import com.ofss.common.MoneyUtility;
import com.ofss.dto.risk.RiskEvaluationResult;

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
        name = "PAYMENT_TRANSACTION",
        schema = "SAFEPAY_OWNER")
@SequenceGenerator(
        name = "paymentTransactionSequence",
        sequenceName =
                "SAFEPAY_OWNER.SEQ_PAYMENT_TRANSACTION_ID",
        allocationSize = 1)
public class TransactionDb {

    @Id
    @GeneratedValue(
            strategy = GenerationType.SEQUENCE,
            generator = "paymentTransactionSequence")
    @Column(
            name = "TRANSACTION_ID",
            nullable = false,
            updatable = false)
    private Long transactionId;

    @Column(
            name = "TRANSACTION_REFERENCE",
            nullable = false,
            updatable = false,
            length = 64)
    private String transactionReference;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false)
    @JoinColumn(
            name = "CUSTOMER_USER_ID",
            nullable = false,
            updatable = false)
    private User customer;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false)
    @JoinColumn(
            name = "SOURCE_ACCOUNT_ID",
            nullable = false,
            updatable = false)
    private Account sourceAccount;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false)
    @JoinColumn(
            name = "BENEFICIARY_ID",
            nullable = false,
            updatable = false)
    private Beneficiary beneficiary;

    @Column(
            name = "AMOUNT",
            nullable = false,
            updatable = false,
            precision = MoneyUtility.MONEY_PRECISION,
            scale = MoneyUtility.MONEY_SCALE)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "CURRENCY_CODE",
            nullable = false,
            updatable = false,
            length = 3)
    private CurrencyCode currencyCode;

    @Column(
            name = "PURPOSE",
            updatable = false,
            length = 280)
    private String purpose;

    @Enumerated(EnumType.STRING)
    @Column(name = "PAYMENT_CATEGORY", length = 20, updatable = false)
    private PaymentCategory category;

    @Column(
            name = "CUSTOMER_REFERENCE",
            updatable = false,
            length = 100)
    private String customerReference;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "STATE",
            nullable = false,
            length = 32)
    private TransactionState state;

    @Column(
            name = "TERMINAL_REASON_CODE",
            length = 64)
    private String terminalReasonCode;

    @Version
    @Column(
            name = "VERSION_NO",
            nullable = false,
            precision = 10,
            scale = 0)
    private long versionNo;

    @Column(
            name = "RESERVED_AMOUNT",
            nullable = false,
            precision = MoneyUtility.MONEY_PRECISION,
            scale = MoneyUtility.MONEY_SCALE)
    private BigDecimal reservedAmount;

    @Column(name = "RESERVED_AT")
    private OffsetDateTime reservedAt;

    @Column(name = "RESERVATION_ENDED_AT")
    private OffsetDateTime reservationEndedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "RISK_POLICY_ID")
    private RiskPolicy riskPolicy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "RISK_POLICY_BAND_ID")
    private RiskPolicyBand riskPolicyBand;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PROTECTION_POLICY_ID")
    private ProtectionPolicy protectionPolicy;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "RISK_TIER",
            length = 20)
    private RiskTier riskTier;

    @Column(
            name = "RISK_SCORE",
            precision = 5,
            scale = 2)
    private BigDecimal riskScore;

    @Column(
            name = "POLICY_VERSION",
            length = 50)
    private String policyVersion;

    @Column(
            name = "MATCHED_BAND_CODE",
            length = 50)
    private String matchedBandCode;

    @Column(
            name = "PROTECTION_SECONDS",
            precision = 10,
            scale = 0)
    private Long protectionSeconds;

    @Column(
            name = "RISK_EXPLANATION",
            length = 1000)
    private String riskExplanation;

    @Column(name = "RISK_ASSESSED_AT")
    private OffsetDateTime riskAssessedAt;

    @Column(
            name = "CREATED_AT",
            nullable = false,
            updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "AUTHORIZED_AT")
    private OffsetDateTime authorizedAt;

    @Column(name = "PROTECTED_UNTIL")
    private OffsetDateTime protectedUntil;

    @Column(name = "VERIFICATION_COMPLETED_AT")
    private OffsetDateTime verificationCompletedAt;

    @Column(name = "RELEASED_AT")
    private OffsetDateTime releasedAt;

    @Column(name = "SETTLED_AT")
    private OffsetDateTime settledAt;

    @Column(name = "CANCELLED_AT")
    private OffsetDateTime cancelledAt;

    @Column(name = "FAILED_AT")
    private OffsetDateTime failedAt;

    @Column(
            name = "UPDATED_AT",
            nullable = false)
    private OffsetDateTime updatedAt;

    protected TransactionDb() {
        // Required by JPA.
    }

    public PaymentCategory getCategory() {
        return category;
    }

    public static TransactionDb createPaymentInstruction(
            String transactionReference,
            User customer,
            Account sourceAccount,
            Beneficiary beneficiary,
            BigDecimal amount,
            String purpose,
            String customerReference,
            OffsetDateTime createdAt) {

        return createPaymentInstruction(transactionReference, customer, sourceAccount, beneficiary,
                amount, purpose, customerReference, createdAt, null);
    }

    public static TransactionDb createPaymentInstruction(
            String transactionReference, User customer, Account sourceAccount,
            Beneficiary beneficiary, BigDecimal amount, String purpose,
            String customerReference, OffsetDateTime createdAt, PaymentCategory category) {

        requirePersistedCustomer(customer);
        requireOwnedSourceAccount(customer, sourceAccount);
        requireOwnedBeneficiary(customer, beneficiary);

        TransactionDb transaction = new TransactionDb();

        transaction.transactionReference = requireText(
                transactionReference,
                "transactionReference",
                64);

        transaction.customer = customer;
        transaction.sourceAccount = sourceAccount;
        transaction.beneficiary = beneficiary;
        transaction.amount =
                MoneyUtility.requireValidTransactionAmount(amount);
        transaction.currencyCode = CurrencyCode.INR;
        transaction.purpose = normalizeOptionalText(
                purpose,
                "purpose",
                280);
        PaymentCategory.requireForNewPayment(transaction.amount, category, transaction.purpose);
        transaction.category = category;
        transaction.customerReference = normalizeOptionalText(
                customerReference,
                "customerReference",
                100);
        transaction.state = TransactionState.CREATED;
        transaction.reservedAmount =
                BigDecimal.ZERO.setScale(MoneyUtility.MONEY_SCALE);

        OffsetDateTime timestamp = requireUtcTimestamp(
                createdAt,
                "createdAt");

        transaction.createdAt = timestamp;
        transaction.updatedAt = timestamp;

        return transaction;
    }

    /*
     * Lifecycle mutations are intentionally narrow. The state service owns
     * the transition matrix; this entity owns the column-level invariants
     * that must remain coherent with the Oracle constraints.
     */
    public void recordRiskAssessment(
            RiskEvaluationResult evaluation,
            RiskPolicy assessedPolicy,
            RiskPolicyBand assessedBand,
            ProtectionPolicy assessedProtection) {

        if (state != TransactionState.AUTHORIZED) {
            throw new IllegalStateException(
                    "Risk can be recorded only for an authorized transaction");
        }

        Objects.requireNonNull(evaluation, "evaluation is required");
        Objects.requireNonNull(assessedPolicy, "assessedPolicy is required");
        Objects.requireNonNull(assessedBand, "assessedBand is required");
        Objects.requireNonNull(
                assessedProtection,
                "assessedProtection is required");

        if (!Objects.equals(
                    assessedPolicy.getRiskPolicyId(),
                    evaluation.riskPolicyId())
                || !Objects.equals(
                    assessedBand.getRiskPolicyBandId(),
                    evaluation.riskPolicyBandId())
                || !Objects.equals(
                    assessedProtection.getProtectionPolicyId(),
                    evaluation.protectionPolicyId())) {

            throw new IllegalArgumentException(
                    "Risk relationships must match the evaluated snapshot");
        }

        riskPolicy = assessedPolicy;
        riskPolicyBand = assessedBand;
        protectionPolicy = assessedProtection;
        riskTier = Objects.requireNonNull(
                evaluation.riskTier(),
                "riskTier is required");
        riskScore = evaluation.riskScore();
        policyVersion = requireText(
                evaluation.policyVersion(),
                "policyVersion",
                50);
        matchedBandCode = requireText(
                evaluation.matchedBandCode(),
                "matchedBandCode",
                50);
        protectionSeconds = evaluation.protectionSeconds();
        riskExplanation = requireText(
                evaluation.explanation(),
                "riskExplanation",
                1000);
        riskAssessedAt = requireLifecycleTimestamp(
                evaluation.evaluatedAt(),
                "riskAssessedAt");
        updatedAt = riskAssessedAt;
    }

    public void recordReservation(OffsetDateTime reservedAt) {
        if (state != TransactionState.RISK_ASSESSED) {
            throw new IllegalStateException(
                    "Funds can be reserved only after risk assessment");
        }

        if (riskTier == null || riskAssessedAt == null) {
            throw new IllegalStateException(
                    "A complete risk snapshot is required before reservation");
        }

        OffsetDateTime timestamp = requireLifecycleTimestamp(
                reservedAt,
                "reservedAt");

        if (timestamp.isBefore(riskAssessedAt)) {
            throw new IllegalArgumentException(
                    "reservedAt cannot precede riskAssessedAt");
        }

        reservedAmount = amount;
        this.reservedAt = timestamp;
        reservationEndedAt = null;
        protectedUntil = switch (riskTier) {
            case LOW, VERY_HIGH -> null;
            case MEDIUM, HIGH -> timestamp.plusSeconds(
                    requirePositiveProtectionSeconds());
        };
        updatedAt = timestamp;
    }

    public void endReservation(OffsetDateTime endedAt) {
        if (!state.holdsReservation()) {
            throw new IllegalStateException(
                    "The current state does not hold a reservation");
        }

        if (reservedAmount == null
                || reservedAmount.compareTo(amount) != 0
                || reservedAt == null
                || reservationEndedAt != null) {

            throw new IllegalStateException(
                    "Transaction reservation is inconsistent");
        }

        OffsetDateTime timestamp = requireLifecycleTimestamp(
                endedAt,
                "reservationEndedAt");

        if (timestamp.isBefore(reservedAt)) {
            throw new IllegalArgumentException(
                    "reservationEndedAt cannot precede reservedAt");
        }

        reservedAmount = BigDecimal.ZERO.setScale(
                MoneyUtility.MONEY_SCALE);
        reservationEndedAt = timestamp;
        updatedAt = timestamp;
    }

    public void recordVerificationCompleted(
            OffsetDateTime completedAt) {

        if (state != TransactionState.VERIFICATION_REQUIRED) {
            throw new IllegalStateException(
                    "Verification can be completed only when required");
        }

        OffsetDateTime timestamp = requireLifecycleTimestamp(
                completedAt,
                "verificationCompletedAt");

        verificationCompletedAt = timestamp;
        updatedAt = timestamp;
    }

    public void applyValidatedTransition(
            TransactionState targetState,
            String reasonCode,
            OffsetDateTime occurredAt) {

        Objects.requireNonNull(targetState, "targetState is required");

        OffsetDateTime timestamp = requireLifecycleTimestamp(
                occurredAt,
                "occurredAt");

        String normalizedReason = normalizeReasonCode(
                targetState,
                reasonCode);

        if (state == TransactionState.PENDING_RISK_REVIEW
                && targetState == TransactionState.VERIFICATION_REQUIRED) {
            /* A new verification round must not reuse the prior OTP result. */
            verificationCompletedAt = null;
        }

        validateTargetStateInvariant(targetState);

        state = targetState;
        terminalReasonCode = normalizedReason;

        switch (targetState) {
            case AUTHORIZED -> authorizedAt = timestamp;
            case RELEASED -> releasedAt = timestamp;
            case SETTLED -> settledAt = timestamp;
            case CANCELLED -> cancelledAt = timestamp;
            case FAILED -> failedAt = timestamp;
            default -> {
                // The remaining states do not own a dedicated timestamp.
            }
        }

        updatedAt = timestamp;
    }

    private void validateTargetStateInvariant(
            TransactionState targetState) {

        boolean completeRiskSnapshot = riskPolicy != null
                && riskPolicyBand != null
                && protectionPolicy != null
                && riskTier != null
                && policyVersion != null
                && matchedBandCode != null
                && riskExplanation != null
                && riskAssessedAt != null
                && riskScore == null;

        boolean intactReservation = reservedAmount != null
                && reservedAmount.compareTo(amount) == 0
                && reservedAt != null
                && reservationEndedAt == null;

        boolean endedOrAbsentReservation = reservedAmount != null
                && reservedAmount.signum() == 0
                && ((reservedAt == null && reservationEndedAt == null)
                        || (reservedAt != null
                                && reservationEndedAt != null));

        if (targetState == TransactionState.CREATED
                || targetState == TransactionState.AUTHORIZED) {
            if (completeRiskSnapshot || !endedOrAbsentReservation) {
                throw new IllegalStateException(
                        "The target state requires no risk snapshot or reservation");
            }
            return;
        }

        if (targetState == TransactionState.RISK_ASSESSED) {
            if (!completeRiskSnapshot || !endedOrAbsentReservation) {
                throw new IllegalStateException(
                        "RISK_ASSESSED requires risk without a reservation");
            }
            return;
        }

        if (targetState.holdsReservation()) {
            if (!completeRiskSnapshot || !intactReservation) {
                throw new IllegalStateException(
                        "The target state requires risk and an intact reservation");
            }
        } else if (!endedOrAbsentReservation) {
            throw new IllegalStateException(
                    "The target state cannot retain reserved funds");
        }

        switch (targetState) {
            case PROTECTED -> {
                if ((riskTier != RiskTier.MEDIUM
                        && riskTier != RiskTier.HIGH)
                        || protectedUntil == null
                        || !protectedUntil.isAfter(reservedAt)) {
                    throw new IllegalStateException(
                            "PROTECTED requires a valid timed risk route");
                }
            }
            case VERIFICATION_REQUIRED -> {
                if (riskTier != RiskTier.VERY_HIGH
                        || protectedUntil != null
                        || verificationCompletedAt != null) {
                    throw new IllegalStateException(
                            "VERIFICATION_REQUIRED requires an unverified very-high-risk route");
                }
            }
            case PENDING_RISK_REVIEW -> {
                if (riskTier != RiskTier.VERY_HIGH
                        || protectedUntil != null
                        || verificationCompletedAt == null) {
                    throw new IllegalStateException(
                            "PENDING_RISK_REVIEW requires completed verification");
                }
            }
            case RELEASED -> validateReleasedRoute();
            default -> {
                // The remaining target states are covered above.
            }
        }
    }

    private void validateReleasedRoute() {
        boolean validRoute = switch (riskTier) {
            case LOW -> protectedUntil == null;
            case MEDIUM, HIGH -> protectedUntil != null;
            case VERY_HIGH -> verificationCompletedAt != null
                    && protectedUntil == null;
        };

        if (!validRoute) {
            throw new IllegalStateException(
                    "RELEASED does not match the assessed risk route");
        }
    }

    private long requirePositiveProtectionSeconds() {
        if (protectionSeconds == null || protectionSeconds <= 0L) {
            throw new IllegalStateException(
                    "A positive protection duration is required");
        }

        return protectionSeconds;
    }

    private String normalizeReasonCode(
            TransactionState targetState,
            String reasonCode) {

        boolean reasonRequired = targetState == TransactionState.CANCELLED
                || targetState == TransactionState.FAILED;

        if (!reasonRequired) {
            if (reasonCode != null && !reasonCode.isBlank()) {
                throw new IllegalArgumentException(
                        "A reason code is permitted only for failure or cancellation");
            }
            return null;
        }

        return requireText(
                reasonCode,
                "terminalReasonCode",
                64).toUpperCase(java.util.Locale.ROOT);
    }

    private OffsetDateTime requireLifecycleTimestamp(
            OffsetDateTime value,
            String fieldName) {

        OffsetDateTime timestamp = requireUtcTimestamp(value, fieldName);

        if (createdAt != null && timestamp.isBefore(createdAt)) {
            throw new IllegalArgumentException(
                    fieldName + " cannot precede createdAt");
        }

        if (updatedAt != null && timestamp.isBefore(updatedAt)) {
            throw new IllegalArgumentException(
                    fieldName + " cannot precede updatedAt");
        }

        return timestamp;
    }

    private static void requirePersistedCustomer(User customer) {
        Objects.requireNonNull(customer, "customer is required");

        if (customer.getUserId() == null
                || customer.getUserId() <= 0L) {
            throw new IllegalArgumentException(
                    "customer must already be persisted");
        }
    }

    private static void requireOwnedSourceAccount(
            User customer,
            Account sourceAccount) {

        Objects.requireNonNull(
                sourceAccount,
                "sourceAccount is required");

        if (sourceAccount.getAccountId() == null
                || sourceAccount.getAccountId() <= 0L) {
            throw new IllegalArgumentException(
                    "sourceAccount must already be persisted");
        }

        if (!sourceAccount.isCustomerOwnedAccount()
                || sourceAccount.getOwner() == null
                || !Objects.equals(
                        sourceAccount.getOwner().getUserId(),
                        customer.getUserId())) {

            throw new IllegalArgumentException(
                    "sourceAccount must belong to customer");
        }

        if (sourceAccount.getCurrencyCode() != CurrencyCode.INR) {
            throw new IllegalArgumentException(
                    "sourceAccount must use INR");
        }
    }

    private static void requireOwnedBeneficiary(
            User customer,
            Beneficiary beneficiary) {

        Objects.requireNonNull(
                beneficiary,
                "beneficiary is required");

        if (beneficiary.getBeneficiaryId() == null
                || beneficiary.getBeneficiaryId() <= 0L) {
            throw new IllegalArgumentException(
                    "beneficiary must already be persisted");
        }

        if (beneficiary.getOwner() == null
                || !Objects.equals(
                        beneficiary.getOwner().getUserId(),
                        customer.getUserId())) {

            throw new IllegalArgumentException(
                    "beneficiary must belong to customer");
        }
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

    private static String normalizeOptionalText(
            String value,
            String fieldName,
            int maximumLength) {

        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.trim();

        if (normalized.length() > maximumLength) {
            throw new IllegalArgumentException(
                    fieldName + " has an invalid length");
        }

        return normalized;
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

    public Long getTransactionId() {
        return transactionId;
    }

    public String getTransactionReference() {
        return transactionReference;
    }

    public User getCustomer() {
        return customer;
    }

    public Account getSourceAccount() {
        return sourceAccount;
    }

    public Beneficiary getBeneficiary() {
        return beneficiary;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public CurrencyCode getCurrencyCode() {
        return currencyCode;
    }

    public String getPurpose() {
        return purpose;
    }

    public String getCustomerReference() {
        return customerReference;
    }

    public TransactionState getState() {
        return state;
    }

    public String getTerminalReasonCode() {
        return terminalReasonCode;
    }

    public long getVersionNo() {
        return versionNo;
    }

    public BigDecimal getReservedAmount() {
        return reservedAmount;
    }

    public OffsetDateTime getReservedAt() {
        return reservedAt;
    }

    public OffsetDateTime getReservationEndedAt() {
        return reservationEndedAt;
    }

    public RiskPolicy getRiskPolicy() {
        return riskPolicy;
    }

    public RiskPolicyBand getRiskPolicyBand() {
        return riskPolicyBand;
    }

    public ProtectionPolicy getProtectionPolicy() {
        return protectionPolicy;
    }

    public RiskTier getRiskTier() {
        return riskTier;
    }

    public BigDecimal getRiskScore() {
        return riskScore;
    }

    public String getPolicyVersion() {
        return policyVersion;
    }

    public String getMatchedBandCode() {
        return matchedBandCode;
    }

    public Long getProtectionSeconds() {
        return protectionSeconds;
    }

    public String getRiskExplanation() {
        return riskExplanation;
    }

    public OffsetDateTime getRiskAssessedAt() {
        return riskAssessedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getAuthorizedAt() {
        return authorizedAt;
    }

    public OffsetDateTime getProtectedUntil() {
        return protectedUntil;
    }

    public OffsetDateTime getVerificationCompletedAt() {
        return verificationCompletedAt;
    }

    public OffsetDateTime getReleasedAt() {
        return releasedAt;
    }

    public OffsetDateTime getSettledAt() {
        return settledAt;
    }

    public OffsetDateTime getCancelledAt() {
        return cancelledAt;
    }

    public OffsetDateTime getFailedAt() {
        return failedAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
