package com.ofss.services;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.ofss.beans.AuditLog;
import com.ofss.beans.AuditOutcome;
import com.ofss.beans.User;
import com.ofss.repository.AuditLogDao;

@Service
public class AuthenticationAuditService {

    private final AuditLogDao auditLogDao;

    public AuthenticationAuditService(AuditLogDao auditLogDao) {
        this.auditLogDao = auditLogDao;
    }

    public void record(
            User actor,
            String actionCode,
            AuditOutcome outcome,
            String reasonCode,
            String correlationId,
            OffsetDateTime occurredAt) {

        auditLogDao.save(
                AuditLog.authenticationEvent(
                        UUID.randomUUID().toString(),
                        actor,
                        actionCode,
                        outcome,
                        reasonCode,
                        correlationId,
                        occurredAt));
    }
}
