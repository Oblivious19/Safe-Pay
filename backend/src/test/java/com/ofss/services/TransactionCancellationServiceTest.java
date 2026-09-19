package com.ofss.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import com.ofss.beans.ProtectionPolicy;
import com.ofss.beans.OtpChallenge;
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
import com.ofss.dto.transaction.TransactionResponse;
import com.ofss.excp.InvalidStateTransitionException;
import com.ofss.repository.AccountDao;
import com.ofss.repository.OtpChallengeDao;
import com.ofss.repository.TransactionDao;
import com.ofss.repository.TransactionRiskFactorDao;

import jakarta.persistence.EntityManager;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TransactionCancellationServiceTest {

    private static final Long CUSTOMER_ID = 7L;
    private static final Long ACCOUNT_ID = 70L;
    private static final Long BENEFICIARY_ID = 700L;
    private static final Long TRANSACTION_ID = 1001L;
    private static final OffsetDateTime NOW =
            OffsetDateTime.parse("2026-09-15T10:00:30Z");

    @Mock private TransactionDao transactionDao;
    @Mock private TransactionRiskFactorDao riskFactorDao;
    @Mock private AccountDao accountDao;
    @Mock private OtpChallengeDao otpChallengeDao;
    @Mock private UserService userService;
    @Mock private AccountService accountService;
    @Mock private BeneficiaryService beneficiaryService;
    @Mock private AmountRiskEngine amountRiskEngine;
    @Mock private RiskReviewService riskReviewService;
    @Mock private TransactionLifecycleEvidenceService evidenceService;
    @Mock private EntityManager entityManager;
    @Mock private User customer;
    @Mock private Account sourceAccount;
    @Mock private Beneficiary beneficiary;
    @Mock private RiskPolicy riskPolicy;
    @Mock private RiskPolicyBand riskBand;
    @Mock private ProtectionPolicy protectionPolicy;

    private TransactionStateService stateService;
    private TransactionService service;

    @BeforeEach
    void setUp() {
        stateService = new TransactionStateServiceImpl();
        service = new TransactionServiceImpl(
                transactionDao,
                riskFactorDao,
                accountDao,
                otpChallengeDao,
                userService,
                accountService,
                beneficiaryService,
                amountRiskEngine,
                stateService,
                riskReviewService,
                evidenceService,
                entityManager,
                Clock.fixed(NOW.toInstant(), ZoneOffset.UTC));
    }

    @Test
    void cancelsCreatedInstructionWithoutTouchingAccountFunds() {
        TransactionDb transaction = createdTransaction(NOW.minusSeconds(1));
        arrangeTransactionLock(transaction);

        TransactionResponse response = service.cancelTransaction(
                CUSTOMER_ID,
                TRANSACTION_ID);

        assertThat(response.state()).isEqualTo(TransactionState.CANCELLED);
        assertThat(response.terminalReasonCode())
                .isEqualTo("CUSTOMER_CANCELLED");
        assertThat(response.cancelledAt()).isEqualTo(NOW);
        assertThat(response.reservedAmount()).isEqualByComparingTo("0.00");
        verifyNoInteractions(accountDao);
        verify(entityManager).flush();
        verify(evidenceService).appendUserEvent(
                eq(TransactionLifecycleEvent.PAYMENT_CANCELLED),
                eq(transaction),
                eq(customer),
                eq(RoleName.CUSTOMER),
                eq(TransactionState.CREATED),
                eq(TransactionState.CANCELLED),
                eq(AuditOutcome.SUCCESS),
                eq("CUSTOMER_CANCELLED"),
                any(OperationContext.class),
                eq("CUSTOMER-CANCEL"),
                eq(NOW));
    }

    @Test
    void cancelsProtectedTransactionBeforeDeadlineAndReleasesFunds() {
        TransactionDb transaction = assessedTransaction(
                RiskTier.MEDIUM,
                ProtectionReleaseMode.AFTER_TIMER,
                10L,
                NOW.minusSeconds(5),
                TransactionState.PROTECTED);
        arrangeTransactionAndAccountLocks(transaction);
        when(transactionDao.currentDatabaseTime()).thenReturn(NOW);

        TransactionResponse response = service.cancelTransaction(
                CUSTOMER_ID,
                TRANSACTION_ID);

        assertThat(response.state()).isEqualTo(TransactionState.CANCELLED);
        assertThat(response.reservedAmount()).isEqualByComparingTo("0.00");
        assertThat(transaction.getReservationEndedAt()).isEqualTo(NOW);
        verify(sourceAccount).releaseReservedFunds(
                new BigDecimal("2500.00"),
                NOW);
    }

    @Test
    void cancelsVerificationRequiredTransactionAndReleasesFunds() {
        TransactionDb transaction = assessedTransaction(
                RiskTier.VERY_HIGH,
                ProtectionReleaseMode.AFTER_REVIEW,
                null,
                NOW.minusSeconds(5),
                TransactionState.VERIFICATION_REQUIRED);
        arrangeTransactionAndAccountLocks(transaction);
        OtpChallenge challenge = org.mockito.Mockito.mock(
                OtpChallenge.class);
        when(otpChallengeDao.findPendingOwnedForUpdate(
                TRANSACTION_ID,
                CUSTOMER_ID))
                .thenReturn(Optional.of(challenge));

        TransactionResponse response = service.cancelTransaction(
                CUSTOMER_ID,
                TRANSACTION_ID);

        assertThat(response.state()).isEqualTo(TransactionState.CANCELLED);
        assertThat(response.protectedUntil()).isNull();
        verify(sourceAccount).releaseReservedFunds(any(), any());
        verify(challenge).cancel(NOW);
    }

    @Test
    void cancelsPendingRiskReviewAndPreservesVerificationEvidence() {
        TransactionDb transaction = assessedTransaction(
                RiskTier.VERY_HIGH,
                ProtectionReleaseMode.AFTER_REVIEW,
                null,
                NOW.minusSeconds(5),
                TransactionState.PENDING_RISK_REVIEW);
        OffsetDateTime verifiedAt = NOW.minusSeconds(2);
        ReflectionTestUtils.setField(
                transaction,
                "verificationCompletedAt",
                verifiedAt);
        arrangeTransactionAndAccountLocks(transaction);
        when(transactionDao.currentDatabaseTime()).thenReturn(NOW);

        TransactionResponse response = service.cancelTransaction(
                CUSTOMER_ID,
                TRANSACTION_ID);

        assertThat(response.state()).isEqualTo(TransactionState.CANCELLED);
        assertThat(response.verificationCompletedAt())
                .isEqualTo(verifiedAt);
        assertThat(transaction.getReservationEndedAt()).isEqualTo(NOW);
        verify(riskReviewService).cancelPendingForCustomer(
                transaction,
                NOW);
    }

    @Test
    void rejectsCancellationAtOrAfterAuthoritativeProtectionDeadline() {
        TransactionDb transaction = assessedTransaction(
                RiskTier.MEDIUM,
                ProtectionReleaseMode.AFTER_TIMER,
                10L,
                NOW.minusSeconds(10),
                TransactionState.PROTECTED);
        arrangeTransactionLock(transaction);
        when(transactionDao.currentDatabaseTime()).thenReturn(NOW);

        assertThatThrownBy(() -> service.cancelTransaction(
                CUSTOMER_ID,
                TRANSACTION_ID))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessage(
                        "Transaction cannot move from PROTECTED to CANCELLED");

        assertThat(transaction.getState())
                .isEqualTo(TransactionState.PROTECTED);
        verifyNoInteractions(accountDao);
        verify(entityManager, never()).flush();
    }

    @Test
    void protectedCancellationUsesOracleTimeDespiteApplicationClockSkew() {
        OffsetDateTime oracleTime = NOW.minusSeconds(4);
        TransactionDb transaction = assessedTransaction(
                RiskTier.MEDIUM,
                ProtectionReleaseMode.AFTER_TIMER,
                10L,
                NOW.minusSeconds(5),
                TransactionState.PROTECTED);
        arrangeTransactionAndAccountLocks(transaction);
        when(transactionDao.currentDatabaseTime())
                .thenReturn(oracleTime);

        TransactionResponse response = service.cancelTransaction(
                CUSTOMER_ID,
                TRANSACTION_ID);

        assertThat(response.state()).isEqualTo(TransactionState.CANCELLED);
        assertThat(response.cancelledAt()).isEqualTo(oracleTime);
        assertThat(transaction.getReservationEndedAt())
                .isEqualTo(oracleTime);
        verify(sourceAccount).releaseReservedFunds(
                new BigDecimal("2500.00"),
                oracleTime);
    }

    @Test
    void rejectsCancellationAfterReleaseWithoutReleasingFunds() {
        TransactionDb transaction = assessedTransaction(
                RiskTier.LOW,
                ProtectionReleaseMode.IMMEDIATE,
                0L,
                NOW.minusSeconds(5),
                TransactionState.RELEASED);
        arrangeTransactionLock(transaction);

        assertThatThrownBy(() -> service.cancelTransaction(
                CUSTOMER_ID,
                TRANSACTION_ID))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessage(
                        "Transaction cannot move from RELEASED to CANCELLED");

        verifyNoInteractions(accountDao);
        verify(sourceAccount, never()).releaseReservedFunds(any(), any());
    }

    private TransactionDb assessedTransaction(
            RiskTier tier,
            ProtectionReleaseMode releaseMode,
            Long protectionSeconds,
            OffsetDateTime assessedAt,
            TransactionState targetState) {

        TransactionDb transaction = createdTransaction(
                assessedAt.minusSeconds(1));

        when(riskPolicy.getRiskPolicyId()).thenReturn(11L);
        when(riskBand.getRiskPolicyBandId()).thenReturn(22L);
        when(protectionPolicy.getProtectionPolicyId()).thenReturn(33L);

        stateService.transition(
                transaction,
                TransactionState.AUTHORIZED,
                assessedAt);
        transaction.recordRiskAssessment(
                evaluation(tier, releaseMode, protectionSeconds, assessedAt),
                riskPolicy,
                riskBand,
                protectionPolicy);
        stateService.transition(
                transaction,
                TransactionState.RISK_ASSESSED,
                assessedAt);
        transaction.recordReservation(assessedAt);

        if (targetState == TransactionState.PENDING_RISK_REVIEW) {
            stateService.transition(
                    transaction,
                    TransactionState.VERIFICATION_REQUIRED,
                    assessedAt);
            transaction.recordVerificationCompleted(assessedAt);
            stateService.transition(
                    transaction,
                    TransactionState.PENDING_RISK_REVIEW,
                    assessedAt);
        } else {
            stateService.transition(
                    transaction,
                    targetState,
                    assessedAt);
        }

        return transaction;
    }

    private TransactionDb createdTransaction(OffsetDateTime createdAt) {
        when(customer.getUserId()).thenReturn(CUSTOMER_ID);
        when(sourceAccount.getAccountId()).thenReturn(ACCOUNT_ID);
        when(sourceAccount.getOwner()).thenReturn(customer);
        when(sourceAccount.isCustomerOwnedAccount()).thenReturn(true);
        when(sourceAccount.getCurrencyCode()).thenReturn(CurrencyCode.INR);
        when(sourceAccount.getAccountNumber())
                .thenReturn("1234567890123456");
        when(beneficiary.getBeneficiaryId())
                .thenReturn(BENEFICIARY_ID);
        when(beneficiary.getOwner()).thenReturn(customer);
        when(beneficiary.getBeneficiaryName()).thenReturn("Vendor One");
        when(beneficiary.getPaymentMethod())
                .thenReturn(BeneficiaryPaymentMethod.UPI);
        when(beneficiary.getUpiId()).thenReturn("vendor@upi");

        TransactionDb transaction = TransactionDb.createPaymentInstruction(
                "SP-CANCELLATION-TEST",
                customer,
                sourceAccount,
                beneficiary,
                new BigDecimal("2500.00"),
                null,
                null,
                createdAt);
        ReflectionTestUtils.setField(
                transaction,
                "transactionId",
                TRANSACTION_ID);
        return transaction;
    }

    private void arrangeTransactionLock(TransactionDb transaction) {
        when(transactionDao.findOwnedByIdForUpdate(
                TRANSACTION_ID,
                CUSTOMER_ID))
                .thenReturn(Optional.of(transaction));
    }

    private void arrangeTransactionAndAccountLocks(
            TransactionDb transaction) {

        arrangeTransactionLock(transaction);
        when(accountDao.findByIdForUpdate(ACCOUNT_ID))
                .thenReturn(Optional.of(sourceAccount));
    }

    private static RiskEvaluationResult evaluation(
            RiskTier tier,
            ProtectionReleaseMode releaseMode,
            Long protectionSeconds,
            OffsetDateTime assessedAt) {

        boolean afterReview =
                releaseMode == ProtectionReleaseMode.AFTER_REVIEW;
        boolean afterTimer =
                releaseMode == ProtectionReleaseMode.AFTER_TIMER;

        return new RiskEvaluationResult(
                new RiskPolicySnapshot(
                        11L,
                        "AMOUNT_ONLY_V1",
                        22L,
                        33L,
                        tier,
                        "AMOUNT_" + tier.name() + "_V1"),
                null,
                protectionSeconds,
                releaseMode,
                afterTimer || afterReview,
                !afterReview,
                afterReview,
                afterReview,
                "The payment amount matched " + tier + ".",
                assessedAt);
    }
}
