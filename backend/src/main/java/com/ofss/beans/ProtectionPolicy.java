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
        name = "PROTECTION_POLICY",
        schema = "SAFEPAY_OWNER")
public class ProtectionPolicy {

    @Id
    @Column(
            name = "PROTECTION_POLICY_ID",
            nullable = false,
            updatable = false)
    private Long protectionPolicyId;

    @Column(
            name = "PROTECTION_CODE",
            nullable = false,
            updatable = false,
            length = 50)
    private String protectionCode;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "RELEASE_MODE",
            nullable = false,
            updatable = false,
            length = 30)
    private ProtectionReleaseMode releaseMode;

    @Column(
            name = "PROTECTION_SECONDS",
            updatable = false,
            precision = 10,
            scale = 0)
    private Long protectionSeconds;

    @Column(
            name = "CUSTOMER_CAN_CANCEL",
            nullable = false,
            updatable = false,
            length = 1,
            columnDefinition = "CHAR(1)")
    private String customerCanCancel;

    @Column(
            name = "AUTO_RELEASE",
            nullable = false,
            updatable = false,
            length = 1,
            columnDefinition = "CHAR(1)")
    private String autoRelease;

    @Column(
            name = "OTP_REQUIRED",
            nullable = false,
            updatable = false,
            length = 1,
            columnDefinition = "CHAR(1)")
    private String otpRequired;

    @Column(
            name = "RISK_REVIEW_REQUIRED",
            nullable = false,
            updatable = false,
            length = 1,
            columnDefinition = "CHAR(1)")
    private String riskReviewRequired;

    @Column(
            name = "DESCRIPTION",
            nullable = false,
            updatable = false,
            length = 300)
    private String description;

    @Column(
            name = "CREATED_AT",
            nullable = false,
            updatable = false)
    private OffsetDateTime createdAt;

    protected ProtectionPolicy() {
        // Required by JPA for database materialization.
    }

    public Long getProtectionPolicyId() {
        return protectionPolicyId;
    }

    public String getProtectionCode() {
        return protectionCode;
    }

    public ProtectionReleaseMode getReleaseMode() {
        return releaseMode;
    }

    public Long getProtectionSeconds() {
        return protectionSeconds;
    }

    public boolean canCustomerCancel() {
        return "Y".equals(customerCanCancel);
    }

    public boolean isAutoRelease() {
        return "Y".equals(autoRelease);
    }

    public boolean isOtpRequired() {
        return "Y".equals(otpRequired);
    }

    public boolean isRiskReviewRequired() {
        return "Y".equals(riskReviewRequired);
    }

    public String getDescription() {
        return description;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
