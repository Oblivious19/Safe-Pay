package com.ofss.repository;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;

import com.ofss.beans.Account;
import com.ofss.beans.AccountStatus;

public interface AccountDao extends JpaRepository<Account, Long> {
    // Account numbers are unique inside this simulated bank. Beneficiary.account is
    // the sender's address-book owner, NOT the account to credit.
    @Query("select a.accountId from Account a, Beneficiary b where b.beneficiaryId = :beneficiary "
            + "and b.account.accountId = :source and b.account.user.userId = :owner "
            + "and a.accountNumber = b.bankAccountNumber")
    Optional<Long> findRecipientId(@Param("beneficiary") Long beneficiary, @Param("source") Long source,
            @Param("owner") Long owner);

    @Query("select a.accountId from Account a, TransactionDb t where t.transactionId = :id "
            + "and (t.toAccount = a or (t.toAccount is null and a.accountNumber = t.beneficiary.bankAccountNumber))")
    Optional<Long> findPaymentRecipientId(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.accountId = :id")
    Optional<Account> findForSettlement(@Param("id") Long id);
    Optional<Account> findFirstByUserUserIdOrderByAccountId(Long userId);
    List<Account> findByUserUserIdOrderByAccountId(Long userId);
    Optional<Account> findByAccountIdAndUserUserId(Long accountId, Long userId);
    boolean existsByAccountIdAndUserUserId(Long accountId, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.accountId = :accountId and a.user.userId = :userId")
    Optional<Account> findForTransaction(@Param("accountId") Long accountId, @Param("userId") Long userId);
    @Query(value = "SELECT TO_CHAR(SEQ_ACCOUNT_NUMBER.NEXTVAL) FROM dual", nativeQuery = true)
    String nextAccountNumber();
    Optional<Account> findByAccountIdAndUserEmail(Long accountId, String email);
    List<Account> findByUserEmail(String email);
    boolean existsByUserUserId(Long userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Account a set a.status = :target, a.updatedAt = :time where a.accountId = :id and a.status = :expected")
    int changeAdminStatus(@Param("id") Long id, @Param("expected") AccountStatus expected,
            @Param("target") AccountStatus target, @Param("time") LocalDateTime time);
}
