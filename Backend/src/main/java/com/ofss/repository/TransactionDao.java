package com.ofss.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Collection;
import java.util.Optional;
import java.math.BigDecimal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.ofss.beans.RiskTier;

import com.ofss.beans.TransactionDb;
import com.ofss.beans.TransactionState;

public interface TransactionDao extends JpaRepository<TransactionDb, Long> {
    @EntityGraph(attributePaths = { "fromAccount", "fromAccount.user", "beneficiary" })
    @Query("select t from TransactionDb t where (:state is null or t.state = :state) and (:risk is null or t.riskTier = :risk) and (:query is null or lower(t.fromAccount.user.name) like lower(concat('%', :query, '%')) or lower(t.fromAccount.user.email) like lower(concat('%', :query, '%')) or lower(t.transactionRef) like lower(concat('%', :query, '%')))")
    Page<TransactionDb> findAdminTransactions(@Param("state") TransactionState state, @Param("risk") RiskTier risk, @Param("query") String query, Pageable pageable);
    @Query("select count(t) from TransactionDb t where t.fromAccount.user.userId = :userId "
            + "and t.beneficiary.beneficiaryId = :beneficiaryId and t.state = 'SETTLED'")
    long settledPaymentsToBeneficiary(@Param("userId") Long userId, @Param("beneficiaryId") Long beneficiaryId);

    @Query("select t.amount from TransactionDb t where t.fromAccount.user.userId = :userId "
            + "and t.state = 'SETTLED' and t.settledAt >= :start and t.settledAt < :end")
    List<BigDecimal> recentSettledAmounts(@Param("userId") Long userId,
            @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @EntityGraph(attributePaths = { "fromAccount", "fromAccount.user", "beneficiary" })
    Optional<TransactionDb> findByIdempotencyKey(String idempotencyKey);
    Optional<TransactionDb> findByCancelIdempotencyKey(String cancelIdempotencyKey);
    @Override
    @EntityGraph(attributePaths = { "fromAccount", "fromAccount.user", "beneficiary" })
    Optional<TransactionDb> findById(Long id);
    @EntityGraph(attributePaths = { "fromAccount", "fromAccount.user", "beneficiary" })
    Optional<TransactionDb> findByTransactionIdAndFromAccountUserEmail(Long transactionId, String email);

    @Query("select t.fromAccount.accountId from TransactionDb t where t.transactionId = :id "
            + "and t.fromAccount.user.email = :email")
    Optional<Long> findOwnedAccountId(@Param("id") Long id, @Param("email") String email);

    @Query("select t.fromAccount.accountId from TransactionDb t where t.transactionId = :id")
    Optional<Long> findAccountId(@Param("id") Long id);

    @Query("select local datetime from Account a where a.accountId = :accountId")
    LocalDateTime currentDatabaseTime(@Param("accountId") Long accountId);

    @Query("select t.transactionId from TransactionDb t where t.state = 'PROTECTED' "
            + "and t.protectionExpiresAt <= local datetime order by t.fromAccount.accountId, t.transactionId")
    List<Long> findExpiredTransactionIds();

    @EntityGraph(attributePaths = { "fromAccount", "fromAccount.user", "beneficiary" })
    List<TransactionDb> findByFromAccountUserEmail(String email);

    @EntityGraph(attributePaths = { "fromAccount", "fromAccount.user", "beneficiary" })
    List<TransactionDb> findByFromAccountUserEmailAndState(String email, TransactionState state);

    @EntityGraph(attributePaths = { "fromAccount", "fromAccount.user", "beneficiary" })
    List<TransactionDb> findByBeneficiaryBankAccountNumberIn(Collection<String> accountNumbers);
    List<TransactionDb> findByStateAndProtectionExpiresAtLessThanEqual(TransactionState state, LocalDateTime time);
    boolean existsByFromAccountAccountId(Long accountId);

    @Query("select coalesce(sum(t.amount), 0) from TransactionDb t where t.fromAccount.accountId = :accountId "
            + "and t.state in :states")
    BigDecimal pendingAmount(@Param("accountId") Long accountId, @Param("states") List<TransactionState> states);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update TransactionDb t set t.state = 'CANCELLED', t.cancelledAt = local datetime, "
            + "t.cancelIdempotencyKey = :key, t.version = t.version + 1 "
            + "where t.transactionId = :id and t.version = :version and t.state = 'PROTECTED' "
            + "and t.protectionExpiresAt > local datetime")
    int cancelProtected(@Param("id") Long id, @Param("version") Long version, @Param("key") String key);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update TransactionDb t set t.state = 'SETTLED', t.settledAt = local datetime, "
            + "t.releasedAt = local datetime, t.version = t.version + 1 "
            + "where t.transactionId = :id and t.version = :version and t.state = 'PROTECTED' "
            + "and t.protectionExpiresAt <= local datetime")
    int settleProtected(@Param("id") Long id, @Param("version") Long version);
}
