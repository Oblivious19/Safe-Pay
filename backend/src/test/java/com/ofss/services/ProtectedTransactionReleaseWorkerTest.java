package com.ofss.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
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
import com.ofss.beans.ProtectionReleaseMode;
import com.ofss.beans.RiskPolicy;
import com.ofss.beans.RiskPolicyBand;
import com.ofss.beans.RiskTier;
import com.ofss.beans.TransactionDb;
import com.ofss.beans.TransactionState;
import com.ofss.beans.User;
import com.ofss.dto.risk.RiskEvaluationResult;
import com.ofss.dto.risk.RiskPolicySnapshot;
import com.ofss.repository.TransactionDao;

import jakarta.persistence.EntityManager;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProtectedTransactionReleaseWorkerTest {

    private static final Long TRANSACTION_ID = 1001L;
    private static final OffsetDateTime ASSESSED_AT =
            OffsetDateTime.parse("2026-09-16T10:00:00Z");
    private static final OffsetDateTime DEADLINE =
            ASSESSED_AT.plusSeconds(10);

    @Mock private TransactionDao transactionDao;
    @Mock private EntityManager entityManager;
    @Mock private TransactionLifecycleEvidenceService evidenceService;
    @Mock private User customer;
    @Mock private Account sourceAccount;
    @Mock private Beneficiary beneficiary;
    @Mock private RiskPolicy riskPolicy;
    @Mock private RiskPolicyBand riskBand;
    @Mock private ProtectionPolicy protectionPolicy;

    private ProtectedTransactionReleaseWorker worker;
    private TransactionStateService stateService;

    @BeforeEach
    void setUp() {
        stateService = new TransactionStateServiceImpl();
        worker = new ProtectedTransactionReleaseWorkerImpl(
                transactionDao,
                stateService,
                evidenceService,
                entityManager);
    }

    @Test
    void releasesAtTheExactDatabaseDeadlineAndPreservesReservation() {
        TransactionDb transaction = protectedTransaction();
        when(transactionDao.findByIdForUpdate(TRANSACTION_ID))
                .thenReturn(Optional.of(transaction));
        when(transactionDao.currentDatabaseTime())
                .thenReturn(DEADLINE);

        ProtectedTransactionReleaseOutcome outcome =
                worker.releaseIfExpired(TRANSACTION_ID);

        assertThat(outcome)
                .isEqualTo(ProtectedTransactionReleaseOutcome.RELEASED);
        assertThat(transaction.getState())
                .isEqualTo(TransactionState.RELEASED);
        assertThat(transaction.getReleasedAt()).isEqualTo(DEADLINE);
        assertThat(transaction.getReservedAmount())
                .isEqualByComparingTo("2500.00");
        assertThat(transaction.getReservationEndedAt()).isNull();
        verify(entityManager).flush();
        verify(evidenceService).appendSystemEvent(
                eq(TransactionLifecycleEvent.PAYMENT_RELEASED),
                eq(transaction),
                eq(TransactionState.PROTECTED),
                eq(TransactionState.RELEASED),
                eq(AuditOutcome.SUCCESS),
                isNull(),
                any(OperationContext.class),
                eq("PROTECTION-DEADLINE-" + DEADLINE),
                eq(DEADLINE));
    }

    @Test
    void refusesToReleaseBeforeTheDatabaseDeadline() {
        TransactionDb transaction = protectedTransaction();
        when(transactionDao.findByIdForUpdate(TRANSACTION_ID))
                .thenReturn(Optional.of(transaction));
        when(transactionDao.currentDatabaseTime())
                .thenReturn(DEADLINE.minusNanos(1_000));

        ProtectedTransactionReleaseOutcome outcome =
                worker.releaseIfExpired(TRANSACTION_ID);

        assertThat(outcome)
                .isEqualTo(ProtectedTransactionReleaseOutcome.NOT_DUE);
        assertThat(transaction.getState())
                .isEqualTo(TransactionState.PROTECTED);
        verify(entityManager, never()).flush();
    }

    @Test
    void duplicateExecutionDoesNotReleaseAnAlreadyReleasedTransaction() {
        TransactionDb transaction = protectedTransaction();
        stateService.transition(
                transaction,
                TransactionState.RELEASED,
                DEADLINE);
        when(transactionDao.findByIdForUpdate(TRANSACTION_ID))
                .thenReturn(Optional.of(transaction));

        ProtectedTransactionReleaseOutcome outcome =
                worker.releaseIfExpired(TRANSACTION_ID);

        assertThat(outcome)
                .isEqualTo(
                        ProtectedTransactionReleaseOutcome.NOT_ELIGIBLE);
        verify(transactionDao, never()).currentDatabaseTime();
        verify(entityManager, never()).flush();
    }

    @Test
    void missingCandidateIsARepeatSafeNoOp() {
        when(transactionDao.findByIdForUpdate(TRANSACTION_ID))
                .thenReturn(Optional.empty());

        assertThat(worker.releaseIfExpired(TRANSACTION_ID))
                .isEqualTo(ProtectedTransactionReleaseOutcome.NOT_FOUND);

        verify(transactionDao, never()).currentDatabaseTime();
        verify(entityManager, never()).flush();
    }

    @Test
    void rejectsInvalidTransactionIdBeforeLocking() {
        assertThatThrownBy(() -> worker.releaseIfExpired(0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("transactionId must be positive");

        verify(transactionDao, never()).findByIdForUpdate(0L);
    }

    private TransactionDb protectedTransaction() {
        when(customer.getUserId()).thenReturn(7L);
        when(sourceAccount.getAccountId()).thenReturn(70L);
        when(sourceAccount.getOwner()).thenReturn(customer);
        when(sourceAccount.isCustomerOwnedAccount()).thenReturn(true);
        when(sourceAccount.getCurrencyCode()).thenReturn(CurrencyCode.INR);
        when(sourceAccount.getAccountNumber())
                .thenReturn("1234567890123456");
        when(beneficiary.getBeneficiaryId()).thenReturn(700L);
        when(beneficiary.getOwner()).thenReturn(customer);
        when(beneficiary.getBeneficiaryName()).thenReturn("Vendor One");
        when(beneficiary.getPaymentMethod())
                .thenReturn(BeneficiaryPaymentMethod.UPI);
        when(beneficiary.getUpiId()).thenReturn("vendor@upi");
        when(riskPolicy.getRiskPolicyId()).thenReturn(11L);
        when(riskBand.getRiskPolicyBandId()).thenReturn(22L);
        when(protectionPolicy.getProtectionPolicyId()).thenReturn(33L);

        TransactionDb transaction =
                TransactionDb.createPaymentInstruction(
                        "SP-SCHEDULER-TEST",
                        customer,
                        sourceAccount,
                        beneficiary,
                        new BigDecimal("2500.00"),
                        null,
                        null,
                        ASSESSED_AT.minusSeconds(1));
        ReflectionTestUtils.setField(
                transaction,
                "transactionId",
                TRANSACTION_ID);

        stateService.transition(
                transaction,
                TransactionState.AUTHORIZED,
                ASSESSED_AT);
        transaction.recordRiskAssessment(
                mediumEvaluation(),
                riskPolicy,
                riskBand,
                protectionPolicy);
        stateService.transition(
                transaction,
                TransactionState.RISK_ASSESSED,
                ASSESSED_AT);
        transaction.recordReservation(ASSESSED_AT);
        stateService.transition(
                transaction,
                TransactionState.PROTECTED,
                ASSESSED_AT);

        return transaction;
    }

    private static RiskEvaluationResult mediumEvaluation() {
        return new RiskEvaluationResult(
                new RiskPolicySnapshot(
                        11L,
                        "AMOUNT_ONLY_V1",
                        22L,
                        33L,
                        RiskTier.MEDIUM,
                        "AMOUNT_MEDIUM_V1"),
                null,
                10L,
                ProtectionReleaseMode.AFTER_TIMER,
                true,
                true,
                false,
                false,
                "The payment amount matched MEDIUM.",
                ASSESSED_AT);
    }
}
