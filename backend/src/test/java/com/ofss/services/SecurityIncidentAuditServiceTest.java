package com.ofss.services;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

import com.ofss.beans.AuditOutcome;
import com.ofss.beans.User;
import com.ofss.beans.UserStatus;
import com.ofss.repository.UserDao;
import com.ofss.security.SafePayPrincipal;

@ExtendWith(MockitoExtension.class)
class SecurityIncidentAuditServiceTest {

    private static final OffsetDateTime NOW =
            OffsetDateTime.parse("2026-09-17T11:00:00Z");

    @Mock private UserDao userDao;
    @Mock private AuthenticationAuditService auditService;
    private SecurityIncidentAuditService service;

    @BeforeEach
    void setUp() {
        service = new SecurityIncidentAuditService(
                userDao,
                auditService);
    }

    @Test
    void recordsAuthenticatedForbiddenAccessWithoutRequestSecrets() {
        User user = user();
        when(userDao.findById(101L)).thenReturn(Optional.of(user));
        when(userDao.currentDatabaseTime()).thenReturn(NOW);

        service.recordForbidden(
                authentication(),
                "HTTP_ACCESS_DENIED",
                "correlation-101");

        verify(auditService).record(
                user,
                "AUTH_FORBIDDEN_ACCESS",
                AuditOutcome.DENIED,
                "HTTP_ACCESS_DENIED",
                "correlation-101",
                NOW);
    }

    @Test
    void anonymousDenialIsNotAttributedToAUser() {
        AnonymousAuthenticationToken anonymous =
                new AnonymousAuthenticationToken(
                        "key",
                        "anonymous",
                        Set.of(new SimpleGrantedAuthority(
                                "ROLE_ANONYMOUS")));
        service.recordForbidden(
                anonymous,
                "HTTP_ACCESS_DENIED",
                "correlation");
        verify(userDao, never()).findById(org.mockito.ArgumentMatchers.any());
        verify(auditService, never()).record(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void stalePrincipalWithoutUserCreatesNoFalseEvidence() {
        when(userDao.findById(101L)).thenReturn(Optional.empty());
        service.recordForbidden(
                authentication(),
                "METHOD_ACCESS_DENIED",
                "correlation");
        verify(userDao, never()).currentDatabaseTime();
        verify(auditService, never()).record(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    private static UsernamePasswordAuthenticationToken authentication() {
        SafePayPrincipal principal = new SafePayPrincipal(
                101L,
                "user@safepay.test",
                "hash",
                UserStatus.ACTIVE,
                0L,
                Set.of(new SimpleGrantedAuthority("CUSTOMER")));
        return new UsernamePasswordAuthenticationToken(
                principal,
                null,
                principal.getAuthorities());
    }

    private static User user() {
        User user = User.createActiveUser(
                "Incident User",
                "user@safepay.test",
                null,
                "hash",
                NOW.minusDays(1));
        ReflectionTestUtils.setField(user, "userId", 101L);
        return user;
    }
}
