package com.ofss.services;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;

import com.ofss.beans.*;
import com.ofss.excp.ResourceNotFoundExcp;
import com.ofss.repository.*;
import com.ofss.security.StaffReadAccess;

class AuditEvidenceServiceImplTest {
    private final StaffReadAccess access = mock(StaffReadAccess.class);
    private final RiskReviewDao reviews = mock(RiskReviewDao.class);
    private final TransactionDao transactions = mock(TransactionDao.class);
    private final TransactionRiskFactorDao factors = mock(TransactionRiskFactorDao.class);
    private final LedgerPostingDao postings = mock(LedgerPostingDao.class);
    private final LedgerEntryDao entries = mock(LedgerEntryDao.class);
    private final TransactionExceptionDao exceptions = mock(TransactionExceptionDao.class);
    private final RiskPolicyDao policies = mock(RiskPolicyDao.class);
    private final RiskPolicyBandDao bands = mock(RiskPolicyBandDao.class);
    private final ReportingReadRepository reporting = mock(ReportingReadRepository.class);
    private final AuditEvidenceService service = new AuditEvidenceServiceImpl(access, reviews, transactions, factors,
            postings, entries, exceptions, policies, bands, reporting);
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-09-19T10:00:00Z");

    @Test
    void allReportingReadsCheckAuditorBeforeRepositories() {
        doThrow(new AccessDeniedException("denied")).when(access).requireActiveRole(10L, RoleName.AUDITOR);
        assertThatThrownBy(() -> service.searchLedgerPostings(10L, null, null, null, null, 0, 20)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.getLedgerPosting(10L, 1L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.ledgerReconciliation(10L, null, null, 0, 20)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.reservationReconciliation(10L, null, null, 0, 20)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.searchExceptions(10L, null, null, null, null, null, 0, 20)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.getException(10L, 1L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.listPolicies(10L, null, 0, 20)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.getPolicy(10L, "OLD")).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(postings, entries, reporting, exceptions, policies, bands);
    }

    @Test
    void reportingBoundsAreRejectedBeforeDatabaseAccess() {
        assertThatThrownBy(() -> service.searchLedgerPostings(10L, -1L, null, null, null, 0, 20)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.searchLedgerPostings(10L, null, null, NOW, NOW, 0, 20)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.searchExceptions(10L, null, null, null, null, null, -1, 20)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.getLedgerPosting(10L, 0L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.getException(10L, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.getPolicy(10L, "x".repeat(51))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.listPolicies(10L, null, 0, 101)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(postings, exceptions, policies);
    }

    @Test
    void reportingMissingIdsReturnNotFoundWithoutFollowingRelationships() {
        assertThatThrownBy(() -> service.getLedgerPosting(10L, 1L)).isInstanceOf(ResourceNotFoundExcp.class);
        assertThatThrownBy(() -> service.getException(10L, 1L)).isInstanceOf(ResourceNotFoundExcp.class);
        assertThatThrownBy(() -> service.getPolicy(10L, "MISSING")).isInstanceOf(ResourceNotFoundExcp.class);
        verifyNoInteractions(entries, bands);
    }

    @Test
    void emptyReportingPagesPreserveBoundsAndUtcFilters() {
        var pageable = PageRequest.of(2, 7);
        when(postings.searchForAudit(null, LedgerPostingStatus.POSTED, NOW, NOW.plusDays(1), pageable))
                .thenReturn(Page.empty(pageable));
        when(exceptions.searchForAudit(null, TransactionProcessingStage.SETTLEMENT,
                TransactionExceptionStatus.RESOLVED, NOW, NOW.plusDays(1), pageable)).thenReturn(Page.empty(pageable));
        when(policies.searchForAudit(RiskPolicyStatus.RETIRED, pageable)).thenReturn(Page.empty(pageable));
        var local = NOW.withOffsetSameInstant(java.time.ZoneOffset.ofHoursMinutes(5, 30));
        assertThat(service.searchLedgerPostings(10L, null, LedgerPostingStatus.POSTED, local, local.plusDays(1), 2, 7).size()).isEqualTo(7);
        assertThat(service.searchExceptions(10L, null, TransactionProcessingStage.SETTLEMENT,
                TransactionExceptionStatus.RESOLVED, local, local.plusDays(1), 2, 7).items()).isEmpty();
        assertThat(service.listPolicies(10L, RiskPolicyStatus.RETIRED, 2, 7).items()).isEmpty();
    }

    @Test
    void historicalPolicyAndOpeningPostingAreReadWithoutCurrentPolicySelection() {
        var policy = mock(RiskPolicy.class);
        when(policy.getRiskPolicyId()).thenReturn(5L);
        when(policy.getStatus()).thenReturn(RiskPolicyStatus.RETIRED);
        when(policies.findByPolicyVersion("OLD")).thenReturn(Optional.of(policy));
        when(bands.findAllForPolicy(5L)).thenReturn(List.of());
        assertThat(service.getPolicy(10L, "OLD").policy().status()).isEqualTo(RiskPolicyStatus.RETIRED);
        var posting = mock(LedgerPosting.class);
        when(posting.getPostingId()).thenReturn(6L);
        when(postings.findByPostingId(6L)).thenReturn(Optional.of(posting));
        when(entries.findAllByPosting_PostingIdOrderByLineNumberAsc(6L)).thenReturn(List.of());
        assertThat(service.getLedgerPosting(10L, 6L).posting().transactionId()).isNull();
        verify(policies, never()).findEligiblePolicies(any(), any(), any(), any());
    }

    @Test
    void authorityIsCheckedBeforeAnyEvidenceQuery() {
        doThrow(new AccessDeniedException("denied")).when(access).requireActiveRole(10L, RoleName.AUDITOR);
        assertThatThrownBy(() -> service.listReviews(10L, RiskReviewStatus.PENDING, 0, 20)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.getReview(10L, 501L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.searchTransactions(10L, null, null, null, null, 0, 20)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.getTransactionEvidence(10L, 101L)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(reviews, transactions, factors);
    }

    @Test
    void validatesAllReadBoundsBeforeDataAccess() {
        assertThatThrownBy(() -> service.listReviews(10L, null, 0, 20)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.listReviews(10L, RiskReviewStatus.PENDING, 0, 101)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.getReview(10L, 0L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.getTransactionEvidence(10L, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.searchTransactions(10L, 0L, null, null, null, 0, 20)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.searchTransactions(10L, null, null, NOW, NOW, 0, 20)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(reviews, transactions, factors);
    }

    @Test
    void historicalReviewSearchUsesItsOwnStatusAndFifoQuery() {
        when(reviews.findAllByStatusOrderByRequestedAtAscApprovalIdAsc(RiskReviewStatus.REJECTED, PageRequest.of(0, 20)))
                .thenReturn(Page.empty(PageRequest.of(0, 20)));
        assertThat(service.listReviews(10L, RiskReviewStatus.REJECTED, 0, 20).items()).isEmpty();
        verify(reviews).findAllByStatusOrderByRequestedAtAscApprovalIdAsc(RiskReviewStatus.REJECTED, PageRequest.of(0, 20));
    }

    @Test
    void auditSearchNormalizesOffsetsWithoutImpersonatingCustomer() {
        when(transactions.searchForAudit(99L, TransactionState.CREATED, NOW, NOW.plusHours(1), PageRequest.of(0, 20)))
                .thenReturn(Page.empty(PageRequest.of(0, 20)));
        var from = NOW.withOffsetSameInstant(java.time.ZoneOffset.ofHoursMinutes(5, 30));
        assertThat(service.searchTransactions(10L, 99L, TransactionState.CREATED, from, from.plusHours(1), 0, 20).items()).isEmpty();
        verify(transactions).searchForAudit(99L, TransactionState.CREATED, NOW, NOW.plusHours(1), PageRequest.of(0, 20));
    }

    @Test
    void missingRecordsHaveStableNotFoundErrors() {
        assertThatThrownBy(() -> service.getReview(10L, 501L)).isInstanceOf(ResourceNotFoundExcp.class);
        assertThatThrownBy(() -> service.getTransactionEvidence(10L, 101L)).isInstanceOf(ResourceNotFoundExcp.class);
        verifyNoInteractions(factors);
    }

    @Test
    void unassessedHistoricalPaymentHasNoInventedRiskOrAccountBalance() throws Exception {
        TransactionDb payment = payment();
        when(transactions.findForAuditById(101L)).thenReturn(Optional.of(payment));
        when(factors.findAllForAuditByTransactionId(101L)).thenReturn(List.of());
        var result = service.getTransactionEvidence(10L, 101L);
        assertThat(result.customerId()).isEqualTo("99");
        assertThat(result.riskEvidence()).isEmpty();
        assertThat(result.transaction().category()).isNull();
        var json = org.springframework.http.converter.json.Jackson2ObjectMapperBuilder.json().build().valueToTree(result);
        assertThat(json.findValues("currentBalance")).isEmpty();
        assertThat(json.findValues("availableBalance")).isEmpty();
        assertThat(json.at("/transaction/purpose").asText()).isEqualTo("Original purpose");
    }

    @Test
    void assessedEvidenceRetainsStoredPolicyAndDetectsMismatch() {
        TransactionDb payment = payment();
        RiskPolicyBand band = mock(RiskPolicyBand.class);
        when(band.getRiskPolicyBandId()).thenReturn(201L);
        when(payment.getRiskPolicyBand()).thenReturn(band);
        when(payment.getRiskAssessedAt()).thenReturn(NOW);
        when(payment.getRiskTier()).thenReturn(RiskTier.VERY_HIGH);
        when(payment.getPolicyVersion()).thenReturn("HISTORICAL_POLICY");
        when(payment.getRiskExplanation()).thenReturn("Stored evidence");
        TransactionRiskFactor factor = mock(TransactionRiskFactor.class);
        when(factor.getTransactionRiskFactorId()).thenReturn(301L);
        when(factor.getTransaction()).thenReturn(payment);
        when(factor.getRiskPolicyBand()).thenReturn(band);
        when(factor.getFactorCode()).thenReturn(TransactionRiskFactorCode.PAYMENT_AMOUNT);
        when(factor.getRawValue()).thenReturn("100000.01");
        when(factor.getResultingTier()).thenReturn(RiskTier.VERY_HIGH);
        when(factor.getExplanation()).thenReturn("Stored evidence");
        when(factor.getEvaluatedAt()).thenReturn(NOW);
        when(transactions.findForAuditById(101L)).thenReturn(Optional.of(payment));
        when(factors.findAllForAuditByTransactionId(101L)).thenReturn(List.of(factor));
        var result = service.getTransactionEvidence(10L, 101L);
        assertThat(result.transaction().policyVersion()).isEqualTo("HISTORICAL_POLICY");
        assertThat(result.riskEvidence().getFirst().riskPolicyBandId()).isEqualTo("201");
        when(factor.getExplanation()).thenReturn("Corrupted evidence");
        assertThatThrownBy(() -> service.getTransactionEvidence(10L, 101L)).isInstanceOf(IllegalStateException.class);
        when(factors.findAllForAuditByTransactionId(101L)).thenReturn(List.of());
        assertThatThrownBy(() -> service.getTransactionEvidence(10L, 101L)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsUnexpectedEvidenceOnUnassessedPayment() {
        TransactionDb payment = payment();
        TransactionRiskFactor factor = mock(TransactionRiskFactor.class);
        when(transactions.findForAuditById(101L)).thenReturn(Optional.of(payment));
        when(factors.findAllForAuditByTransactionId(101L)).thenReturn(List.of(factor));
        assertThatThrownBy(() -> service.getTransactionEvidence(10L, 101L)).isInstanceOf(IllegalStateException.class);
    }

    private TransactionDb payment() {
        TransactionDb payment = mock(TransactionDb.class);
        User customer = mock(User.class);
        Account account = mock(Account.class);
        Beneficiary beneficiary = mock(Beneficiary.class);
        when(customer.getUserId()).thenReturn(99L);
        when(account.getAccountId()).thenReturn(71L);
        when(account.getAccountNumber()).thenReturn("123456789012");
        when(beneficiary.getBeneficiaryId()).thenReturn(81L);
        when(beneficiary.getPaymentMethod()).thenReturn(BeneficiaryPaymentMethod.UPI);
        when(beneficiary.getUpiId()).thenReturn("recipient@upi");
        when(payment.getTransactionId()).thenReturn(101L);
        when(payment.getTransactionReference()).thenReturn("AUDIT-101");
        when(payment.getCustomer()).thenReturn(customer);
        when(payment.getSourceAccount()).thenReturn(account);
        when(payment.getBeneficiary()).thenReturn(beneficiary);
        when(payment.getAmount()).thenReturn(new BigDecimal("100000.01"));
        when(payment.getCurrencyCode()).thenReturn(CurrencyCode.INR);
        when(payment.getPurpose()).thenReturn("Original purpose");
        when(payment.getState()).thenReturn(TransactionState.CREATED);
        when(payment.getCreatedAt()).thenReturn(NOW);
        return payment;
    }
}
