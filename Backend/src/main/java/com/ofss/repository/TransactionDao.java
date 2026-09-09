package com.ofss.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ofss.beans.TransactionDb;
import com.ofss.beans.TransactionState;

public interface TransactionDao extends JpaRepository<TransactionDb, Long> {
    Optional<TransactionDb> findByIdempotencyKey(String idempotencyKey);
    Optional<TransactionDb> findByTransactionIdAndFromAccountUserEmail(Long transactionId, String email);
    List<TransactionDb> findByFromAccountUserEmail(String email);
    List<TransactionDb> findByFromAccountUserEmailAndState(String email, TransactionState state);
    List<TransactionDb> findByStateAndProtectionExpiresAtLessThanEqual(TransactionState state, LocalDateTime time);

    @Modifying
    @Query("update TransactionDb t set t.state = 'CANCELLED', t.cancelledAt = :time, t.version = t.version + 1 "
            + "where t.transactionId = :id and t.version = :version and t.state = 'PROTECTED'")
    int cancelProtected(@Param("id") Long id, @Param("version") Long version, @Param("time") LocalDateTime time);

    @Modifying
    @Query("update TransactionDb t set t.state = 'SETTLED', t.settledAt = :time, t.version = t.version + 1 "
            + "where t.transactionId = :id and t.version = :version and t.state = 'PROTECTED'")
    int settleProtected(@Param("id") Long id, @Param("version") Long version, @Param("time") LocalDateTime time);
}
