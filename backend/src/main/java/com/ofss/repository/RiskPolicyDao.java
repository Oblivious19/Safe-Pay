package com.ofss.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.ofss.beans.CurrencyCode;
import com.ofss.beans.RiskAlgorithmType;
import com.ofss.beans.RiskPolicy;
import com.ofss.beans.RiskPolicyStatus;

public interface RiskPolicyDao
        extends Repository<RiskPolicy, Long> {

    Optional<RiskPolicy> findByPolicyVersion(
            String policyVersion);

    @Query("""
            select p from RiskPolicy p where (:status is null or p.status = :status)
             order by p.effectiveFrom desc, p.riskPolicyId desc
            """)
    org.springframework.data.domain.Page<RiskPolicy> searchForAudit(
            @Param("status") RiskPolicyStatus status, org.springframework.data.domain.Pageable pageable);

    @Query("""
            select policy
              from RiskPolicy policy
             where policy.status = :status
               and policy.algorithmType = :algorithmType
               and policy.currencyCode = :currencyCode
               and policy.effectiveFrom <= :evaluatedAt
               and (policy.effectiveTo is null
                    or policy.effectiveTo > :evaluatedAt)
             order by policy.riskPolicyId asc
            """)
    List<RiskPolicy> findEligiblePolicies(
            @Param("status") RiskPolicyStatus status,
            @Param("algorithmType")
                    RiskAlgorithmType algorithmType,
            @Param("currencyCode") CurrencyCode currencyCode,
            @Param("evaluatedAt") OffsetDateTime evaluatedAt);
}
