package com.ofss.dto.audit;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import com.ofss.beans.*;

public record AuditRiskPolicyResponse(Header policy, List<Band> bands) {
    public AuditRiskPolicyResponse { bands = List.copyOf(bands); }
    public record Header(String riskPolicyId, String policyVersion, String policyName, RiskPolicyStatus status,
            RiskAlgorithmType algorithmType, CurrencyCode currencyCode, OffsetDateTime effectiveFrom,
            OffsetDateTime effectiveTo, OffsetDateTime createdAt) {
        public static Header from(RiskPolicy p) {
            return new Header(p.getRiskPolicyId().toString(), p.getPolicyVersion(), p.getPolicyName(), p.getStatus(),
                    p.getAlgorithmType(), p.getCurrencyCode(), p.getEffectiveFrom(), p.getEffectiveTo(), p.getCreatedAt());
        }
    }
    public record Band(String riskPolicyBandId, String bandCode, int displayOrder, BigDecimal minimumAmount,
            BigDecimal maximumAmount, RiskTier riskTier, String protectionCode, ProtectionReleaseMode releaseMode,
            Long protectionSeconds, boolean customerCanCancel, boolean autoRelease, boolean otpRequired,
            boolean riskReviewRequired) {
        public static Band from(RiskPolicyBand b) {
            ProtectionPolicy p = b.getProtectionPolicy();
            return new Band(b.getRiskPolicyBandId().toString(), b.getBandCode(), b.getDisplayOrder(),
                    b.getMinimumAmount(), b.getMaximumAmount(), b.getRiskTier(), p.getProtectionCode(),
                    p.getReleaseMode(), p.getProtectionSeconds(), p.canCustomerCancel(), p.isAutoRelease(),
                    p.isOtpRequired(), p.isRiskReviewRequired());
        }
    }
}
