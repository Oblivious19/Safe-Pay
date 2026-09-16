package com.ofss.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.Optional;
import com.ofss.beans.*;
import com.ofss.config.*;
import com.ofss.repository.AccountDao;
import com.ofss.repository.UserDao;
import com.ofss.services.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Browser-origin requests through the real MVC CORS and session/CSRF filter chains. No Oracle. */
@WebMvcTest({LoginController.class, AuthController.class, AccountController.class,
        TransactionController.class, AdminController.class, AdminReportController.class})
@Import({WebConfig.class, LoginSecurityConfig.class, LogoutSecurityConfig.class,
        CustomerResourceSecurityConfig.class, AdminSecurityConfig.class})
class BrowserCorsTest extends WebSecuritySliceSupport {
    private static final String ORIGIN = "http://localhost:8000";
    @Autowired MockMvc mvc;
    @MockitoBean LoginService login;
    @MockitoBean UserService users;
    @MockitoBean AccountDao accounts;
    @MockitoBean UserDao userDao;
    @MockitoBean TransactionService transactions;
    @MockitoBean AdminService admin;
    @MockitoBean AdminReportService reports;

    @ParameterizedTest
    @CsvSource({"/api/auth/register,POST", "/api/auth/login,POST", "/api/auth/logout,POST",
            "/api/accounts/current,GET", "/api/transactions,POST",
            "/api/admin/reports/transactions/summary,GET", "/api/admin/users/1/status,PATCH"})
    void preflightAllowsExactOriginCredentialsAndClientHeaders(String path, String method) throws Exception {
        mvc.perform(options(path).header("Origin", ORIGIN)
                .header("Access-Control-Request-Method", method)
                .header("Access-Control-Request-Headers", "content-type,x-csrf-token,accept,idempotency-key"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ORIGIN))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"))
                .andExpect(header().string("Access-Control-Allow-Headers", org.hamcrest.Matchers.containsString("x-csrf-token")))
                .andExpect(header().string("Access-Control-Allow-Headers", org.hamcrest.Matchers.containsString("idempotency-key")))
                .andExpect(header().string("Access-Control-Expose-Headers", "X-CSRF-TOKEN"));
        verifyNoInteractions(login, users, accounts, userDao, transactions, admin, reports);
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://untrusted.example", "http://localhost:8001", "http://127.0.0.1:8000", "null"})
    void unapprovedOriginsRemainRejected(String origin) throws Exception {
        mvc.perform(options("/api/auth/login").header("Origin", origin)
                .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void unapprovedHeaderAndMethodRemainRejected() throws Exception {
        mvc.perform(options("/api/transactions").header("Origin", ORIGIN)
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "X-Unapproved-Header"))
                .andExpect(status().isForbidden());
        mvc.perform(options("/api/transactions").header("Origin", ORIGIN)
                .header("Access-Control-Request-Method", "TRACE"))
                .andExpect(status().isForbidden());
    }

    private MockHttpSession session(String role) throws Exception {
        when(login.login("cors@example.test", "test-only"))
                .thenReturn(new LoginPrincipal(1L, "Demo", "cors@example.test", role, UserStatus.ACTIVE));
        return (MockHttpSession) mvc.perform(post("/api/auth/login").header("Origin", ORIGIN)
                .contentType("application/json").content("{\"email\":\"cors@example.test\",\"password\":\"test-only\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ORIGIN))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"))
                .andReturn().getRequest().getSession(false);
    }

    @Test
    void browserSessionTokenAndLogoutStillRequireValidCsrf() throws Exception {
        MockHttpSession session = session("CUSTOMER");
        assertNotNull(session);
        when(accounts.findFirstByUserUserIdOrderByAccountId(1L)).thenReturn(Optional.of(new Account()));
        String token = mvc.perform(get("/api/accounts/current").session(session).header("Origin", ORIGIN))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"))
                .andExpect(header().string("Access-Control-Expose-Headers", "X-CSRF-TOKEN"))
                .andExpect(header().exists("X-CSRF-TOKEN"))
                .andReturn().getResponse().getHeader("X-CSRF-TOKEN");
        mvc.perform(post("/api/auth/logout").session(session).header("Origin", ORIGIN))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/logout").session(session).header("Origin", ORIGIN)
                .header("X-CSRF-TOKEN", "invalid"))
                .andExpect(status().isForbidden());
        assertFalse(session.isInvalid());
        mvc.perform(post("/api/auth/logout").session(session).header("Origin", ORIGIN)
                .header("X-CSRF-TOKEN", token))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ORIGIN))
                .andExpect(jsonPath("$.message").value("Logged out successfully"));
        assertTrue(session.isInvalid());
        mvc.perform(get("/api/accounts/current").header("Origin", ORIGIN))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    void adminTokenIsExposedButCustomerStillDeniedReports() throws Exception {
        mvc.perform(get("/api/admin/reports/transactions/summary").session(session("ADMIN"))
                .header("Origin", ORIGIN))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-CSRF-TOKEN"))
                .andExpect(header().string("Access-Control-Expose-Headers", "X-CSRF-TOKEN"));
        mvc.perform(get("/api/admin/reports/transactions/summary").session(session("CUSTOMER"))
                .header("Origin", ORIGIN))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Access-Control-Allow-Origin", ORIGIN));
    }
}

