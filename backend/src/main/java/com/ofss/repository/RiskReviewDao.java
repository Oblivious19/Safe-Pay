package com.ofss.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.ofss.beans.RiskReview;
import com.ofss.beans.RiskReviewStatus;

import jakarta.persistence.LockModeType;

public interface RiskReviewDao
        extends Repository<RiskReview, Long> {

    <S extends RiskReview> S save(S review);

    <S extends RiskReview> S saveAndFlush(S review);

    @EntityGraph(attributePaths = {"transaction", "transaction.customer", "transaction.sourceAccount",
            "transaction.beneficiary", "customer", "assignedRiskOfficer", "decidedByUser"})
    @Query(value = """
            select review from RiskReview review
             where review.status = com.ofss.beans.RiskReviewStatus.PENDING
               and review.transaction.state = com.ofss.beans.TransactionState.PENDING_RISK_REVIEW
               and review.transaction.riskTier = com.ofss.beans.RiskTier.VERY_HIGH
               and review.transaction.amount > 100000.00
               and (:category is null or review.transaction.category = :category)
             order by case when :priority = true then
                       case review.transaction.category
                         when com.ofss.beans.PaymentCategory.MEDICAL then 1
                         when com.ofss.beans.PaymentCategory.LOAN then 2
                         when com.ofss.beans.PaymentCategory.FRIENDS_FAMILY then 3
                         when com.ofss.beans.PaymentCategory.INVESTMENTS then 4
                         when com.ofss.beans.PaymentCategory.OTHERS then 5
                         else 6 end
                       else 0 end,
                      review.requestedAt asc, review.approvalId asc
            """, countQuery = """
            select count(review) from RiskReview review
             where review.status = com.ofss.beans.RiskReviewStatus.PENDING
               and review.transaction.state = com.ofss.beans.TransactionState.PENDING_RISK_REVIEW
               and review.transaction.riskTier = com.ofss.beans.RiskTier.VERY_HIGH
               and review.transaction.amount > 100000.00
               and (:category is null or review.transaction.category = :category)
            """)
    Page<RiskReview> searchPending(@Param("category") com.ofss.beans.PaymentCategory category,
            @Param("priority") boolean priority, Pageable pageable);

    @Query("""
            select review.transaction.transactionId
              from RiskReview review
             where review.approvalId = :reviewId
            """)
    Optional<Long> findTransactionIdByReviewId(
            @Param("reviewId") Long reviewId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select review
              from RiskReview review
             where review.approvalId = :reviewId
            """)
    Optional<RiskReview> findByIdForUpdate(
            @Param("reviewId") Long reviewId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select review
              from RiskReview review
             where review.transaction.transactionId = :transactionId
               and review.status = com.ofss.beans.RiskReviewStatus.PENDING
            """)
    Optional<RiskReview> findPendingByTransactionIdForUpdate(
            @Param("transactionId") Long transactionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RiskReview>
            findFirstByTransaction_TransactionIdOrderByReviewRoundDesc(
                    Long transactionId);

    @EntityGraph(attributePaths = {
            "transaction",
            "transaction.customer",
            "transaction.sourceAccount",
            "transaction.beneficiary",
            "customer",
            "assignedRiskOfficer",
            "decidedByUser"
    })
    Page<RiskReview> findAllByStatusOrderByRequestedAtAscApprovalIdAsc(
            RiskReviewStatus status,
            Pageable pageable);

    @EntityGraph(attributePaths = {
            "transaction",
            "transaction.customer",
            "transaction.sourceAccount",
            "transaction.beneficiary",
            "customer",
            "assignedRiskOfficer",
            "decidedByUser"
    })
    Optional<RiskReview> findByApprovalId(Long reviewId);

    boolean existsByTransaction_TransactionId(Long transactionId);
}
