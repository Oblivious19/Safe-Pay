package com.ofss.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import com.ofss.beans.Account;
import com.ofss.beans.AccountType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface AdminAccountRepository extends JpaRepository<Account, Long> {
    @EntityGraph(attributePaths = {"user"})
    List<Account> findAllByOrderByAccountId();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.accountId = :id")
    Optional<Account> lockAccount(@Param("id") Long id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Account a set a.balance = :balance, a.updatedAt = :now where a.accountId = :id")
    int updateCreditedBalance(@Param("id") Long id, @Param("balance") BigDecimal balance, @Param("now") LocalDateTime now);

    // Update only requested fields; never overwrite a concurrent status change.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Account a set a.balance = :balance, a.accountType = :type, a.updatedAt = :now where a.accountId = :id")
    int updateDetails(@Param("id") Long id, @Param("balance") BigDecimal balance,
            @Param("type") AccountType type, @Param("now") LocalDateTime now);
}
