package com.ofss.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ofss.beans.TransactionDb;
import com.ofss.beans.TransactionState;

public interface TransactionDao extends JpaRepository<TransactionDb, Long> {

    @EntityGraph(attributePaths = {"fromAccount", "fromAccount.user", "beneficiary"})
    Optional<TransactionDb> findByIdempotencyKey(String idempotencyKey);

    @EntityGraph(attributePaths = {"fromAccount", "fromAccount.user", "beneficiary"})
    Optional<TransactionDb> findByCancelIdempotencyKey(String cancelIdempotencyKey);

    @Override
    @EntityGraph(attributePaths = {"fromAccount", "fromAccount.user", "beneficiary"})
    Optional<TransactionDb> findById(Long transactionId);

    @EntityGraph(attributePaths = {"fromAccount", "fromAccount.user", "beneficiary"})
    Optional<TransactionDb> findByTransactionIdAndFromAccountUserEmail(Long transactionId, String email);

    @EntityGraph(attributePaths = {"fromAccount", "fromAccount.user", "beneficiary"})
    List<TransactionDb> findByFromAccountUserEmailOrderByCreatedAtDescTransactionIdDesc(String email);

    @EntityGraph(attributePaths = {"fromAccount", "fromAccount.user", "beneficiary"})
    List<TransactionDb> findByFromAccountUserEmailAndStateOrderByCreatedAtDescTransactionIdDesc(
            String email, TransactionState state);

    boolean existsByFromAccountAccountId(Long accountId);

    @Query("select coalesce(sum(t.amount), 0) from TransactionDb t where t.fromAccount.accountId = :accountId "
            + "and t.state in (com.ofss.beans.TransactionState.PROTECTED, com.ofss.beans.TransactionState.HARD_HOLD)")
    BigDecimal sumHeldAmount(@Param("accountId") Long accountId);

    @Query("select t.fromAccount.accountId from TransactionDb t where t.transactionId = :transactionId")
    Optional<Long> findAccountId(@Param("transactionId") Long transactionId);

    @Query("select t.transactionId from TransactionDb t where t.state = com.ofss.beans.TransactionState.PROTECTED "
            + "and t.protectionExpiresAt <= local datetime order by t.fromAccount.accountId, t.transactionId")
    List<Long> findExpiredTransactionIds();

    // Oracle LOCALTIMESTAMP supplies both the window start and the atomic transition deadlines.
    @Query("select local datetime from Account a where a.accountId = :accountId")
    LocalDateTime currentDatabaseTime(@Param("accountId") Long accountId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update TransactionDb t set t.state = com.ofss.beans.TransactionState.CANCELLED, "
            + "t.cancelledAt = local datetime, t.cancelIdempotencyKey = :key, t.version = t.version + 1 "
            + "where t.transactionId = :id and t.version = :version "
            + "and t.state = com.ofss.beans.TransactionState.PROTECTED and t.protectionExpiresAt > local datetime")
    int cancelProtected(@Param("id") Long id, @Param("version") Long version, @Param("key") String key);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update TransactionDb t set t.state = com.ofss.beans.TransactionState.SETTLED, "
            + "t.settledAt = local datetime, t.releasedAt = local datetime, t.version = t.version + 1 "
            + "where t.transactionId = :id and t.version = :version "
            + "and t.state = com.ofss.beans.TransactionState.PROTECTED and t.protectionExpiresAt <= local datetime")
    int settleProtected(@Param("id") Long id, @Param("version") Long version);
}
