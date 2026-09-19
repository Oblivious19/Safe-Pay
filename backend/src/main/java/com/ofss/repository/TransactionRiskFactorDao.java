package com.ofss.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.ofss.beans.TransactionRiskFactor;
import com.ofss.beans.TransactionRiskFactorCode;

public interface TransactionRiskFactorDao
        extends Repository<TransactionRiskFactor, Long> {

    <S extends TransactionRiskFactor> S save(S factor);

    @Query("""
            select factor from TransactionRiskFactor factor
              join fetch factor.riskPolicyBand
             where factor.transaction.transactionId = :transactionId
             order by factor.evaluatedAt asc, factor.transactionRiskFactorId asc
            """)
    List<TransactionRiskFactor> findAllForAuditByTransactionId(@Param("transactionId") Long transactionId);

    @Query("""
            select factor
              from TransactionRiskFactor factor
              join fetch factor.riskPolicyBand riskPolicyBand
              join factor.transaction payment
             where payment.transactionId = :transactionId
               and payment.customer.userId = :customerUserId
             order by factor.evaluatedAt asc,
                      factor.transactionRiskFactorId asc
            """)
    List<TransactionRiskFactor> findAllOwnedForTransaction(
            @Param("transactionId") Long transactionId,
            @Param("customerUserId") Long customerUserId);

    @Query("""
            select factor
              from TransactionRiskFactor factor
              join fetch factor.riskPolicyBand riskPolicyBand
              join factor.transaction payment
             where payment.transactionId = :transactionId
               and payment.customer.userId = :customerUserId
               and factor.factorCode = :factorCode
            """)
    Optional<TransactionRiskFactor> findOwnedByFactorCode(
            @Param("transactionId") Long transactionId,
            @Param("customerUserId") Long customerUserId,
            @Param("factorCode")
                    TransactionRiskFactorCode factorCode);
}
