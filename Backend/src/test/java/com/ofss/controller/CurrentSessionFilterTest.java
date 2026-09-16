package com.ofss.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.util.List;
import com.ofss.beans.*;
import com.ofss.config.CustomerResourceSecurityConfig;
import com.ofss.config.LoginSecurityConfig;
import com.ofss.repository.AccountDao;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AccountController.class)
@Import({CustomerResourceSecurityConfig.class, LoginSecurityConfig.class})
class CurrentSessionFilterTest extends WebSecuritySliceSupport {
    @Autowired MockMvc mvc;
    @MockitoBean AccountDao accounts;

    @Test void revokedSessionIsInvalidatedBeforeControllerRuns() throws Exception {
        MockHttpSession session = session();
        when(currentSessions.isCurrent(any())).thenReturn(false);
        mvc.perform(get("/api/accounts").session(session)).andExpect(status().isUnauthorized());
        assertTrue(session.isInvalid());
        verifyNoInteractions(accounts);
    }

    @Test void databaseFailureDeniesAccessWithoutDiscardingSessionOrLeakingDetails() throws Exception {
        MockHttpSession session = session();
        when(currentSessions.isCurrent(any())).thenThrow(new DataAccessResourceFailureException("private-db-details"));
        var result = mvc.perform(get("/api/accounts").session(session)).andExpect(status().isServiceUnavailable()).andReturn();
        assertFalse(session.isInvalid());
        assertFalse(result.getResponse().getContentAsString().contains("private-db-details"));
        verifyNoInteractions(accounts);
    }

    @Test void anonymousRequestDoesNotQueryDatabaseAccess() throws Exception {
        mvc.perform(get("/api/accounts")).andExpect(status().isUnauthorized());
        verifyNoInteractions(currentSessions, accounts);
    }

    private MockHttpSession session() {
        var session = new MockHttpSession();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new LoginPrincipal(101L, "Customer", "customer@example.test", "CUSTOMER", UserStatus.ACTIVE),
                null, List.of()));
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return session;
    }
}
