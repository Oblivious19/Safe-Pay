package com.ofss.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import com.ofss.beans.Account;
import com.ofss.beans.AuditOutcome;
import com.ofss.beans.Beneficiary;
import com.ofss.beans.BeneficiaryPaymentMethod;
import com.ofss.beans.CurrencyCode;
import com.ofss.beans.OtpChallenge;
import com.ofss.beans.OtpChallengeStatus;
import com.ofss.beans.ProtectionPolicy;
import com.ofss.beans.ProtectionReleaseMode;
import com.ofss.beans.RiskPolicy;
import com.ofss.beans.RiskPolicyBand;
import com.ofss.beans.RiskTier;
import com.ofss.beans.RoleName;
import com.ofss.beans.TransactionDb;
import com.ofss.beans.TransactionState;
import com.ofss.beans.User;
import com.ofss.dto.risk.RiskEvaluationResult;
import com.ofss.dto.risk.RiskPolicySnapshot;
import com.ofss.excp.BusinessRuleException;
import com.ofss.excp.ResourceNotFoundExcp;
import com.ofss.repository.OtpChallengeDao;
import com.ofss.repository.TransactionDao;

import jakarta.persistence.EntityManager;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OtpServiceImplTest {

    private static final Long CUSTOMER_ID = 7L;
    private static final Long TRANSACTION_ID = 101L;
    private static final Long ACCOUNT_ID = 71L;
    private static final Long CHALLENGE_ID = 501L;
    private static final OffsetDateTime NOW =
            OffsetDateTime.parse("2026-09-16T12:00:00Z");
    private static final OffsetDateTime CYCLE_STARTED_AT =
            NOW.minusMinutes(2);

    @Mock private TransactionDao transactionDao;
    @Mock private OtpChallengeDao otpChallengeDao;
    @Mock private OtpCodeGenerator codeGenerator;
    @Mock private OtpHashingService hashingService;
    @Mock private OtpDeliveryGateway deliveryGateway;
    @Mock private AccountFundsService accountFundsService;
    @Mock private RiskReviewService riskReviewService;
    @Mock private TransactionLifecycleEvidenceService evidenceService;
    @Mock private EntityManager entityManager;
    @Mock private User customer;
    @Mock private Account sourceAccount;
    @Mock private Beneficiary beneficiary;
    @Mock private RiskPolicy riskPolicy;
    @Mock private RiskPolicyBand riskBand;
    @Mock private ProtectionPolicy protectionPolicy;

    private OtpPolicyProperties policy;
    private OtpEmailProperties emailProperties;
    private TransactionStateService stateService;
    private OtpService service;
    private TransactionDb transaction;

    @BeforeEach
    void setUp() {
        policy = new OtpPolicyProperties(
                Duration.ofMinutes(5),
                3,
                Duration.ofSeconds(30),
                3,
                6);
        emailProperties = new OtpEmailProperties(
                true,
                "safepay-dev@gmail.com",
                OtpEmailRoutingMode.FIXED_OVERRIDE,
                "otp-receiver@gmail.com");
        stateService = new TransactionStateServiceImpl();

        transaction = verificationRequiredTransaction(
                "customer@example.com");

        lenient().when(transactionDao.findOwnedByIdForUpdate(
                TRANSACTION_ID,
                CUSTOMER_ID))
                .thenReturn(Optional.of(transaction));
        lenient().when(transactionDao.currentDatabaseTime())
                .thenReturn(NOW);
        lenient().when(codeGenerator.generate())
                .thenReturn(new OtpCode("123456", 6));
        lenient().when(hashingService.hash(any(OtpCode.class)))
                .thenReturn("SHA-256$test-salt$test-digest");
        lenient().when(otpChallengeDao
                .countByTransaction_TransactionIdAndCustomer_UserIdAndCreatedAtGreaterThanEqual(
                        TRANSACTION_ID,
                        CUSTOMER_ID,
                        CYCLE_STARTED_AT))
                .thenReturn(0L);
        lenient().when(otpChallengeDao.saveAndFlush(
                any(OtpChallenge.class)))
                .thenAnswer(invocation -> {
                    OtpChallenge challenge = invocation.getArgument(0);
                    ReflectionTestUtils.setField(
                            challenge,
                            "otpChallengeId",
                            CHALLENGE_ID);
                    return challenge;
                });

        service = new OtpServiceImpl(
                transactionDao,
                otpChallengeDao,
                codeGenerator,
                hashingService,
                deliveryGateway,
                emailProperties,
                policy,
                accountFundsService,
                stateService,
                riskReviewService,
                evidenceService,
                entityManager);
    }

    @Test
    void issuesAndDeliversOnlyToFixedOverride() {
        OtpChallengeResult result = service.issue(
                CUSTOMER_ID,
                TRANSACTION_ID);

        assertThat(result.accepted()).isTrue();
        assertThat(result.response().challengeId()).isEqualTo("501");
        assertThat(result.response().transactionId()).isEqualTo("101");
        assertThat(result.response().maskedDestination())
                .isEqualTo("o***********@gmail.com");
        assertThat(result.response().expiresAt())
                .isEqualTo(NOW.plusMinutes(5));
        assertThat(result.response().resendAvailableAt())
                .isEqualTo(NOW.plusSeconds(30));
        assertThat(result.response().remainingIssues()).isEqualTo(2);

        verify(deliveryGateway).deliver(
                eq("otp-receiver@gmail.com"),
                any(OtpCode.class),
                eq(NOW.plusMinutes(5)));
        verify(evidenceService).appendUserEvent(
                eq(TransactionLifecycleEvent.OTP_ISSUED),
                eq(transaction),
                eq(customer),
                eq(RoleName.CUSTOMER),
                eq(TransactionState.VERIFICATION_REQUIRED),
                eq(TransactionState.VERIFICATION_REQUIRED),
                eq(AuditOutcome.SUCCESS),
                org.mockito.ArgumentMatchers.isNull(),
                any(OperationContext.class),
                eq("CHALLENGE-501"),
                eq(NOW));
    }

    @Test
    void rejectsMobileOnlyCustomerBeforeGeneratingSecret() {
        when(customer.getEmail()).thenReturn(null);

        assertThatThrownBy(() -> service.issue(
                CUSTOMER_ID,
                TRANSACTION_ID))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("errorCode")
                .isEqualTo("OTP_EMAIL_UNAVAILABLE");

        verifyNoInteractions(codeGenerator, deliveryGateway);
    }

    @Test
    void hidesUnownedTransactionAsNotFound() {
        when(transactionDao.findOwnedByIdForUpdate(
                TRANSACTION_ID,
                CUSTOMER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.issue(
                CUSTOMER_ID,
                TRANSACTION_ID))
                .isInstanceOf(ResourceNotFoundExcp.class)
                .extracting("errorCode")
                .isEqualTo("TRANSACTION_NOT_FOUND");

        verifyNoInteractions(codeGenerator, deliveryGateway);
    }

    @Test
    void rejectsInitialIssueWhenCurrentCycleAlreadyHasChallenge() {
        arrangeIssueCount(1L);

        assertThatThrownBy(() -> service.issue(
                CUSTOMER_ID,
                TRANSACTION_ID))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("errorCode")
                .isEqualTo("OTP_ALREADY_ISSUED");
    }

    @Test
    void resendEnforcesThirtySecondCooldown() {
        OtpChallenge current = challengeAt(
                NOW.minusSeconds(29),
                CHALLENGE_ID);
        when(otpChallengeDao.findLatestOwnedForUpdate(
                TRANSACTION_ID,
                CUSTOMER_ID))
                .thenReturn(Optional.of(current));

        assertThatThrownBy(() -> service.resend(
                CUSTOMER_ID,
                TRANSACTION_ID))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("errorCode")
                .isEqualTo("OTP_RESEND_COOLDOWN");

        assertThat(current.getStatus())
                .isEqualTo(OtpChallengeStatus.PENDING);
        verify(deliveryGateway, never()).deliver(
                any(),
                any(),
                any());
    }

    @Test
    void resendImmediatelyInvalidatesPriorPendingChallenge() {
        OtpChallenge current = challengeAt(
                NOW.minusSeconds(31),
                CHALLENGE_ID);
        when(otpChallengeDao.findLatestOwnedForUpdate(
                TRANSACTION_ID,
                CUSTOMER_ID))
                .thenReturn(Optional.of(current));
        arrangeIssueCount(1L);

        OtpChallengeResult result = service.resend(
                CUSTOMER_ID,
                TRANSACTION_ID);

        assertThat(current.getStatus())
                .isEqualTo(OtpChallengeStatus.CANCELLED);
        assertThat(current.getInvalidatedAt()).isEqualTo(NOW);
        assertThat(result.accepted()).isTrue();
        assertThat(result.response().remainingIssues()).isEqualTo(1);
        verify(deliveryGateway).deliver(
                eq("otp-receiver@gmail.com"),
                any(OtpCode.class),
                eq(NOW.plusMinutes(5)));
    }

    @Test
    void resendMarksExpiredPriorChallengeBeforeReplacement() {
        OffsetDateTime longRunningCycleStartedAt =
                NOW.minusMinutes(10);
        ReflectionTestUtils.setField(
                transaction,
                "updatedAt",
                longRunningCycleStartedAt);

        OtpChallenge expired = challengeAt(
                NOW.minusMinutes(6),
                CHALLENGE_ID);
        when(otpChallengeDao.findLatestOwnedForUpdate(
                TRANSACTION_ID,
                CUSTOMER_ID))
                .thenReturn(Optional.of(expired));
        when(otpChallengeDao
                .countByTransaction_TransactionIdAndCustomer_UserIdAndCreatedAtGreaterThanEqual(
                        TRANSACTION_ID,
                        CUSTOMER_ID,
                        longRunningCycleStartedAt))
                .thenReturn(1L);

        service.resend(CUSTOMER_ID, TRANSACTION_ID);

        assertThat(expired.getStatus())
                .isEqualTo(OtpChallengeStatus.EXPIRED);
        assertThat(expired.getInvalidatedAt()).isEqualTo(NOW);
    }

    @Test
    void fourthIssueRequestCancelsTransactionAndReleasesReservation() {
        OtpChallenge current = challengeAt(
                NOW.minusSeconds(31),
                CHALLENGE_ID);
        when(otpChallengeDao.findLatestOwnedForUpdate(
                TRANSACTION_ID,
                CUSTOMER_ID))
                .thenReturn(Optional.of(current));
        arrangeIssueCount(3L);

        OtpChallengeResult result = service.resend(
                CUSTOMER_ID,
                TRANSACTION_ID);

        assertThat(result.accepted()).isFalse();
        assertThat(result.errorCode())
                .isEqualTo("OTP_POLICY_EXHAUSTED");
        assertThat(current.getStatus())
                .isEqualTo(OtpChallengeStatus.CANCELLED);
        assertThat(transaction.getState())
                .isEqualTo(TransactionState.CANCELLED);
        assertThat(transaction.getReservedAmount())
                .isEqualByComparingTo("0.00");
        verify(accountFundsService).releaseReservedFunds(
                ACCOUNT_ID,
                new BigDecimal("125000.00"),
                NOW);
        verify(deliveryGateway, never()).deliver(
                any(),
                any(),
                any());
    }

    @Test
    void correctOtpMovesToPendingReviewWithoutReleasingFunds() {
        OtpChallenge challenge = challengeAt(
                NOW.minusMinutes(1),
                CHALLENGE_ID);
        arrangeChallengeLookup(challenge);
        when(hashingService.matches(
                any(OtpCode.class),
                eq(challenge.getOtpHash())))
                .thenReturn(true);

        OtpVerificationResult result = service.verify(
                CUSTOMER_ID,
                TRANSACTION_ID,
                CHALLENGE_ID,
                "123456");

        assertThat(result.verified()).isTrue();
        assertThat(challenge.getStatus())
                .isEqualTo(OtpChallengeStatus.VERIFIED);
        assertThat(transaction.getState())
                .isEqualTo(TransactionState.PENDING_RISK_REVIEW);
        assertThat(transaction.getVerificationCompletedAt())
                .isEqualTo(NOW);
        assertThat(transaction.getReservedAmount())
                .isEqualByComparingTo("125000.00");
        verify(riskReviewService).openNextRound(
                transaction,
                NOW);
        verify(evidenceService).appendUserEvent(
                eq(TransactionLifecycleEvent.OTP_VERIFIED),
                eq(transaction),
                eq(customer),
                eq(RoleName.CUSTOMER),
                eq(TransactionState.VERIFICATION_REQUIRED),
                eq(TransactionState.PENDING_RISK_REVIEW),
                eq(AuditOutcome.SUCCESS),
                org.mockito.ArgumentMatchers.isNull(),
                any(OperationContext.class),
                eq("CHALLENGE-501"),
                eq(NOW));
        verify(evidenceService).appendUserEvent(
                eq(TransactionLifecycleEvent.RISK_REVIEW_PENDING),
                eq(transaction),
                eq(customer),
                eq(RoleName.CUSTOMER),
                eq(TransactionState.PENDING_RISK_REVIEW),
                eq(TransactionState.PENDING_RISK_REVIEW),
                eq(AuditOutcome.SUCCESS),
                org.mockito.ArgumentMatchers.isNull(),
                any(OperationContext.class),
                eq("CHALLENGE-501"),
                eq(NOW));
        verifyNoInteractions(accountFundsService);
    }

    @Test
    void incorrectOtpPersistsAttemptAndReturnsSafeRemainingCount() {
        OtpChallenge challenge = challengeAt(
                NOW.minusMinutes(1),
                CHALLENGE_ID);
        arrangeChallengeLookup(challenge);
        when(hashingService.matches(any(), any()))
                .thenReturn(false);

        OtpVerificationResult result = service.verify(
                CUSTOMER_ID,
                TRANSACTION_ID,
                CHALLENGE_ID,
                "000000");

        assertThat(result.verified()).isFalse();
        assertThat(result.errorCode()).isEqualTo("OTP_INVALID");
        assertThat(result.response().remainingAttempts()).isEqualTo(2);
        assertThat(challenge.getAttemptCount()).isEqualTo(1);
        assertThat(transaction.getState())
                .isEqualTo(TransactionState.VERIFICATION_REQUIRED);
        verifyNoInteractions(accountFundsService);
    }

    @Test
    void thirdIncorrectOtpLocksChallengeAndCancelsTransaction() {
        OtpChallenge challenge = challengeAt(
                NOW.minusMinutes(1),
                CHALLENGE_ID);
        challenge.recordFailedAttempt(NOW.minusSeconds(2));
        challenge.recordFailedAttempt(NOW.minusSeconds(1));
        arrangeChallengeLookup(challenge);
        when(hashingService.matches(any(), any()))
                .thenReturn(false);

        OtpVerificationResult result = service.verify(
                CUSTOMER_ID,
                TRANSACTION_ID,
                CHALLENGE_ID,
                "000000");

        assertThat(result.errorCode())
                .isEqualTo("OTP_POLICY_EXHAUSTED");
        assertThat(result.response().remainingAttempts()).isZero();
        assertThat(challenge.getStatus())
                .isEqualTo(OtpChallengeStatus.LOCKED);
        assertThat(transaction.getState())
                .isEqualTo(TransactionState.CANCELLED);
        verify(accountFundsService).releaseReservedFunds(
                ACCOUNT_ID,
                new BigDecimal("125000.00"),
                NOW);
    }

    @Test
    void expiredChallengeAllowsResendWhenIssueLimitRemains() {
        OtpChallenge challenge = challengeAt(
                NOW.minusMinutes(6),
                CHALLENGE_ID);
        arrangeChallengeLookup(challenge);
        arrangeIssueCount(2L);

        OtpVerificationResult result = service.verify(
                CUSTOMER_ID,
                TRANSACTION_ID,
                CHALLENGE_ID,
                "123456");

        assertThat(result.errorCode()).isEqualTo("OTP_EXPIRED");
        assertThat(challenge.getStatus())
                .isEqualTo(OtpChallengeStatus.EXPIRED);
        assertThat(transaction.getState())
                .isEqualTo(TransactionState.VERIFICATION_REQUIRED);
    }

    @Test
    void expiredFinalChallengeExhaustsPolicyAndCancels() {
        OtpChallenge challenge = challengeAt(
                NOW.minusMinutes(6),
                CHALLENGE_ID);
        arrangeChallengeLookup(challenge);
        arrangeIssueCount(3L);

        OtpVerificationResult result = service.verify(
                CUSTOMER_ID,
                TRANSACTION_ID,
                CHALLENGE_ID,
                "123456");

        assertThat(result.errorCode())
                .isEqualTo("OTP_POLICY_EXHAUSTED");
        assertThat(transaction.getState())
                .isEqualTo(TransactionState.CANCELLED);
        verify(accountFundsService).releaseReservedFunds(
                ACCOUNT_ID,
                new BigDecimal("125000.00"),
                NOW);
    }

    @Test
    void terminalChallengeCannotBeVerifiedOrReused() {
        OtpChallenge challenge = challengeAt(
                NOW.minusMinutes(1),
                CHALLENGE_ID);
        challenge.markVerified(NOW.minusSeconds(1));
        arrangeChallengeLookup(challenge);

        OtpVerificationResult result = service.verify(
                CUSTOMER_ID,
                TRANSACTION_ID,
                CHALLENGE_ID,
                "123456");

        assertThat(result.errorCode())
                .isEqualTo("OTP_CHALLENGE_NOT_USABLE");
        verify(hashingService, never()).matches(any(), any());
    }

    @Test
    void deliveryFailurePropagatesWithoutClaimingAcceptance() {
        doThrow(new OtpDeliveryException(
                "OTP email could not be delivered"))
                .when(deliveryGateway)
                .deliver(any(), any(), any());

        assertThatThrownBy(() -> service.issue(
                CUSTOMER_ID,
                TRANSACTION_ID))
                .isInstanceOf(OtpDeliveryException.class)
                .hasMessage("OTP email could not be delivered");
    }

    @Test
    void rejectsChallengeThatIsNotOwnedByTransactionCustomer() {
        when(otpChallengeDao.findOwnedByIdForUpdate(
                CHALLENGE_ID,
                TRANSACTION_ID,
                CUSTOMER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verify(
                CUSTOMER_ID,
                TRANSACTION_ID,
                CHALLENGE_ID,
                "123456"))
                .isInstanceOf(ResourceNotFoundExcp.class)
                .extracting("errorCode")
                .isEqualTo("OTP_CHALLENGE_NOT_FOUND");
    }

    @Test
    void resendUsesCurrentVerificationCycleOnly() {
        OtpChallenge oldCycle = challengeAt(
                CYCLE_STARTED_AT.minusMinutes(1),
                CHALLENGE_ID);
        when(otpChallengeDao.findLatestOwnedForUpdate(
                TRANSACTION_ID,
                CUSTOMER_ID))
                .thenReturn(Optional.of(oldCycle));

        assertThatThrownBy(() -> service.resend(
                CUSTOMER_ID,
                TRANSACTION_ID))
                .isInstanceOf(ResourceNotFoundExcp.class)
                .extracting("errorCode")
                .isEqualTo("OTP_CHALLENGE_NOT_FOUND");
    }

    private void arrangeChallengeLookup(OtpChallenge challenge) {
        when(otpChallengeDao.findOwnedByIdForUpdate(
                CHALLENGE_ID,
                TRANSACTION_ID,
                CUSTOMER_ID))
                .thenReturn(Optional.of(challenge));
    }

    private void arrangeIssueCount(long count) {
        when(otpChallengeDao
                .countByTransaction_TransactionIdAndCustomer_UserIdAndCreatedAtGreaterThanEqual(
                        TRANSACTION_ID,
                        CUSTOMER_ID,
                        CYCLE_STARTED_AT))
                .thenReturn(count);
    }

    private OtpChallenge challengeAt(
            OffsetDateTime issuedAt,
            Long challengeId) {

        OtpChallenge challenge = OtpChallenge.issue(
                transaction,
                "SHA-256$stored-salt$stored-digest",
                policy,
                issuedAt);
        ReflectionTestUtils.setField(
                challenge,
                "otpChallengeId",
                challengeId);
        return challenge;
    }

    private TransactionDb verificationRequiredTransaction(
            String email) {

        lenient().when(customer.getUserId()).thenReturn(CUSTOMER_ID);
        lenient().when(customer.getEmail()).thenReturn(email);
        lenient().when(sourceAccount.getAccountId())
                .thenReturn(ACCOUNT_ID);
        lenient().when(sourceAccount.getOwner()).thenReturn(customer);
        lenient().when(sourceAccount.isCustomerOwnedAccount())
                .thenReturn(true);
        lenient().when(sourceAccount.getCurrencyCode())
                .thenReturn(CurrencyCode.INR);
        lenient().when(sourceAccount.getAccountNumber())
                .thenReturn("1234567890123456");
        lenient().when(beneficiary.getBeneficiaryId())
                .thenReturn(81L);
        lenient().when(beneficiary.getOwner()).thenReturn(customer);
        lenient().when(beneficiary.getBeneficiaryName())
                .thenReturn("OTP Vendor");
        lenient().when(beneficiary.getPaymentMethod())
                .thenReturn(BeneficiaryPaymentMethod.UPI);
        lenient().when(beneficiary.getUpiId())
                .thenReturn("otp.vendor@upi");
        lenient().when(riskPolicy.getRiskPolicyId()).thenReturn(11L);
        lenient().when(riskBand.getRiskPolicyBandId()).thenReturn(22L);
        lenient().when(protectionPolicy.getProtectionPolicyId())
                .thenReturn(33L);

        TransactionDb payment = TransactionDb.createPaymentInstruction(
                "SP-OTP-SERVICE-TEST",
                customer,
                sourceAccount,
                beneficiary,
                new BigDecimal("125000.00"),
                null,
                null,
                CYCLE_STARTED_AT.minusSeconds(1), com.ofss.beans.PaymentCategory.MEDICAL);
        // Simulate a historical hydrated row: lifecycle operations must tolerate no category.
        ReflectionTestUtils.setField(payment, "category", null);
        ReflectionTestUtils.setField(
                payment,
                "transactionId",
                TRANSACTION_ID);

        stateService.transition(
                payment,
                TransactionState.AUTHORIZED,
                CYCLE_STARTED_AT);
        payment.recordRiskAssessment(
                veryHighEvaluation(),
                riskPolicy,
                riskBand,
                protectionPolicy);
        stateService.transition(
                payment,
                TransactionState.RISK_ASSESSED,
                CYCLE_STARTED_AT);
        payment.recordReservation(CYCLE_STARTED_AT);
        stateService.transition(
                payment,
                TransactionState.VERIFICATION_REQUIRED,
                CYCLE_STARTED_AT);

        return payment;
    }

    private static RiskEvaluationResult veryHighEvaluation() {
        return new RiskEvaluationResult(
                new RiskPolicySnapshot(
                        11L,
                        "AMOUNT_ONLY_V1",
                        22L,
                        33L,
                        RiskTier.VERY_HIGH,
                        "AMOUNT_VERY_HIGH_V1"),
                null,
                null,
                ProtectionReleaseMode.AFTER_REVIEW,
                true,
                false,
                true,
                true,
                "The payment amount matched VERY_HIGH.",
                CYCLE_STARTED_AT);
    }
}
