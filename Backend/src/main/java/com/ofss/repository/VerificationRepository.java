package com.ofss.repository;

import java.util.Optional;
import com.ofss.beans.TransactionDb;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VerificationRepository extends JpaRepository<TransactionDb, Long> {
    @Query("select t.fromAccount.accountId from TransactionDb t where t.transactionId = :id")
    Optional<Long> accountId(@Param("id") Long id);

    @EntityGraph(attributePaths = {"fromAccount", "fromAccount.user", "beneficiary"})
    Optional<TransactionDb> findByTransactionId(Long id);

    @EntityGraph(attributePaths = {"fromAccount", "fromAccount.user", "beneficiary"})
    java.util.List<TransactionDb> findByStateOrderByCreatedAtAscTransactionIdAsc(com.ofss.beans.TransactionState state);

    @Query("select t.fromAccount.accountId from TransactionDb t where t.transactionId = :id "
            + "and t.fromAccount.user.userId = :owner")
    Optional<Long> ownedAccountId(@Param("id") Long id, @Param("owner") Long owner);

    @EntityGraph(attributePaths = {"fromAccount", "fromAccount.user", "beneficiary"})
    Optional<TransactionDb> findByVerificationIdempotencyKey(String key);

    @EntityGraph(attributePaths = {"fromAccount", "fromAccount.user", "beneficiary"})
    Optional<TransactionDb> findByTransactionIdAndFromAccountUserUserId(Long id, Long owner);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update TransactionDb t set t.state = com.ofss.beans.TransactionState.SETTLED, "
            + "t.settledAt = local datetime, t.releasedAt = local datetime, t.verifiedAt = local datetime, t.verificationIdempotencyKey = :key, "
            + "t.version = t.version + 1 where t.transactionId = :id and t.version = :version "
            + "and t.state = com.ofss.beans.TransactionState.HARD_HOLD and t.authenticationRequired = true "
            + "and t.riskTier in (com.ofss.beans.RiskTier.VERY_HIGH, com.ofss.beans.RiskTier.HARD_HOLD)")
    int settleVerified(@Param("id") Long id, @Param("version") Long version, @Param("key") String key);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update TransactionDb t set t.state = com.ofss.beans.TransactionState.CANCELLED, "
            + "t.cancelledAt = local datetime, t.cancelIdempotencyKey = :key, t.version = t.version + 1 "
            + "where t.transactionId = :id and t.version = :version "
            + "and t.state = com.ofss.beans.TransactionState.HARD_HOLD")
    int declineHeld(@Param("id") Long id, @Param("version") Long version, @Param("key") String key);
}
