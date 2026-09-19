package com.ofss.repository;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.ofss.beans.RiskPolicyBand;

public interface RiskPolicyBandDao
        extends Repository<RiskPolicyBand, Long> {

    @Query("""
            select band
              from RiskPolicyBand band
              join fetch band.riskPolicy policy
              join fetch band.protectionPolicy protection
             where policy.riskPolicyId = :riskPolicyId
             order by band.displayOrder asc
            """)
    List<RiskPolicyBand> findAllForPolicy(
            @Param("riskPolicyId") Long riskPolicyId);

    @Query("""
            select band
              from RiskPolicyBand band
              join fetch band.riskPolicy policy
              join fetch band.protectionPolicy protection
             where policy.riskPolicyId = :riskPolicyId
               and band.minimumAmount <= :amount
               and (band.maximumAmount is null
                    or band.maximumAmount >= :amount)
             order by band.displayOrder asc
            """)
    List<RiskPolicyBand> findMatchingBands(
            @Param("riskPolicyId") Long riskPolicyId,
            @Param("amount") BigDecimal amount);
}
