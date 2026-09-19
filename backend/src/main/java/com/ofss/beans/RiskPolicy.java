package com.ofss.beans;

import java.time.OffsetDateTime;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Immutable
@Table(
        name = "RISK_POLICY",
        schema = "SAFEPAY_OWNER")
public class RiskPolicy {

    @Id
    @Column(
            name = "RISK_POLICY_ID",
            nullable = false,
            updatable = false)
    private Long riskPolicyId;

    @Column(
            name = "POLICY_VERSION",
            nullable = false,
            updatable = false,
            length = 50)
    private String policyVersion;

    @Column(
            name = "POLICY_NAME",
            nullable = false,
            updatable = false,
            length = 120)
    private String policyName;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "ALGORITHM_TYPE",
            nullable = false,
            updatable = false,
            length = 30)
    private RiskAlgorithmType algorithmType;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "CURRENCY_CODE",
            nullable = false,
            updatable = false,
            length = 3)
    private CurrencyCode currencyCode;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "STATUS",
            nullable = false,
            updatable = false,
            length = 20)
    private RiskPolicyStatus status;

    @Column(
            name = "EFFECTIVE_FROM",
            nullable = false,
            updatable = false)
    private OffsetDateTime effectiveFrom;

    @Column(
            name = "EFFECTIVE_TO",
            updatable = false)
    private OffsetDateTime effectiveTo;

    @Column(
            name = "DESCRIPTION",
            nullable = false,
            updatable = false,
            length = 500)
    private String description;

    @Column(
            name = "CREATED_AT",
            nullable = false,
            updatable = false)
    private OffsetDateTime createdAt;

    protected RiskPolicy() {
        // Required by JPA for database materialization.
    }

    public Long getRiskPolicyId() {
        return riskPolicyId;
    }

    public String getPolicyVersion() {
        return policyVersion;
    }

    public String getPolicyName() {
        return policyName;
    }

    public RiskAlgorithmType getAlgorithmType() {
        return algorithmType;
    }

    public CurrencyCode getCurrencyCode() {
        return currencyCode;
    }

    public RiskPolicyStatus getStatus() {
        return status;
    }

    public OffsetDateTime getEffectiveFrom() {
        return effectiveFrom;
    }

    public OffsetDateTime getEffectiveTo() {
        return effectiveTo;
    }

    public String getDescription() {
        return description;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
