package com.ofss.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import com.ofss.beans.CurrencyCode;
import com.ofss.beans.ProtectionPolicy;
import com.ofss.beans.ProtectionReleaseMode;
import com.ofss.beans.RiskAlgorithmType;
import com.ofss.beans.RiskPolicy;
import com.ofss.beans.RiskPolicyBand;
import com.ofss.beans.RiskPolicyStatus;
import com.ofss.beans.RiskTier;

@DataJpaTest
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE)
class RiskPolicyDaoIntegrationTest {

    @Autowired
    private RiskPolicyDao riskPolicyDao;

    @Autowired
    private RiskPolicyBandDao riskPolicyBandDao;

    @Autowired
    private ProtectionPolicyDao protectionPolicyDao;

    @Test
    void loadsExactlyOneEligibleActiveAmountOnlyInrPolicy() {
        List<RiskPolicy> policies =
                riskPolicyDao.findEligiblePolicies(
                        RiskPolicyStatus.ACTIVE,
                        RiskAlgorithmType.AMOUNT_ONLY,
                        CurrencyCode.INR,
                        OffsetDateTime.now(ZoneOffset.UTC));

        assertThat(policies).singleElement()
                .satisfies(policy -> {
                    assertThat(policy.getRiskPolicyId())
                            .isPositive();
                    assertThat(policy.getPolicyVersion())
                            .isEqualTo("AMOUNT_ONLY_V1");
                    assertThat(policy.getAlgorithmType())
                            .isEqualTo(
                                    RiskAlgorithmType.AMOUNT_ONLY);
                    assertThat(policy.getCurrencyCode())
                            .isEqualTo(CurrencyCode.INR);
                    assertThat(policy.getStatus())
                            .isEqualTo(RiskPolicyStatus.ACTIVE);
                    assertThat(policy.getEffectiveFrom())
                            .isNotNull();
                    assertThat(policy.getEffectiveTo()).isNull();
                });
    }

    @Test
    void loadsFourCanonicalProtectionActions() {
        List<ProtectionPolicy> actions = protectionPolicyDao
                .findAllByOrderByProtectionPolicyIdAsc();

        assertThat(actions)
                .extracting(ProtectionPolicy::getProtectionCode)
                .containsExactly(
                        "IMMEDIATE_RELEASE_V1",
                        "MEDIUM_TIMER_10S_V1",
                        "HIGH_TIMER_60S_V1",
                        "VERY_HIGH_REVIEW_V1");

        ProtectionPolicy immediate = actions.get(0);

        assertThat(immediate.getReleaseMode())
                .isEqualTo(ProtectionReleaseMode.IMMEDIATE);
        assertThat(immediate.getProtectionSeconds())
                .isZero();
        assertThat(immediate.canCustomerCancel()).isFalse();
        assertThat(immediate.isAutoRelease()).isTrue();

        ProtectionPolicy review = actions.get(3);

        assertThat(review.getReleaseMode())
                .isEqualTo(ProtectionReleaseMode.AFTER_REVIEW);
        assertThat(review.getProtectionSeconds()).isNull();
        assertThat(review.isOtpRequired()).isTrue();
        assertThat(review.isRiskReviewRequired()).isTrue();
    }

    @Test
    void loadsFourCoherentOrderedBandsWithJoinedActions() {
        RiskPolicy policy = canonicalPolicy();

        List<RiskPolicyBand> bands = riskPolicyBandDao
                .findAllForPolicy(policy.getRiskPolicyId());

        assertThat(bands)
                .extracting(RiskPolicyBand::getRiskTier)
                .containsExactly(
                        RiskTier.LOW,
                        RiskTier.MEDIUM,
                        RiskTier.HIGH,
                        RiskTier.VERY_HIGH);

        assertThat(bands)
                .extracting(RiskPolicyBand::getDisplayOrder)
                .containsExactly(1, 2, 3, 4);

        assertThat(bands).allSatisfy(band -> {
            assertThat(band.getRiskPolicy().getRiskPolicyId())
                    .isEqualTo(policy.getRiskPolicyId());
            assertThat(band.getProtectionPolicy()
                    .getProtectionPolicyId())
                    .isPositive();
            assertThat(band.getBandCode()).isNotBlank();
            assertThat(band.getExplanationTemplate())
                    .isNotBlank();
        });
    }

    @Test
    void matchesEveryMandatoryBoundaryToExactlyOneBand() {
        Long policyId = canonicalPolicy().getRiskPolicyId();

        assertTier(policyId, "1.00", RiskTier.LOW);
        assertTier(policyId, "5000.00", RiskTier.LOW);
        assertTier(policyId, "5000.01", RiskTier.MEDIUM);
        assertTier(policyId, "25000.00", RiskTier.MEDIUM);
        assertTier(policyId, "25000.01", RiskTier.HIGH);
        assertTier(policyId, "100000.00", RiskTier.HIGH);
        assertTier(
                policyId,
                "100000.01",
                RiskTier.VERY_HIGH);
        assertTier(
                policyId,
                "9999999999999999.99",
                RiskTier.VERY_HIGH);

        assertThat(riskPolicyBandDao.findMatchingBands(
                policyId,
                new BigDecimal("0.99")))
                .isEmpty();
    }

    @Test
    void exposesOnlyReadOperations() {
        Set<String> prohibitedMethods = Set.of(
                "save",
                "saveAll",
                "delete",
                "deleteById",
                "deleteAll",
                "flush");

        for (Class<?> repositoryType : List.of(
                RiskPolicyDao.class,
                RiskPolicyBandDao.class,
                ProtectionPolicyDao.class)) {

            Set<String> exposedMethods = Arrays
                    .stream(repositoryType.getMethods())
                    .map(Method::getName)
                    .collect(java.util.stream.Collectors.toSet());

            assertThat(exposedMethods)
                    .doesNotContainAnyElementsOf(
                            prohibitedMethods);
        }
    }

    private RiskPolicy canonicalPolicy() {
        return riskPolicyDao
                .findByPolicyVersion("AMOUNT_ONLY_V1")
                .orElseThrow();
    }

    private void assertTier(
            Long policyId,
            String amount,
            RiskTier expectedTier) {

        assertThat(riskPolicyBandDao.findMatchingBands(
                policyId,
                new BigDecimal(amount)))
                .singleElement()
                .extracting(RiskPolicyBand::getRiskTier)
                .isEqualTo(expectedTier);
    }
}
