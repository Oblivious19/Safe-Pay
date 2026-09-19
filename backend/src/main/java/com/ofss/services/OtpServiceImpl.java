package com.ofss.services;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ofss.beans.OtpChallenge;
import com.ofss.beans.OtpChallengeStatus;
import com.ofss.beans.AuditOutcome;
import com.ofss.beans.RiskTier;
import com.ofss.beans.RoleName;
import com.ofss.beans.TransactionDb;
import com.ofss.beans.TransactionState;
import com.ofss.common.SensitiveDataMasker;
import com.ofss.dto.otp.OtpChallengeResponse;
import com.ofss.dto.otp.OtpVerificationResponse;
import com.ofss.excp.BusinessRuleException;
import com.ofss.excp.ResourceNotFoundExcp;
import com.ofss.repository.OtpChallengeDao;
import com.ofss.repository.TransactionDao;

import jakarta.persistence.EntityManager;

@Service
@Transactional(readOnly = true)
public class OtpServiceImpl implements OtpService {

    private static final String OTP_POLICY_EXHAUSTED =
            "OTP_POLICY_EXHAUSTED";

    private final TransactionDao transactionDao;
    private final OtpChallengeDao otpChallengeDao;
    private final OtpCodeGenerator codeGenerator;
    private final OtpHashingService hashingService;
    private final OtpDeliveryGateway deliveryGateway;
    private final OtpEmailProperties otpEmailProperties;
    private final OtpPolicyProperties policy;
    private final AccountFundsService accountFundsService;
    private final TransactionStateService stateService;
    private final RiskReviewService riskReviewService;
    private final TransactionLifecycleEvidenceService evidenceService;
    private final EntityManager entityManager;

    public OtpServiceImpl(
            TransactionDao transactionDao,
            OtpChallengeDao otpChallengeDao,
            OtpCodeGenerator codeGenerator,
            OtpHashingService hashingService,
            OtpDeliveryGateway deliveryGateway,
            OtpEmailProperties otpEmailProperties,
            OtpPolicyProperties policy,
            AccountFundsService accountFundsService,
            TransactionStateService stateService,
            RiskReviewService riskReviewService,
            TransactionLifecycleEvidenceService evidenceService,
            EntityManager entityManager) {

        this.transactionDao = Objects.requireNonNull(
                transactionDao,
                "transactionDao is required");
        this.otpChallengeDao = Objects.requireNonNull(
                otpChallengeDao,
                "otpChallengeDao is required");
        this.codeGenerator = Objects.requireNonNull(
                codeGenerator,
                "codeGenerator is required");
        this.hashingService = Objects.requireNonNull(
                hashingService,
                "hashingService is required");
        this.deliveryGateway = Objects.requireNonNull(
                deliveryGateway,
                "deliveryGateway is required");
        this.otpEmailProperties = Objects.requireNonNull(
                otpEmailProperties,
                "otpEmailProperties is required");
        this.policy = Objects.requireNonNull(
                policy,
                "policy is required");
        this.accountFundsService = Objects.requireNonNull(
                accountFundsService,
                "accountFundsService is required");
        this.stateService = Objects.requireNonNull(
                stateService,
                "stateService is required");
        this.riskReviewService = Objects.requireNonNull(
                riskReviewService,
                "riskReviewService is required");
        this.evidenceService = Objects.requireNonNull(
                evidenceService,
                "evidenceService is required");
        this.entityManager = Objects.requireNonNull(
                entityManager,
                "entityManager is required");
    }

    @Override
    @Transactional
    public OtpChallengeResult issue(
            Long customerUserId,
            Long transactionId) {

        return issue(
                customerUserId,
                transactionId,
                OperationContext.internal());
    }

    @Override
    @Transactional
    public OtpChallengeResult issue(
            Long customerUserId,
            Long transactionId,
            OperationContext context) {

        TransactionDb transaction = requireEligibleOwnedTransaction(
                customerUserId,
                transactionId);
        OffsetDateTime now = currentDatabaseTime();
        OffsetDateTime cycleStartedAt = cycleStartedAt(transaction);
        long issueCount = issueCount(
                transaction,
                customerUserId,
                cycleStartedAt);

        if (issueCount != 0L) {
            throw new BusinessRuleException(
                    "OTP_ALREADY_ISSUED",
                    "An OTP challenge already exists for this verification cycle");
        }

        String email = requireRecipientEmail(transaction);

        return OtpChallengeResult.accepted(
                createAndDeliver(
                        transaction,
                        email,
                        now,
                        issueCount,
                        TransactionLifecycleEvent.OTP_ISSUED,
                        requireContext(context)));
    }

