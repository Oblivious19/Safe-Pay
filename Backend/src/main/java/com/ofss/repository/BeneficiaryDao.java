package com.ofss.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ofss.beans.Beneficiary;

public interface BeneficiaryDao extends JpaRepository<Beneficiary, Long> {
    List<Beneficiary> findByAccountUserEmail(String email);
    Optional<Beneficiary> findByBeneficiaryIdAndAccountUserEmail(Long beneficiaryId, String email);
    boolean existsByAccountAccountId(Long accountId);
}
