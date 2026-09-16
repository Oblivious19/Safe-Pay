package com.ofss.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import com.ofss.beans.*;
import com.ofss.config.AdminSecurityConfig;
import com.ofss.config.CustomerResourceSecurityConfig;
import com.ofss.config.LoginSecurityConfig;
import com.ofss.repository.AccountDao;
import com.ofss.repository.UserDao;
import com.ofss.services.AdminService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Sort;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({AdminController.class, AccountController.class})
@Import({AdminSecurityConfig.class, CustomerResourceSecurityConfig.class, LoginSecurityConfig.class, AdminService.class})
class AdminAuthorizationTest extends WebSecuritySliceSupport {
    @Autowired MockMvc mvc;
    @MockitoBean UserDao users;
    @MockitoBean AccountDao accounts;
    @MockitoBean com.ofss.repository.AuditLogDao audits;
    private User user;
    private Account account;
    private MockHttpSession admin;

    @BeforeEach
    void setup() {
        user = new User();
        user.setUserId(103L); user.setName("Customer"); user.setEmail("owner@example.com");
        user.setPhone("9876543210"); user.setPasswordHash("secret-hash");
        user.setCreatedAt(LocalDateTime.of(2026, 9, 1, 10, 0)); user.setUpdatedAt(user.getCreatedAt());
        Role role = new Role(); role.setRoleName("CUSTOMER"); user.setRole(role);
        account = new Account(); account.setAccountId(1000001L); account.setUser(user);
        account.setAccountNumber("500000000001"); account.setBalance(new BigDecimal("5000.00"));
        account.setUpdatedAt(user.getCreatedAt());
        when(users.findAll(Sort.by("userId"))).thenReturn(List.of(user));
        when(users.findById(103L)).thenReturn(Optional.of(user));
        when(accounts.findById(1000001L)).thenReturn(Optional.of(account));
        when(users.changeAdminStatus(anyLong(), any(), any(), any())).thenReturn(1);
        when(accounts.changeAdminStatus(anyLong(), any(), any(), any())).thenReturn(1);
        admin = session("ADMIN");
    }

