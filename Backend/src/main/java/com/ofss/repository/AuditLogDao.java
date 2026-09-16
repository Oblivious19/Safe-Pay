package com.ofss.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ofss.beans.AuditLog;

public interface AuditLogDao extends JpaRepository<AuditLog, Long> {
    java.util.Optional<AuditLog> findByRequestKey(String requestKey);
}