    @Override
    @Transactional
    public OtpChallengeResult resend(
            Long customerUserId,
            Long transactionId) {

        return resend(
                customerUserId,
                transactionId,
                OperationContext.internal());
    }

    @Override
    @Transactional
    public OtpChallengeResult resend(
            Long customerUserId,
            Long transactionId,
            OperationContext context) {

        TransactionDb transaction = requireEligibleOwnedTransaction(
                customerUserId,
                transactionId);
        OffsetDateTime now = currentDatabaseTime();
        OffsetDateTime cycleStartedAt = cycleStartedAt(transaction);

        OtpChallenge latest = otpChallengeDao
                .findLatestOwnedForUpdate(
                        transactionId,
                        customerUserId)
                .filter(challenge -> !challenge.getCreatedAt()
                        .isBefore(cycleStartedAt))
                .orElseThrow(
                        OtpServiceImpl::challengeNotFound);

        if (now.isBefore(latest.getCreatedAt().plus(
                policy.resendCooldown()))) {
            throw new BusinessRuleException(
                    "OTP_RESEND_COOLDOWN",
                    "OTP resend is temporarily unavailable");
        }

        long issueCount = issueCount(
                transaction,
                customerUserId,
                cycleStartedAt);

        if (issueCount >= policy.maxIssuesPerCycle()) {
            invalidatePending(latest, now);
            cancelForPolicyExhaustion(
                    transaction,
                    now,
                    requireContext(context),
                    "OTP-ISSUE-EXHAUSTED-"
                            + latest.getOtpChallengeId());

            return OtpChallengeResult.rejected(
                    response(
                            latest,
                            transaction,
                            issueCount,
                            now),
                    OTP_POLICY_EXHAUSTED,
                    "OTP issue limit is exhausted");
        }

        invalidatePending(latest, now);

        String email = requireRecipientEmail(transaction);

        return OtpChallengeResult.accepted(
                createAndDeliver(
                        transaction,
                        email,
                        now,
                        issueCount,
                        TransactionLifecycleEvent.OTP_RESENT,
                        requireContext(context)));
    }

    @Override
    @Transactional
    public OtpVerificationResult verify(
            Long customerUserId,
            Long transactionId,
            Long challengeId,
            String otp) {

        return verify(
                customerUserId,
                transactionId,
                challengeId,
                otp,
                OperationContext.internal());
    }

    @Override
    @Transactional
    public OtpVerificationResult verify(
            Long customerUserId,
            Long transactionId,
            Long challengeId,
            String otp,
            OperationContext context) {

        requirePositiveId(challengeId, "challengeId");
        TransactionDb transaction = requireEligibleOwnedTransaction(
                customerUserId,
                transactionId);
        OffsetDateTime now = currentDatabaseTime();

        OtpChallenge challenge = otpChallengeDao
                .findOwnedByIdForUpdate(
                        challengeId,
                        transactionId,
                        customerUserId)
                .orElseThrow(
                        OtpServiceImpl::challengeNotFound);

        if (challenge.getStatus() != OtpChallengeStatus.PENDING) {
            return rejectedVerification(
                    challenge,
                    transaction,
                    now,
                    "OTP_CHALLENGE_NOT_USABLE",
                    "The OTP challenge is no longer usable",
                    requireContext(context));
        }

        if (challenge.isExpiredAt(now)) {
            challenge.expire(now);

            long issueCount = issueCount(
                    transaction,
                    customerUserId,
                    cycleStartedAt(transaction));

            if (issueCount >= policy.maxIssuesPerCycle()) {
                cancelForPolicyExhaustion(
                        transaction,
                        now,
                        requireContext(context),
                        "OTP-EXPIRED-EXHAUSTED-"
                                + challenge.getOtpChallengeId());

                return rejectedVerification(
                        challenge,
                        transaction,
                        now,
                        OTP_POLICY_EXHAUSTED,
                        "OTP policy is exhausted",
                        requireContext(context));
            }

            entityManager.flush();
            return rejectedVerification(
                    challenge,
                    transaction,
                    now,
                    "OTP_EXPIRED",
                    "The OTP challenge has expired",
                    requireContext(context));
        }

        OtpCode candidate = new OtpCode(
                requireOtp(otp),
                policy.codeLength());

        if (!hashingService.matches(
                candidate,
                challenge.getOtpHash())) {

            OtpChallengeStatus status =
                    challenge.recordFailedAttempt(now);

            if (status == OtpChallengeStatus.LOCKED) {
                cancelForPolicyExhaustion(
                        transaction,
                        now,
                        requireContext(context),
                        "OTP-ATTEMPT-EXHAUSTED-"
                                + challenge.getOtpChallengeId());

                return rejectedVerification(
                        challenge,
                        transaction,
                        now,
                        OTP_POLICY_EXHAUSTED,
                        "OTP attempt limit is exhausted",
                        requireContext(context));
            }

            entityManager.flush();
            return rejectedVerification(
                    challenge,
                    transaction,
                    now,
                    "OTP_INVALID",
                    "The OTP code is incorrect",
                    requireContext(context));
        }

        challenge.markVerified(now);
        transaction.recordVerificationCompleted(now);
        stateService.transition(
                transaction,
                TransactionState.PENDING_RISK_REVIEW,
                now);
        riskReviewService.openNextRound(transaction, now);
        String occurrence = "CHALLENGE-"
                + challenge.getOtpChallengeId();
        evidenceService.appendUserEvent(
                TransactionLifecycleEvent.OTP_VERIFIED,
                transaction,
                transaction.getCustomer(),
                RoleName.CUSTOMER,
                TransactionState.VERIFICATION_REQUIRED,
                TransactionState.PENDING_RISK_REVIEW,
                AuditOutcome.SUCCESS,
                null,
                requireContext(context),
                occurrence,
                now);
        evidenceService.appendUserEvent(
                TransactionLifecycleEvent.RISK_REVIEW_PENDING,
                transaction,
                transaction.getCustomer(),
                RoleName.CUSTOMER,
                TransactionState.PENDING_RISK_REVIEW,
                TransactionState.PENDING_RISK_REVIEW,
                AuditOutcome.SUCCESS,
                null,
                requireContext(context),
                occurrence,
                now);
        entityManager.flush();

        return OtpVerificationResult.verified(
                OtpVerificationResponse.from(
                        challenge,
                        transaction,
                        true,
                        now));
    }

