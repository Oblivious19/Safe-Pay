package com.ofss.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.ofss.beans.Account;
import com.ofss.beans.AccountType;

import jakarta.persistence.LockModeType;

public interface AccountDao
        extends Repository<Account, Long> {

    Optional<Account> findById(Long accountId);

    @Query(value = """
            select account from Account account
              join fetch account.owner owner
             where account.accountType in (com.ofss.beans.AccountType.SAVINGS, com.ofss.beans.AccountType.CURRENT)
               and (:customerId is null or owner.userId = :customerId)
               and (:minCurrentBalance is null or account.currentBalance >= :minCurrentBalance)
               and (:accountType is null or account.accountType = :accountType)
             order by account.accountId asc
            """, countQuery = """
            select count(account) from Account account
              join account.owner owner
             where account.accountType in (com.ofss.beans.AccountType.SAVINGS, com.ofss.beans.AccountType.CURRENT)
               and (:customerId is null or owner.userId = :customerId)
               and (:minCurrentBalance is null or account.currentBalance >= :minCurrentBalance)
               and (:accountType is null or account.accountType = :accountType)
            """)
    Page<Account> searchCustomerAccounts(
            @Param("customerId") Long customerId,
            @Param("minCurrentBalance") BigDecimal minCurrentBalance,
            @Param("accountType") AccountType accountType,
            Pageable pageable);

    @Query("""
            select account from Account account
              join fetch account.owner owner
             where account.accountId = :accountId
               and account.accountType in (com.ofss.beans.AccountType.SAVINGS, com.ofss.beans.AccountType.CURRENT)
            """)
    Optional<Account> findCustomerAccountById(@Param("accountId") Long accountId);

    Optional<Account> findByAccountNumber(
            String accountNumber);

    /*
     * Spring Security integration seam:
     * ownerUserId comes exclusively from the authenticated principal,
     * never from request JSON.
     */
    Optional<Account> findByAccountIdAndOwner_UserId(
            Long accountId,
            Long ownerUserId);

    List<Account> findAllByOwner_UserIdOrderByAccountIdAsc(
            Long ownerUserId);

    boolean existsByAccountIdAndOwner_UserId(
            Long accountId,
            Long ownerUserId);

    /*
     * Must be invoked inside an active transaction.
     * The lock protects later reservation and settlement changes.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select account
              from Account account
             where account.accountId = :accountId
            """)
    Optional<Account> findByIdForUpdate(
            @Param("accountId") Long accountId);
}
