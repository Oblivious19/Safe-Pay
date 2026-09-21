package com.ofss.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import com.ofss.beans.Account;
import com.ofss.beans.Beneficiary;
import com.ofss.beans.BeneficiaryPaymentMethod;
import com.ofss.beans.CurrencyCode;
import com.ofss.beans.RiskPolicyBand;
import com.ofss.beans.RiskTier;
import com.ofss.beans.TransactionDb;
import com.ofss.beans.TransactionRiskFactor;
import com.ofss.beans.TransactionRiskFactorCode;
import com.ofss.beans.TransactionState;
import com.ofss.common.api.PagedResponse;
import com.ofss.dto.transaction.TransactionRiskExplanationResponse;
import com.ofss.dto.transaction.TransactionResponse;
import com.ofss.dto.transaction.TransactionSummaryResponse;
import com.ofss.excp.ResourceNotFoundExcp;
import com.ofss.repository.AccountDao;
import com.ofss.repository.OtpChallengeDao;
import com.ofss.repository.TransactionDao;
import com.ofss.repository.TransactionRiskFactorDao;

import jakarta.persistence.EntityManager;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TransactionQueryServiceTest {

    private static final Long CUSTOMER_ID = 7L;
    private static final Long TRANSACTION_ID = 1001L;
    private static final OffsetDateTime ASSESSED_AT =
            OffsetDateTime.parse("2026-09-15T10:00:00Z");

    @Mock private TransactionDao transactionDao;
    @Mock private TransactionRiskFactorDao riskFactorDao;
    @Mock private AccountDao accountDao;
    @Mock private OtpChallengeDao otpChallengeDao;
    @Mock private UserService userService;
    @Mock private AccountService accountService;
    @Mock private BeneficiaryService beneficiaryService;
    @Mock private AmountRiskEngine amountRiskEngine;
    @Mock private TransactionStateService stateService;
    @Mock private RiskReviewService riskReviewService;
    @Mock private TransactionLifecycleEvidenceService evidenceService;
    @Mock private EntityManager entityManager;

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
                stateService,
                riskReviewService,
                evidenceService,
                entityManager,
                Clock.fixed(ASSESSED_AT.toInstant(), ZoneOffset.UTC));
    }

    @Test
    void filteredHistoryNormalizesOffsetsAndKeepsCustomerScope() {
        OffsetDateTime from = OffsetDateTime.parse("2026-09-15T15:30:00+05:30");
        OffsetDateTime to = from.plusHours(1);
        when(transactionDao.searchOwned(CUSTOMER_ID, ASSESSED_AT, ASSESSED_AT.plusHours(1),
                TransactionState.CREATED, 501L, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
        assertThat(service.listTransactions(CUSTOMER_ID, from, to, TransactionState.CREATED,
                501L, 0, 20).items()).isEmpty();
        verify(transactionDao).searchOwned(CUSTOMER_ID, ASSESSED_AT, ASSESSED_AT.plusHours(1),
                TransactionState.CREATED, 501L, PageRequest.of(0, 20));
    }

    @Test
    void rejectsInvalidHistoryRangeAndSourceBeforeRepositoryAccess() {
        assertThatThrownBy(() -> service.listTransactions(CUSTOMER_ID, ASSESSED_AT, ASSESSED_AT,
                null, null, 0, 20)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.listTransactions(CUSTOMER_ID, ASSESSED_AT,
                ASSESSED_AT.minusSeconds(1), null, null, 0, 20)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.listTransactions(CUSTOMER_ID, null, null,
                null, 0L, 0, 20)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(transactionDao);
    }

    @Test
    void emptyFiltersPreserveExistingQuery() {
        when(transactionDao.findAllOwned(CUSTOMER_ID, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
        service.listTransactions(CUSTOMER_ID, null, null, null, null, 0, 20);
        verify(transactionDao).findAllOwned(CUSTOMER_ID, PageRequest.of(0, 20));
    }

    @Test
    void listsOnlyOwnedTransactionsWithBoundedPaginationMetadata() {
        TransactionDb transaction = customerSafeTransaction();
        PageRequest pageRequest = PageRequest.of(1, 2);

        when(transactionDao.findAllOwned(CUSTOMER_ID, pageRequest))
                .thenReturn(new PageImpl<>(
                        List.of(transaction),
                        pageRequest,
                        5));

        PagedResponse<TransactionSummaryResponse> response =
                service.listTransactions(CUSTOMER_ID, 1, 2);

        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(2);
        assertThat(response.totalElements()).isEqualTo(5);
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).transactionId())
                .isEqualTo("1001");
        assertThat(response.items().get(0).maskedDestinationIdentifier())
                .isEqualTo("v****r@upi");
    }

    @Test
    void rejectsUnboundedOrInvalidPaginationBeforeRepositoryAccess() {
        assertThatThrownBy(() -> service.listTransactions(
                CUSTOMER_ID,
                -1,
                20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("page must be zero or greater");

        assertThatThrownBy(() -> service.listTransactions(
                CUSTOMER_ID,
                0,
                101))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("size must be between 1 and 100");

        verifyNoInteractions(transactionDao);
    }

    @Test
    void retrievesOwnedTransactionDetailWithoutExposingPolicyIds() {
        TransactionDb transaction = customerSafeTransaction();
        when(transactionDao.currentDatabaseTime()).thenReturn(ASSESSED_AT);
        when(transactionDao.findOwnedById(
                TRANSACTION_ID,
                CUSTOMER_ID))
                .thenReturn(Optional.of(transaction));

        TransactionResponse response = service.getTransaction(
                CUSTOMER_ID,
                TRANSACTION_ID);

        assertThat(response.transactionId()).isEqualTo("1001");
        assertThat(response.serverTime()).isEqualTo(ASSESSED_AT);
        assertThat(response.canCancel()).isTrue();
        assertThat(response.protectionRemainingMillis()).isNull();
        verify(transactionDao).currentDatabaseTime();
        assertThat(response.maskedSourceAccountNumber())
                .isEqualTo("************3456");
        assertThat(TransactionResponse.class.getRecordComponents())
                .extracting(component -> component.getName())
                .doesNotContain(
                        "riskPolicyId",
                        "riskPolicyBandId",
                        "protectionPolicyId",
                        "matchedBandCode");
    }

    @Test
    void hidesMissingAndForeignOwnedTransactionBehindNotFound() {
        when(transactionDao.findOwnedById(
                TRANSACTION_ID,
                CUSTOMER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getTransaction(
                CUSTOMER_ID,
                TRANSACTION_ID))
                .isInstanceOf(ResourceNotFoundExcp.class)
                .extracting("errorCode")
                .isEqualTo("TRANSACTION_NOT_FOUND");
        verify(transactionDao, org.mockito.Mockito.never()).currentDatabaseTime();
    }

    @Test
    void countdownUsesDatabaseTimeRatherThanApplicationClockWithoutMutatingPayment() {
        TransactionDb transaction = customerSafeTransaction();
        when(transaction.getState()).thenReturn(TransactionState.PROTECTED);
        when(transaction.getProtectedUntil()).thenReturn(ASSESSED_AT.plusSeconds(10));
        when(transactionDao.findOwnedById(TRANSACTION_ID, CUSTOMER_ID))
                .thenReturn(Optional.of(transaction));
        when(transactionDao.currentDatabaseTime()).thenReturn(ASSESSED_AT.plusSeconds(7));

        TransactionResponse response = service.getTransaction(CUSTOMER_ID, TRANSACTION_ID);

        assertThat(response.protectionRemainingMillis()).isEqualTo(3000L);
        assertThat(response.canCancel()).isTrue();
        assertThat(response.serverTime()).isEqualTo(ASSESSED_AT.plusSeconds(7));
        verify(transactionDao).currentDatabaseTime();
        verifyNoInteractions(accountDao, stateService, evidenceService, entityManager);
    }

    @Test
    void returnsExplanationOnlyWhenPersistedEvidenceMatchesSnapshot() {
        TransactionDb transaction = assessedTransaction();
        TransactionRiskFactor evidence = matchingEvidence(transaction);
        arrangeRiskQuery(transaction, evidence);

        TransactionRiskExplanationResponse response =
                service.getRiskExplanation(
                        CUSTOMER_ID,
                        TRANSACTION_ID);

        assertThat(response.riskTier()).isEqualTo(RiskTier.HIGH);
        assertThat(response.policyVersion())
                .isEqualTo("AMOUNT_ONLY_V1");
        assertThat(response.explanation())
                .isEqualTo("Amount matched HIGH band.");
        assertThat(response.riskAssessedAt())
                .isEqualTo(ASSESSED_AT);
        verify(riskFactorDao).findOwnedByFactorCode(
                TRANSACTION_ID,
                CUSTOMER_ID,
                TransactionRiskFactorCode.PAYMENT_AMOUNT);
    }

    @Test
    void reportsNoExplanationForUnassessedInstruction() {
        TransactionDb transaction = customerSafeTransaction();
        when(transactionDao.findOwnedById(
                TRANSACTION_ID,
                CUSTOMER_ID))
                .thenReturn(Optional.of(transaction));

        assertThatThrownBy(() -> service.getRiskExplanation(
                CUSTOMER_ID,
                TRANSACTION_ID))
                .isInstanceOf(ResourceNotFoundExcp.class)
                .extracting("errorCode")
                .isEqualTo("RISK_EXPLANATION_NOT_FOUND");

        verifyNoInteractions(riskFactorDao);
    }

    @Test
    void failsClosedWhenEvidenceAndSnapshotDiverge() {
        TransactionDb transaction = assessedTransaction();
        TransactionRiskFactor evidence = matchingEvidence(transaction);
        when(evidence.getExplanation())
                .thenReturn("Different explanation");
        arrangeRiskQuery(transaction, evidence);

        assertThatThrownBy(() -> service.getRiskExplanation(
                CUSTOMER_ID,
                TRANSACTION_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "Persisted risk evidence does not match the transaction snapshot");
    }

    private void arrangeRiskQuery(
            TransactionDb transaction,
            TransactionRiskFactor evidence) {

        when(transactionDao.findOwnedById(
                TRANSACTION_ID,
                CUSTOMER_ID))
                .thenReturn(Optional.of(transaction));
        when(riskFactorDao.findOwnedByFactorCode(
                TRANSACTION_ID,
                CUSTOMER_ID,
                TransactionRiskFactorCode.PAYMENT_AMOUNT))
                .thenReturn(Optional.of(evidence));
    }

    private TransactionDb assessedTransaction() {
        TransactionDb transaction = customerSafeTransaction();
        RiskPolicyBand band = mock(RiskPolicyBand.class);

        when(transaction.getRiskTier()).thenReturn(RiskTier.HIGH);
        when(transaction.getPolicyVersion())
                .thenReturn("AMOUNT_ONLY_V1");
        when(transaction.getProtectionSeconds()).thenReturn(60L);
        when(transaction.getRiskExplanation())
                .thenReturn("Amount matched HIGH band.");
        when(transaction.getRiskAssessedAt()).thenReturn(ASSESSED_AT);
        when(transaction.getRiskPolicyBand()).thenReturn(band);
        when(band.getRiskPolicyBandId()).thenReturn(22L);

        return transaction;
    }

    private TransactionRiskFactor matchingEvidence(
            TransactionDb transaction) {

        TransactionRiskFactor evidence =
                mock(TransactionRiskFactor.class);
        RiskPolicyBand transactionBand =
                transaction.getRiskPolicyBand();

        when(evidence.getTransaction()).thenReturn(transaction);
        when(evidence.getFactorCode())
                .thenReturn(TransactionRiskFactorCode.PAYMENT_AMOUNT);
        when(evidence.getRiskPolicyBand())
                .thenReturn(transactionBand);
        when(evidence.getResultingTier()).thenReturn(RiskTier.HIGH);
        when(evidence.getExplanation())
                .thenReturn("Amount matched HIGH band.");
        when(evidence.getEvaluatedAt()).thenReturn(ASSESSED_AT);

        return evidence;
    }

    private TransactionDb customerSafeTransaction() {
        TransactionDb transaction = mock(TransactionDb.class);
        Account account = mock(Account.class);
        Beneficiary beneficiary = mock(Beneficiary.class);

        when(transaction.getTransactionId())
                .thenReturn(TRANSACTION_ID);
        when(transaction.getTransactionReference())
                .thenReturn("SP-QUERY-TEST");
        when(transaction.getSourceAccount()).thenReturn(account);
        when(transaction.getBeneficiary()).thenReturn(beneficiary);
        when(transaction.getAmount())
                .thenReturn(new BigDecimal("25000.01"));
        when(transaction.getCurrencyCode()).thenReturn(CurrencyCode.INR);
        when(transaction.getState()).thenReturn(TransactionState.CREATED);
        when(transaction.getReservedAmount())
                .thenReturn(new BigDecimal("0.00"));
        when(transaction.getCreatedAt()).thenReturn(ASSESSED_AT);
        when(transaction.getUpdatedAt()).thenReturn(ASSESSED_AT);

        when(account.getAccountId()).thenReturn(70L);
        when(account.getAccountNumber())
                .thenReturn("1234567890123456");
        when(beneficiary.getBeneficiaryId()).thenReturn(700L);
        when(beneficiary.getBeneficiaryName()).thenReturn("Vendor One");
        when(beneficiary.getPaymentMethod())
                .thenReturn(BeneficiaryPaymentMethod.UPI);
        when(beneficiary.getUpiId()).thenReturn("vendor@upi");

        return transaction;
    }
}
