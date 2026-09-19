package com.ofss.services;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ofss.beans.CurrencyCode;
import com.ofss.beans.RiskAlgorithmType;
import com.ofss.beans.RiskPolicy;
import com.ofss.beans.RiskPolicyBand;
import com.ofss.beans.RiskPolicyStatus;
import com.ofss.common.MoneyUtility;
import com.ofss.repository.RiskPolicyBandDao;
import com.ofss.repository.RiskPolicyDao;

@Service
@Transactional(readOnly = true)
public class RiskPolicyServiceImpl
        implements RiskPolicyService {

    private final RiskPolicyDao riskPolicyDao;
    private final RiskPolicyBandDao riskPolicyBandDao;

    public RiskPolicyServiceImpl(
            RiskPolicyDao riskPolicyDao,
            RiskPolicyBandDao riskPolicyBandDao) {

        this.riskPolicyDao = riskPolicyDao;
        this.riskPolicyBandDao = riskPolicyBandDao;
    }

    @Override
    public RiskPolicyBand resolveRequiredBand(
            BigDecimal amount,
            OffsetDateTime evaluatedAt) {

        BigDecimal normalizedAmount =
                MoneyUtility.requireValidTransactionAmount(amount);

        OffsetDateTime normalizedEvaluationTime =
                normalizeEvaluationTime(evaluatedAt);

        List<RiskPolicy> eligiblePolicies =
                riskPolicyDao.findEligiblePolicies(
                        RiskPolicyStatus.ACTIVE,
                        RiskAlgorithmType.AMOUNT_ONLY,
                        CurrencyCode.INR,
                        normalizedEvaluationTime);

        if (eligiblePolicies.size() != 1) {
            throw configurationFailure(
                    eligiblePolicies.isEmpty()
                            ? "No eligible V1 risk policy is available"
                            : "More than one eligible V1 risk policy was returned");
        }

        RiskPolicy selectedPolicy = eligiblePolicies.get(0);

        List<RiskPolicyBand> matchingBands =
                riskPolicyBandDao.findMatchingBands(
                        selectedPolicy.getRiskPolicyId(),
                        normalizedAmount);

        if (matchingBands.size() != 1) {
            throw configurationFailure(
                    matchingBands.isEmpty()
                            ? "The active V1 risk policy does not cover the amount"
                            : "The active V1 risk policy contains overlapping bands");
        }

        return matchingBands.get(0);
    }

    private static OffsetDateTime normalizeEvaluationTime(
            OffsetDateTime evaluatedAt) {

        return Objects.requireNonNull(
                        evaluatedAt,
                        "evaluatedAt is required")
                .withOffsetSameInstant(ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.MICROS);
    }

    private static IllegalStateException configurationFailure(
            String message) {

        return new IllegalStateException(message);
    }
}
