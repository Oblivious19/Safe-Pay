package com.ofss.services;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Service;

import com.ofss.beans.RiskPolicyBand;
import com.ofss.common.MoneyUtility;
import com.ofss.dto.risk.RiskEvaluationResult;

@Service
public class AmountRiskEngine {

    private final RiskPolicyService riskPolicyService;
    private final Clock clock;

    public AmountRiskEngine(
            RiskPolicyService riskPolicyService,
            Clock clock) {

        this.riskPolicyService = riskPolicyService;
        this.clock = clock;
    }

    public RiskEvaluationResult evaluate(BigDecimal amount) {
        BigDecimal normalizedAmount =
                MoneyUtility.requireValidTransactionAmount(amount);

        OffsetDateTime evaluatedAt = OffsetDateTime
                .now(clock)
                .withOffsetSameInstant(ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.MICROS);

        RiskPolicyBand matchedBand =
                riskPolicyService.resolveRequiredBand(
                        normalizedAmount,
                        evaluatedAt);

        return RiskEvaluationResult.fromMatchedBand(
                matchedBand,
                evaluatedAt);
    }
}
