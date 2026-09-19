package com.ofss.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.Repository;

import com.ofss.beans.ProtectionPolicy;

public interface ProtectionPolicyDao
        extends Repository<ProtectionPolicy, Long> {

    Optional<ProtectionPolicy> findByProtectionCode(
            String protectionCode);

    List<ProtectionPolicy>
            findAllByOrderByProtectionPolicyIdAsc();
}
