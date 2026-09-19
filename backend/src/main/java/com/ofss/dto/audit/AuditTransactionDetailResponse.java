package com.ofss.dto.audit;

import java.time.OffsetDateTime;
import java.util.List;

import com.ofss.beans.RiskTier;
import com.ofss.beans.TransactionRiskFactor;
import com.ofss.beans.TransactionRiskFactorCode;
import com.ofss.dto.transaction.TransactionResponse;

public record AuditTransactionDetailResponse(
        String customerId, TransactionResponse transaction, List<RiskEvidence> riskEvidence) {

    public AuditTransactionDetailResponse {
        riskEvidence = List.copyOf(riskEvidence);
    }

    public record RiskEvidence(String factorId, TransactionRiskFactorCode factorCode,
            String rawValue, String riskPolicyBandId, RiskTier resultingTier,
            String explanation, OffsetDateTime evaluatedAt) {

        public static RiskEvidence from(TransactionRiskFactor factor) {
            return new RiskEvidence(factor.getTransactionRiskFactorId().toString(), factor.getFactorCode(),
                    factor.getRawValue(), factor.getRiskPolicyBand().getRiskPolicyBandId().toString(),
                    factor.getResultingTier(), factor.getExplanation(), factor.getEvaluatedAt());
        }
    }
}
