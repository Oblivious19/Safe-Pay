package com.ofss.services;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ofss.beans.AuditOutcome;
import com.ofss.beans.User;
import com.ofss.repository.UserDao;
import com.ofss.security.SafePayPrincipal;

@Service
public class SecurityIncidentAuditService {

    private final UserDao userDao;
    private final AuthenticationAuditService authenticationAuditService;

    public SecurityIncidentAuditService(
            UserDao userDao,
            AuthenticationAuditService authenticationAuditService) {
        this.userDao = userDao;
        this.authenticationAuditService = authenticationAuditService;
    }

    @Transactional
    public void recordForbidden(
            Authentication authentication,
            String reasonCode,
            String correlationId) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal()
                        instanceof SafePayPrincipal principal)) {
            return;
        }

        User actor = userDao.findById(principal.getUserId())
                .orElse(null);
        if (actor == null) {
            return;
        }

        authenticationAuditService.record(
                actor,
                "AUTH_FORBIDDEN_ACCESS",
                AuditOutcome.DENIED,
                reasonCode,
                normalizeCorrelationId(correlationId),
                userDao.currentDatabaseTime());
    }

    private static String normalizeCorrelationId(String value) {
        if (value != null && !value.isBlank() && value.length() <= 64) {
            return value.trim();
        }
        return UUID.randomUUID().toString();
    }
}
