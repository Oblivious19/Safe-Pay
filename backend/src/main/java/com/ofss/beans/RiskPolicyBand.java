package com.ofss.beans;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Immutable
@Table(
        name = "RISK_POLICY_BAND",
        schema = "SAFEPAY_OWNER")
public class RiskPolicyBand {

    @Id
    @Column(
            name = "RISK_POLICY_BAND_ID",
            nullable = false,
            updatable = false)
    private Long riskPolicyBandId;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false)
    @JoinColumn(
            name = "RISK_POLICY_ID",
            nullable = false,
            updatable = false)
    private RiskPolicy riskPolicy;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false)
    @JoinColumn(
            name = "PROTECTION_POLICY_ID",
            nullable = false,
            updatable = false)
    private ProtectionPolicy protectionPolicy;

    @Column(
            name = "BAND_CODE",
            nullable = false,
            updatable = false,
            length = 50)
    private String bandCode;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "RISK_TIER",
            nullable = false,
            updatable = false,
            length = 20)
    private RiskTier riskTier;

    @Column(
            name = "MINIMUM_AMOUNT",
            nullable = false,
            updatable = false,
            precision = 18,
            scale = 2)
    private BigDecimal minimumAmount;

    @Column(
            name = "MAXIMUM_AMOUNT",
            updatable = false,
            precision = 18,
            scale = 2)
    private BigDecimal maximumAmount;

    @Column(
            name = "DISPLAY_ORDER",
            nullable = false,
            updatable = false,
            precision = 3,
            scale = 0)
    private Integer displayOrder;

    @Column(
            name = "EXPLANATION_TEMPLATE",
            nullable = false,
            updatable = false,
            length = 500)
    private String explanationTemplate;

    @Column(
            name = "CREATED_AT",
            nullable = false,
            updatable = false)
    private OffsetDateTime createdAt;

    protected RiskPolicyBand() {
        // Required by JPA for database materialization.
    }

    public Long getRiskPolicyBandId() {
        return riskPolicyBandId;
    }

    public RiskPolicy getRiskPolicy() {
        return riskPolicy;
    }

    public ProtectionPolicy getProtectionPolicy() {
        return protectionPolicy;
    }

    public String getBandCode() {
        return bandCode;
    }

    public RiskTier getRiskTier() {
        return riskTier;
    }

    public BigDecimal getMinimumAmount() {
        return minimumAmount;
    }

    public BigDecimal getMaximumAmount() {
        return maximumAmount;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public String getExplanationTemplate() {
        return explanationTemplate;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
