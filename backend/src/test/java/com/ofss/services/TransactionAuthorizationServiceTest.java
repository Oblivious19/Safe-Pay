package com.ofss.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;
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
import com.ofss.beans.RoleName;
import com.ofss.beans.TransactionDb;
import com.ofss.beans.TransactionRiskFactor;
import com.ofss.beans.TransactionState;
import com.ofss.beans.User;
import com.ofss.beans.UserStatus;
import com.ofss.dto.risk.RiskEvaluationResult;
import com.ofss.dto.risk.RiskPolicySnapshot;
import com.ofss.dto.transaction.AuthorizeTransactionRequest;
import com.ofss.dto.transaction.TransactionResponse;
import com.ofss.excp.BusinessRuleException;
import com.ofss.excp.ResourceNotFoundExcp;
import com.ofss.repository.AccountDao;
import com.ofss.repository.OtpChallengeDao;
import com.ofss.repository.TransactionDao;
import com.ofss.repository.TransactionRiskFactorDao;

import jakarta.persistence.EntityManager;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TransactionAuthorizationServiceTest {

    private static final Long CUSTOMER_ID = 7L;
    private static final Long ACCOUNT_ID = 70L;
    private static final Long BENEFICIARY_ID = 700L;
    private static final Long TRANSACTION_ID = 1001L;
    private static final OffsetDateTime NOW =
            OffsetDateTime.parse("2026-09-15T10:00:00.123456Z");

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

    private TransactionService service;

    @BeforeEach
    void setUp() {
        service = new TransactionServiceImpl(
                transactionDao,
                riskFactorDao,
                accountDao,
                otpChallengeDao,
                userService,
                accountService,
                beneficiaryService,
                amountRiskEngine,
                new TransactionStateServiceImpl(),
                riskReviewService,
                evidenceService,
                entityManager,
                Clock.fixed(NOW.toInstant(), ZoneOffset.UTC));
    }

    @Test
    void requiresExplicitConfirmationBeforeLockingTransaction() {
        assertThatThrownBy(() -> service.authorizeTransaction(
                CUSTOMER_ID,
                TRANSACTION_ID,
                new AuthorizeTransactionRequest(false)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "confirmed must be true to authorize the payment");

        verifyNoInteractions(transactionDao);
    }

    @Test
    void hidesMissingOrForeignOwnedTransactionBehindSameNotFoundError() {
        when(transactionDao.findOwnedByIdForUpdate(
                TRANSACTION_ID,
                CUSTOMER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.authorizeTransaction(
                CUSTOMER_ID,
                TRANSACTION_ID,
                new AuthorizeTransactionRequest(true)))
                .isInstanceOf(ResourceNotFoundExcp.class)
                .extracting("errorCode")
                .isEqualTo("TRANSACTION_NOT_FOUND");

        verifyNoInteractions(userService, accountDao, amountRiskEngine);
    }

    @Test
    void routesLowRiskDirectlyToReleasedWithHeldReservation() {
        TransactionDb transaction = arrangeAuthorization(
                RiskTier.LOW,
                ProtectionReleaseMode.IMMEDIATE,
                0L);

        TransactionResponse response = authorize();

        assertThat(response.state()).isEqualTo(TransactionState.RELEASED);
        assertThat(response.reservedAmount()).isEqualByComparingTo("2500.00");
        assertThat(response.protectedUntil()).isNull();
        assertThat(response.releasedAt()).isEqualTo(NOW);
        verify(sourceAccount).reserveFunds(
                new BigDecimal("2500.00"),
                NOW);
        verifyRiskEvidencePersistence(transaction);
        verify(evidenceService).appendUserEvent(
                eq(TransactionLifecycleEvent.PAYMENT_RELEASED),
                eq(transaction),
                eq(customer),
                eq(RoleName.CUSTOMER),
                eq(TransactionState.CREATED),
                eq(TransactionState.RELEASED),
                eq(AuditOutcome.SUCCESS),
                isNull(),
                any(OperationContext.class),
                eq("AUTHORIZE"),
                eq(NOW));
    }

    @Test
    void routesMediumRiskToTenSecondProtectionWindow() {
        arrangeAuthorization(
                RiskTier.MEDIUM,
                ProtectionReleaseMode.AFTER_TIMER,
                10L);

        TransactionResponse response = authorize();

        assertThat(response.state()).isEqualTo(TransactionState.PROTECTED);
        assertThat(response.protectedUntil())
                .isEqualTo(NOW.plusSeconds(10));
        assertThat(response.releasedAt()).isNull();
    }

    @Test
    void routesHighRiskToSixtySecondProtectionWindow() {
        arrangeAuthorization(
                RiskTier.HIGH,
                ProtectionReleaseMode.AFTER_TIMER,
                60L);

        TransactionResponse response = authorize();

        assertThat(response.state()).isEqualTo(TransactionState.PROTECTED);
        assertThat(response.protectedUntil())
                .isEqualTo(NOW.plusSeconds(60));
        assertThat(response.riskTier()).isEqualTo(RiskTier.HIGH);
    }

    @Test
    void routesVeryHighRiskToVerificationWithoutTimer() {
        arrangeAuthorization(
                RiskTier.VERY_HIGH,
                ProtectionReleaseMode.AFTER_REVIEW,
                null);

        TransactionResponse response = authorize();

        assertThat(response.state())
                .isEqualTo(TransactionState.VERIFICATION_REQUIRED);
        assertThat(response.protectedUntil()).isNull();
        assertThat(response.reservedAmount()).isEqualByComparingTo("2500.00");
    }

    @Test
    void persistsDefinitiveRiskConfigurationFailureAsFailed() {
        TransactionDb transaction = arrangeBaseTransaction();
        when(amountRiskEngine.evaluate(transaction.getAmount()))
                .thenThrow(new IllegalStateException(
                        "No eligible V1 risk policy is available"));

        TransactionResponse response = authorize();

        assertThat(response.state()).isEqualTo(TransactionState.FAILED);
        assertThat(response.terminalReasonCode())
                .isEqualTo("RISK_EVALUATION_FAILED");
        assertThat(response.reservedAmount()).isEqualByComparingTo("0.00");
        verify(sourceAccount, never()).reserveFunds(any(), any());
        verifyNoInteractions(riskFactorDao);
        verify(entityManager).flush();
    }

    @Test
    void persistsInsufficientFundsAsFailedWithoutReservation() {
        TransactionDb transaction = arrangeAuthorization(
                RiskTier.MEDIUM,
                ProtectionReleaseMode.AFTER_TIMER,
                10L);
        doThrow(new BusinessRuleException(
                "INSUFFICIENT_AVAILABLE_BALANCE",
                "Insufficient available balance"))
                .when(sourceAccount)
                .reserveFunds(transaction.getAmount(), NOW);

        TransactionResponse response = authorize();

        assertThat(response.state()).isEqualTo(TransactionState.FAILED);
        assertThat(response.terminalReasonCode())
                .isEqualTo("INSUFFICIENT_AVAILABLE_BALANCE");
        assertThat(response.reservedAmount()).isEqualByComparingTo("0.00");
        assertThat(response.riskTier()).isEqualTo(RiskTier.MEDIUM);
        verifyRiskEvidencePersistence(transaction);
    }

    private TransactionResponse authorize() {
        return service.authorizeTransaction(
                CUSTOMER_ID,
                TRANSACTION_ID,
                new AuthorizeTransactionRequest(true));
    }

    private TransactionDb arrangeAuthorization(
            RiskTier tier,
            ProtectionReleaseMode releaseMode,
            Long protectionSeconds) {

        TransactionDb transaction = arrangeBaseTransaction();
        RiskEvaluationResult evaluation = evaluation(
                tier,
                releaseMode,
                protectionSeconds);

        when(amountRiskEngine.evaluate(transaction.getAmount()))
                .thenReturn(evaluation);
        when(entityManager.getReference(RiskPolicy.class, 11L))
                .thenReturn(riskPolicy);
        when(entityManager.getReference(RiskPolicyBand.class, 22L))
                .thenReturn(riskBand);
        when(entityManager.getReference(ProtectionPolicy.class, 33L))
                .thenReturn(protectionPolicy);

        when(riskPolicy.getRiskPolicyId()).thenReturn(11L);
        when(riskBand.getRiskPolicyBandId()).thenReturn(22L);
        when(riskBand.getRiskTier()).thenReturn(tier);
        when(protectionPolicy.getProtectionPolicyId()).thenReturn(33L);

        return transaction;
    }

    private TransactionDb arrangeBaseTransaction() {
        when(customer.getUserId()).thenReturn(CUSTOMER_ID);
        when(customer.getStatus()).thenReturn(UserStatus.ACTIVE);
        when(userService.getRequiredUser(CUSTOMER_ID))
                .thenReturn(customer);

        when(sourceAccount.getAccountId()).thenReturn(ACCOUNT_ID);
        when(sourceAccount.getOwner()).thenReturn(customer);
        when(sourceAccount.isCustomerOwnedAccount()).thenReturn(true);
        when(sourceAccount.isActive()).thenReturn(true);
        when(sourceAccount.getCurrencyCode()).thenReturn(CurrencyCode.INR);
        when(sourceAccount.getAccountNumber())
                .thenReturn("1234567890123456");
        when(accountDao.findByIdForUpdate(ACCOUNT_ID))
                .thenReturn(Optional.of(sourceAccount));

        when(beneficiary.getBeneficiaryId())
                .thenReturn(BENEFICIARY_ID);
        when(beneficiary.getOwner()).thenReturn(customer);
        when(beneficiary.getBeneficiaryName()).thenReturn("Vendor One");
        when(beneficiary.getPaymentMethod())
                .thenReturn(BeneficiaryPaymentMethod.UPI);
        when(beneficiary.getUpiId()).thenReturn("vendor@upi");
        when(beneficiaryService.getRequiredActiveOwnedBeneficiary(
                CUSTOMER_ID,
                BENEFICIARY_ID))
                .thenReturn(beneficiary);

        TransactionDb transaction = TransactionDb.createPaymentInstruction(
                "SP-AUTHORIZATION-TEST",
                customer,
                sourceAccount,
                beneficiary,
                new BigDecimal("2500.00"),
                "Invoice payment",
                "INV-100",
                NOW);

        ReflectionTestUtils.setField(
                transaction,
                "transactionId",
                TRANSACTION_ID);
        when(transactionDao.findOwnedByIdForUpdate(
                TRANSACTION_ID,
                CUSTOMER_ID))
                .thenReturn(Optional.of(transaction));

        return transaction;
    }

    private static RiskEvaluationResult evaluation(
            RiskTier tier,
            ProtectionReleaseMode releaseMode,
            Long protectionSeconds) {

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
                NOW);
    }

    private void verifyRiskEvidencePersistence(
            TransactionDb transaction) {

        verify(riskFactorDao).save(any(TransactionRiskFactor.class));
        verify(entityManager, times(2)).flush();
        assertThat(transaction.getRiskPolicyBand()).isSameAs(riskBand);
        assertThat(transaction.getRiskAssessedAt()).isEqualTo(NOW);
    }
}
