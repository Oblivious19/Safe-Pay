package com.ofss.services;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ofss.beans.AuditOutcome;
import com.ofss.beans.AuthSession;
import com.ofss.beans.User;
import com.ofss.repository.AuthSessionDao;
import com.ofss.repository.UserDao;
import com.ofss.security.AuthenticationPolicyProperties;

@Service
public class LoginSecurityServiceImpl
        implements LoginSecurityService {

    private final UserDao userDao;
    private final AuthSessionDao authSessionDao;
    private final AuthenticationAuditService auditService;
    private final AuthenticationPolicyProperties policy;

    public LoginSecurityServiceImpl(
            UserDao userDao,
            AuthSessionDao authSessionDao,
            AuthenticationAuditService auditService,
            AuthenticationPolicyProperties policy) {
        this.userDao = userDao;
        this.authSessionDao = authSessionDao;
        this.auditService = auditService;
        this.policy = policy;
    }

    @Override
    @Transactional
    public void prepareForAuthentication(String loginIdentifier) {
        userDao.findByLoginIdentifierForUpdate(loginIdentifier)
                .ifPresent(user -> user.releaseExpiredTemporaryLock(
                        userDao.currentDatabaseTime()));
    }

    @Override
    @Transactional
    public void recordFailure(
            String loginIdentifier,
            String correlationId) {

        User user = userDao
                .findByLoginIdentifierForUpdate(loginIdentifier)
                .orElse(null);
        OffsetDateTime databaseTime = userDao.currentDatabaseTime();

        if (user == null) {
            auditService.record(
                    null,
                    "AUTH_LOGIN_FAILED",
                    AuditOutcome.DENIED,
                    "INVALID_CREDENTIALS",
                    correlationId,
                    databaseTime);
            return;
        }

        boolean newlyLocked = user.recordFailedLogin(
                databaseTime,
                policy.failedLoginThreshold(),
                policy.temporaryLockDuration());

        auditService.record(
                user,
                "AUTH_LOGIN_FAILED",
                AuditOutcome.DENIED,
                "INVALID_CREDENTIALS",
                correlationId,
                databaseTime);

        if (!newlyLocked) {
            return;
        }

        List<AuthSession> activeSessions = authSessionDao
                .findAllByUser_UserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtAsc(
                        user.getUserId(),
                        databaseTime);

        for (AuthSession session : activeSessions) {
            session.revoke("FAILED_LOGIN_LOCK", databaseTime);
        }

        auditService.record(
                user,
                "AUTH_ACCOUNT_TEMPORARILY_LOCKED",
                AuditOutcome.SUCCESS,
                "FAILED_LOGIN_THRESHOLD",
                correlationId,
                databaseTime);
    }
}
