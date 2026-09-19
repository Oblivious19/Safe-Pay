package com.ofss.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.ofss.beans.Beneficiary;
import com.ofss.beans.BeneficiaryStatus;

import jakarta.persistence.LockModeType;

public interface BeneficiaryDao
        extends Repository<Beneficiary, Long> {

    /*
     * Beneficiary creation and managed-entity status updates
     * are allowed. Delete operations are intentionally absent.
     */
    <S extends Beneficiary> S save(S beneficiary);

    /*
     * Spring Security integration seam:
     * ownerUserId comes exclusively from the authenticated principal,
     * never from request JSON.
     */
    Optional<Beneficiary>
            findByBeneficiaryIdAndOwner_UserId(
                    Long beneficiaryId,
                    Long ownerUserId);

    List<Beneficiary>
            findAllByOwner_UserIdOrderByBeneficiaryIdAsc(
                    Long ownerUserId);

    /*
     * Later transaction authorization uses this query so that
     * another customer's beneficiary and a disabled beneficiary
     * cannot be selected for a new payment.
     */
    Optional<Beneficiary>
            findByBeneficiaryIdAndOwner_UserIdAndStatus(
                    Long beneficiaryId,
                    Long ownerUserId,
                    BeneficiaryStatus status);

    boolean existsByOwner_UserIdAndBankAccountNumberAndIfscCode(
            Long ownerUserId,
            String bankAccountNumber,
            String ifscCode);

    boolean existsByOwner_UserIdAndUpiId(
            Long ownerUserId,
            String upiId);

    /*
     * Status mutation must load the owned beneficiary with a
     * database lock inside one writable transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select beneficiary
              from Beneficiary beneficiary
             where beneficiary.beneficiaryId = :beneficiaryId
               and beneficiary.owner.userId = :ownerUserId
            """)
    Optional<Beneficiary> findOwnedByIdForUpdate(
            @Param("beneficiaryId") Long beneficiaryId,
            @Param("ownerUserId") Long ownerUserId);
}