    private MockHttpSession session(String role) {
        MockHttpSession session = new MockHttpSession();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new LoginPrincipal(200L, "Caller", "caller@example.com", role, UserStatus.ACTIVE), null, List.of()));
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return session;
    }

    private String csrf(MockHttpSession session) throws Exception {
        return mvc.perform(get("/api/admin/users").session(session)).andReturn()
                .getResponse().getHeader("X-CSRF-TOKEN");
    }

    @Test
    void adminListsSafeUserDtos() throws Exception {
        mvc.perform(get("/api/admin/users").session(admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].userId").value(103))
                .andExpect(jsonPath("$[0].role").value("CUSTOMER"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$[0].password").doesNotExist())
                .andExpect(jsonPath("$[0].lockedUntil").doesNotExist())
                .andExpect(jsonPath("$[0].failedLoginAttempts").doesNotExist())
                .andExpect(header().exists("X-CSRF-TOKEN"));
    }

    @ParameterizedTest
    @CsvSource({"ACTIVE,SUSPENDED", "SUSPENDED,ACTIVE", "ACTIVE,LOCKED", "ACTIVE,INACTIVE",
            "LOCKED,ACTIVE", "LOCKED,SUSPENDED", "LOCKED,INACTIVE", "SUSPENDED,INACTIVE", "INACTIVE,ACTIVE", "INACTIVE,SUSPENDED"})
    void validUserStatusChangesOnlyStatusAndTimestamp(UserStatus current, UserStatus target) throws Exception {
        user.setStatus(current);
        mvc.perform(patch("/api/admin/users/103/status").session(admin).header("X-CSRF-TOKEN", csrf(admin))
                .contentType("application/json").content("{\"status\":\"" + target + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(target.name()))
                .andExpect(jsonPath("$.userId").value(103)).andExpect(jsonPath("$.passwordHash").doesNotExist());
        verify(users).changeAdminStatus(eq(103L), eq(current), eq(target), any(LocalDateTime.class));
        verify(users, never()).save(any());
        assertEquals("owner@example.com", user.getEmail());
        assertEquals("secret-hash", user.getPasswordHash());
        assertEquals(103L, user.getUserId());
    }

    @ParameterizedTest
    @CsvSource({"ACTIVE,BLOCKED", "BLOCKED,ACTIVE"})
    void validAccountStatusChangesNeverWriteBalance(AccountStatus current, AccountStatus target) throws Exception {
        account.setStatus(current);
        mvc.perform(patch("/api/admin/accounts/1000001/status").session(admin).header("X-CSRF-TOKEN", csrf(admin))
                .contentType("application/json").content("{\"status\":\"" + target + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(target.name()))
                .andExpect(jsonPath("$.accountId").value(1000001));
        verify(accounts).changeAdminStatus(eq(1000001L), eq(current), eq(target), any(LocalDateTime.class));
        verify(accounts, never()).save(any());
        assertEquals(new BigDecimal("5000.00"), account.getBalance());
        assertEquals(103L, account.getUserId());
        assertEquals("500000000001", account.getAccountNumber());
    }

    @ParameterizedTest
    @ValueSource(strings = {"CUSTOMER", "UNKNOWN"})
    void nonAdminGets403OnAllRoutesDespiteSpoofedRole(String role) throws Exception {
        MockHttpSession caller = session(role);
        String token = csrf(caller);
        mvc.perform(get("/api/admin/users").session(caller).param("role", "ADMIN"))
                .andExpect(status().isForbidden());
        for (String path : List.of("/api/admin/users/103/status", "/api/admin/accounts/1000001/status")) {
            mvc.perform(patch(path).session(caller).header("X-CSRF-TOKEN", token)
                    .param("role", "ADMIN").contentType("application/json").content("{\"status\":\"ACTIVE\"}"))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(users, accounts);
    }

    @Test
    void anonymousGets401OnAllRoutesWithoutCsrf() throws Exception {
        mvc.perform(get("/api/admin/users").param("role", "ADMIN")).andExpect(status().isUnauthorized());
        for (String path : List.of("/api/admin/users/103/status", "/api/admin/accounts/1000001/status")) {
            mvc.perform(patch(path).contentType("application/json").content("{\"status\":\"ACTIVE\"}"))
                    .andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(users, accounts);
    }

    @ParameterizedTest
    @CsvSource({"SUSPENDED,LOCKED", "INACTIVE,LOCKED"})
    void invalidUserTransitionsReturn409(UserStatus current, UserStatus target) throws Exception {
        user.setStatus(current);
        mvc.perform(patch("/api/admin/users/103/status").session(admin).header("X-CSRF-TOKEN", csrf(admin))
                .contentType("application/json").content("{\"status\":\"" + target + "\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").exists());
        verify(users, never()).changeAdminStatus(anyLong(), any(), any(), any());
        assertEquals(current, user.getStatus());
    }

    @ParameterizedTest
    @CsvSource({"ACTIVE,CLOSED", "BLOCKED,CLOSED", "CLOSED,ACTIVE", "CLOSED,BLOCKED", "CLOSED,CLOSED"})
    void invalidAccountTransitionsReturn409(AccountStatus current, AccountStatus target) throws Exception {
        account.setStatus(current);
        mvc.perform(patch("/api/admin/accounts/1000001/status").session(admin).header("X-CSRF-TOKEN", csrf(admin))
                .contentType("application/json").content("{\"status\":\"" + target + "\"}"))
                .andExpect(status().isConflict());
        verify(accounts, never()).changeAdminStatus(anyLong(), any(), any(), any());
        assertEquals(current, account.getStatus());
    }

    @Test
    void repeatingAllowedStatusIsSafeAndDoesNotWrite() throws Exception {
        String token = csrf(admin);
        mvc.perform(patch("/api/admin/users/103/status").session(admin).header("X-CSRF-TOKEN", token)
                .contentType("application/json").content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk());
        mvc.perform(patch("/api/admin/accounts/1000001/status").session(admin).header("X-CSRF-TOKEN", token)
                .contentType("application/json").content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk());
        verify(users, never()).changeAdminStatus(anyLong(), any(), any(), any());
        verify(accounts, never()).changeAdminStatus(anyLong(), any(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"status\":null}", "{\"status\":\"INVALID\"}",
            "{\"status\":\"\"}", "{\"status\":\"ACTIVE\",\"userId\":104}",
            "{\"status\":\"ACTIVE\",\"accountId\":999}", "{\"status\":\"ACTIVE\",\"email\":\"victim@example.com\"}",
            "{\"status\":\"ACTIVE\",\"passwordHash\":\"injected\"}", "{\"status\":\"ACTIVE\",\"balance\":999999}"})
    void malformedOrExtraFieldsReturn400WithoutMutation(String body) throws Exception {
        String token = csrf(admin);
        clearInvocations(users);
        for (String path : List.of("/api/admin/users/103/status", "/api/admin/accounts/1000001/status")) {
            mvc.perform(patch(path).session(admin).header("X-CSRF-TOKEN", token)
                    .contentType("application/json").content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Send only a valid, non-null status field"));
        }
        verifyNoInteractions(users, accounts);
    }

    @Test
    void missingRecordsReturn404() throws Exception {
        String token = csrf(admin);
        for (String path : List.of("/api/admin/users/999/status", "/api/admin/accounts/999/status")) {
            mvc.perform(patch(path).session(admin).header("X-CSRF-TOKEN", token)
                    .contentType("application/json").content("{\"status\":\"ACTIVE\"}"))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    void concurrentStatusChangesReturn409RatherThanOverwrite() throws Exception {
        when(users.changeAdminStatus(anyLong(), any(), any(), any())).thenReturn(0);
        when(accounts.changeAdminStatus(anyLong(), any(), any(), any())).thenReturn(0);
        String token = csrf(admin);
        mvc.perform(patch("/api/admin/users/103/status").session(admin).header("X-CSRF-TOKEN", token)
                .contentType("application/json").content("{\"status\":\"SUSPENDED\"}"))
                .andExpect(status().isConflict());
        mvc.perform(patch("/api/admin/accounts/1000001/status").session(admin).header("X-CSRF-TOKEN", token)
                .contentType("application/json").content("{\"status\":\"BLOCKED\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void adminWritesStillRequireCsrf() throws Exception {
        mvc.perform(patch("/api/admin/users/103/status").session(admin)
                .contentType("application/json").content("{\"status\":\"SUSPENDED\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/admin/accounts/1000001/status").session(admin).header("X-CSRF-TOKEN", "wrong")
                .contentType("application/json").content("{\"status\":\"BLOCKED\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(users, accounts);
    }

    @Test
    void separateAdminChainDoesNotGrantAdminCustomerAccess() throws Exception {
        mvc.perform(get("/api/accounts/current").session(admin)).andExpect(status().isForbidden());
        verifyNoInteractions(accounts);
    }
}

