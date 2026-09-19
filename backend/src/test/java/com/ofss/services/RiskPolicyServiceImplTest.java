package com.ofss.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import com.ofss.beans.CurrencyCode;
import com.ofss.beans.RiskAlgorithmType;
import com.ofss.beans.RiskPolicy;
import com.ofss.beans.RiskPolicyBand;
import com.ofss.beans.RiskPolicyStatus;
import com.ofss.repository.RiskPolicyBandDao;
import com.ofss.repository.RiskPolicyDao;

@ExtendWith(MockitoExtension.class)
class RiskPolicyServiceImplTest {

    private static final Long POLICY_ID = 11L;

    private static final OffsetDateTime INPUT_TIME =
            OffsetDateTime.parse(
                    "2026-09-15T15:45:30.123456789+05:30");

    private static final OffsetDateTime NORMALIZED_TIME =
            OffsetDateTime.parse(
                    "2026-09-15T10:15:30.123456Z");

    @Mock
    private RiskPolicyDao riskPolicyDao;

    @Mock
    private RiskPolicyBandDao riskPolicyBandDao;

    @Mock
    private RiskPolicy policy;

    @Mock
    private RiskPolicy anotherPolicy;

    @Mock
    private RiskPolicyBand matchedBand;

    @Mock
    private RiskPolicyBand anotherBand;

    private RiskPolicyServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RiskPolicyServiceImpl(
                riskPolicyDao,
                riskPolicyBandDao);
    }

    @Test
    void resolvesExactlyOneBandFromOneEligibleCanonicalPolicy() {
        prepareOneEligiblePolicy();

        when(riskPolicyBandDao.findMatchingBands(
                POLICY_ID,
                new BigDecimal("5000.00")))
                .thenReturn(List.of(matchedBand));

        RiskPolicyBand result = service.resolveRequiredBand(
                new BigDecimal("5000"),
                INPUT_TIME);

        assertThat(result).isSameAs(matchedBand);

        verify(riskPolicyDao).findEligiblePolicies(
                RiskPolicyStatus.ACTIVE,
                RiskAlgorithmType.AMOUNT_ONLY,
                CurrencyCode.INR,
                NORMALIZED_TIME);

        verify(riskPolicyBandDao).findMatchingBands(
                POLICY_ID,
                new BigDecimal("5000.00"));
    }

    @Test
    void rejectsMissingEvaluationTimeBeforePolicyLookup() {
        assertThatThrownBy(() -> service.resolveRequiredBand(
                new BigDecimal("1.00"),
                null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("evaluatedAt is required");

        verifyNoInteractions(
                riskPolicyDao,
                riskPolicyBandDao);
    }

    @Test
    void rejectsInvalidAmountsBeforePolicyLookup() {
        List<BigDecimal> invalidAmounts = List.of(
                new BigDecimal("-1.00"),
                new BigDecimal("0.00"),
                new BigDecimal("0.99"),
                new BigDecimal("1.001"),
                new BigDecimal("10000000000000000.00"));

        for (BigDecimal invalidAmount : invalidAmounts) {
            assertThatThrownBy(() -> service.resolveRequiredBand(
                    invalidAmount,
                    INPUT_TIME))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        verifyNoInteractions(
                riskPolicyDao,
                riskPolicyBandDao);
    }

    @Test
    void failsClosedWhenNoEligiblePolicyExists() {
        when(riskPolicyDao.findEligiblePolicies(
                RiskPolicyStatus.ACTIVE,
                RiskAlgorithmType.AMOUNT_ONLY,
                CurrencyCode.INR,
                NORMALIZED_TIME))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.resolveRequiredBand(
                new BigDecimal("1.00"),
                INPUT_TIME))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "No eligible V1 risk policy is available");

        verifyNoInteractions(riskPolicyBandDao);
    }

    @Test
    void failsClosedWhenSeveralEligiblePoliciesAreReturned() {
        when(riskPolicyDao.findEligiblePolicies(
                RiskPolicyStatus.ACTIVE,
                RiskAlgorithmType.AMOUNT_ONLY,
                CurrencyCode.INR,
                NORMALIZED_TIME))
                .thenReturn(List.of(policy, anotherPolicy));

        assertThatThrownBy(() -> service.resolveRequiredBand(
                new BigDecimal("1.00"),
                INPUT_TIME))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "More than one eligible V1 risk policy was returned");

        verifyNoInteractions(riskPolicyBandDao);
    }

    @Test
    void failsClosedWhenValidAmountHasNoMatchingBand() {
        prepareOneEligiblePolicy();

        when(riskPolicyBandDao.findMatchingBands(
                POLICY_ID,
                new BigDecimal("5000.01")))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.resolveRequiredBand(
                new BigDecimal("5000.01"),
                INPUT_TIME))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "The active V1 risk policy does not cover the amount");
    }

    @Test
    void failsClosedWhenAmountMatchesOverlappingBands() {
        prepareOneEligiblePolicy();

        when(riskPolicyBandDao.findMatchingBands(
                POLICY_ID,
                new BigDecimal("25000.00")))
                .thenReturn(List.of(
                        matchedBand,
                        anotherBand));

        assertThatThrownBy(() -> service.resolveRequiredBand(
                new BigDecimal("25000.00"),
                INPUT_TIME))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "The active V1 risk policy contains overlapping bands");
    }

    @Test
    void serviceUsesReadOnlyTransactionBoundary() {
        Transactional transactional =
                RiskPolicyServiceImpl.class
                        .getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isTrue();
    }

    private void prepareOneEligiblePolicy() {
        when(riskPolicyDao.findEligiblePolicies(
                RiskPolicyStatus.ACTIVE,
                RiskAlgorithmType.AMOUNT_ONLY,
                CurrencyCode.INR,
                NORMALIZED_TIME))
                .thenReturn(List.of(policy));

        when(policy.getRiskPolicyId()).thenReturn(POLICY_ID);
    }
}