    private OtpChallengeResponse createAndDeliver(
            TransactionDb transaction,
            String email,
            OffsetDateTime now,
            long existingIssueCount,
            TransactionLifecycleEvent event,
            OperationContext context) {

        OtpCode code = codeGenerator.generate();
        String encodedHash = hashingService.hash(code);

        OtpChallenge challenge = OtpChallenge.issue(
                transaction,
                encodedHash,
                policy,
                now);

        OtpChallenge persisted = otpChallengeDao.saveAndFlush(
                challenge);

        deliveryGateway.deliver(
                email,
                code,
                persisted.getExpiresAt());

        evidenceService.appendUserEvent(
                event,
                transaction,
                transaction.getCustomer(),
                RoleName.CUSTOMER,
                TransactionState.VERIFICATION_REQUIRED,
                TransactionState.VERIFICATION_REQUIRED,
                AuditOutcome.SUCCESS,
                null,
                context,
                "CHALLENGE-" + persisted.getOtpChallengeId(),
                now);

        return OtpChallengeResponse.from(
                persisted,
                SensitiveDataMasker.maskEmail(email),
                policy.resendCooldown(),
                policy.maxIssuesPerCycle()
                        - Math.toIntExact(existingIssueCount + 1L),
                now);
    }

    private OtpChallengeResponse response(
            OtpChallenge challenge,
            TransactionDb transaction,
            long issueCount,
            OffsetDateTime now) {

        return OtpChallengeResponse.from(
                challenge,
                SensitiveDataMasker.maskEmail(
                        requireRecipientEmail(transaction)),
                policy.resendCooldown(),
                Math.max(
                        0,
                        policy.maxIssuesPerCycle()
                                - Math.toIntExact(issueCount)),
                now);
    }

    private OtpVerificationResult rejectedVerification(
            OtpChallenge challenge,
            TransactionDb transaction,
            OffsetDateTime now,
            String errorCode,
            String message,
            OperationContext context) {

        evidenceService.appendUserEvent(
                TransactionLifecycleEvent.OTP_VERIFICATION_DENIED,
                transaction,
                transaction.getCustomer(),
                RoleName.CUSTOMER,
                TransactionState.VERIFICATION_REQUIRED,
                transaction.getState(),
                AuditOutcome.DENIED,
                errorCode,
                context,
                "CHALLENGE-"
                        + challenge.getOtpChallengeId()
                        + "-REQUEST-"
                        + evidenceRequestIdentity(context),
                now);

        return OtpVerificationResult.rejected(
                OtpVerificationResponse.from(
                        challenge,
                        transaction,
                        false,
                        now),
                errorCode,
                message);
    }

