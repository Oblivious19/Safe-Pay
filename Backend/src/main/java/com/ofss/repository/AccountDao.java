package com.ofss.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ofss.beans.Account;

public interface AccountDao extends JpaRepository<Account, Long> {
    Optional<Account> findByAccountIdAndUserEmail(Long accountId, String email);
}
