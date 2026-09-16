package com.ofss.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ofss.beans.Beneficiary;

public interface BeneficiaryDao extends JpaRepository<Beneficiary, Long> {
    List<Beneficiary> findByAccountUserEmail(String email);
    Optional<Beneficiary> findByBeneficiaryIdAndAccountUserEmail(Long beneficiaryId, String email);
    boolean existsByAccountAccountId(Long accountId);

    List<Beneficiary> findByAccountUserUserIdAndStatusOrderByBeneficiaryId(Long userId, String status);
    List<Beneficiary> findByAccountUserUserIdOrderByBeneficiaryId(Long userId);
    List<Beneficiary> findByAccountAccountIdAndAccountUserUserIdOrderByBeneficiaryId(Long accountId, Long userId);
    List<Beneficiary> findByAccountAccountIdAndAccountUserUserIdAndStatusOrderByBeneficiaryId(Long accountId, Long userId, String status);
    Optional<Beneficiary> findByBeneficiaryIdAndAccountUserUserIdAndStatus(Long beneficiaryId, Long userId, String status);
    Optional<Beneficiary> findByBeneficiaryIdAndAccountUserUserId(Long beneficiaryId, Long userId);

    @Query("select count(b) from Beneficiary b where b.account.accountId = :accountId "
            + "and trim(b.bankAccountNumber) = :number and upper(trim(b.ifsc)) = :ifsc")
    long countDuplicate(@Param("accountId") Long accountId, @Param("number") String number, @Param("ifsc") String ifsc);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Beneficiary b set b.status = 'INACTIVE' where b.beneficiaryId = :id "
            + "and b.account.accountId in (select a.accountId from Account a where a.user.userId = :userId) "
            + "and b.status = 'ACTIVE'")
    int deactivateOwned(@Param("id") Long id, @Param("userId") Long userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Beneficiary b set b.status = :target where b.beneficiaryId = :id "
            + "and b.account.accountId in (select a.accountId from Account a where a.user.userId = :userId) "
            + "and b.status = :expected")
    int changeOwnedStatus(@Param("id") Long id, @Param("userId") Long userId,
            @Param("expected") String expected, @Param("target") String target);
}