    private TransactionDb requireEligibleOwnedTransaction(
            Long customerUserId,
            Long transactionId) {

        requirePositiveId(customerUserId, "customerUserId");
        requirePositiveId(transactionId, "transactionId");

        TransactionDb transaction = transactionDao
                .findOwnedByIdForUpdate(
                        transactionId,
                        customerUserId)
                .orElseThrow(
                        OtpServiceImpl::transactionNotFound);

        boolean intactReservation = transaction.getReservedAmount()
                .compareTo(transaction.getAmount()) == 0
                && transaction.getReservedAmount()
                        .compareTo(BigDecimal.ZERO) > 0
                && transaction.getReservedAt() != null
                && transaction.getReservationEndedAt() == null;

        if (transaction.getState()
                        != TransactionState.VERIFICATION_REQUIRED
                || transaction.getRiskTier() != RiskTier.VERY_HIGH
                || !intactReservation) {
            throw new BusinessRuleException(
                    "OTP_NOT_AVAILABLE",
                    "OTP verification is not available for this transaction");
        }

        return transaction;
    }

    private void cancelForPolicyExhaustion(
            TransactionDb transaction,
            OffsetDateTime now,
            OperationContext context,
            String occurrenceIdentity) {

        Long sourceAccountId = Objects.requireNonNull(
                        transaction.getSourceAccount(),
                        "transaction sourceAccount is required")
                .getAccountId();

        accountFundsService.releaseReservedFunds(
                sourceAccountId,
                transaction.getAmount(),
                now);
        transaction.endReservation(now);
        stateService.transition(
                transaction,
                TransactionState.CANCELLED,
                OTP_POLICY_EXHAUSTED,
                now);
        evidenceService.appendUserEvent(
                TransactionLifecycleEvent.PAYMENT_CANCELLED,
                transaction,
                transaction.getCustomer(),
                RoleName.CUSTOMER,
                TransactionState.VERIFICATION_REQUIRED,
                TransactionState.CANCELLED,
                AuditOutcome.FAILED,
                OTP_POLICY_EXHAUSTED,
                context,
                occurrenceIdentity,
                now);
        entityManager.flush();
    }

    private static void invalidatePending(
            OtpChallenge challenge,
            OffsetDateTime now) {

        if (challenge.getStatus() != OtpChallengeStatus.PENDING) {
            return;
        }

        if (challenge.isExpiredAt(now)) {
            challenge.expire(now);
        } else {
            challenge.cancel(now);
        }
    }

    private long issueCount(
            TransactionDb transaction,
            Long customerUserId,
            OffsetDateTime cycleStartedAt) {

        return otpChallengeDao
                .countByTransaction_TransactionIdAndCustomer_UserIdAndCreatedAtGreaterThanEqual(
                        transaction.getTransactionId(),
                        customerUserId,
                        cycleStartedAt);
    }

    private static OffsetDateTime cycleStartedAt(
            TransactionDb transaction) {

        return Objects.requireNonNull(
                transaction.getUpdatedAt(),
                "transaction updatedAt is required");
    }

    private String requireRecipientEmail(
            TransactionDb transaction) {

        String email = Objects.requireNonNull(
                        transaction.getCustomer(),
                        "transaction customer is required")
                .getEmail();

        try {
            SensitiveDataMasker.maskEmail(email);
        } catch (IllegalArgumentException exception) {
            throw new BusinessRuleException(
                    "OTP_EMAIL_UNAVAILABLE",
                    "A valid stored email is required for OTP delivery");
        }

        return otpEmailProperties.resolveRecipient(email);
    }

    private static String requireOtp(String value) {
        if (value == null
                || value.length() != OtpPolicyProperties.APPROVED_CODE_LENGTH
                || !value.chars().allMatch(character ->
                        character >= '0' && character <= '9')) {
            throw new IllegalArgumentException(
                    "otp must contain exactly 6 numeric digits");
        }

        return value;
    }

    private static OperationContext requireContext(
            OperationContext context) {
        return Objects.requireNonNull(context, "context is required");
    }

    private static String evidenceRequestIdentity(
            OperationContext context) {
        OperationContext validated = requireContext(context);
        return validated.idempotencyKey() == null
                ? validated.correlationId()
                : validated.idempotencyKey();
    }

    private OffsetDateTime currentDatabaseTime() {
        return Objects.requireNonNull(
                        transactionDao.currentDatabaseTime(),
                        "database time is required")
                .withOffsetSameInstant(ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.MICROS);
    }

    private static void requirePositiveId(
            Long value,
            String fieldName) {

        if (value == null || value <= 0L) {
            throw new IllegalArgumentException(
                    fieldName + " must be positive");
        }
    }

    private static ResourceNotFoundExcp transactionNotFound() {
        return new ResourceNotFoundExcp(
                "TRANSACTION_NOT_FOUND",
                "Transaction was not found");
    }

    private static ResourceNotFoundExcp challengeNotFound() {
        return new ResourceNotFoundExcp(
                "OTP_CHALLENGE_NOT_FOUND",
                "OTP challenge was not found");
    }
}
