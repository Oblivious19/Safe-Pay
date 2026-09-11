package com.ofss.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ofss.beans.Account;
import jakarta.persistence.LockModeType;

public interface AccountDao extends JpaRepository<Account, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.accountId = :accountId")
    Optional<Account> findByAccountIdForUpdate(@Param("accountId") Long accountId);

    Optional<Account> findByAccountIdAndUserEmail(Long accountId, String email);
    List<Account> findByUserEmail(String email);
    boolean existsByUserUserId(Long userId);
}
