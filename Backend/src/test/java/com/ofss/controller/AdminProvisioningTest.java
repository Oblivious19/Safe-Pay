package com.ofss.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import com.ofss.beans.*;
import com.ofss.config.*;
import com.ofss.repository.*;
import com.ofss.services.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({AdminProvisioningController.class, AdminController.class})
@Import({AdminProvisioningService.class, AdminSecurityConfig.class, LoginSecurityConfig.class})
class AdminProvisioningTest extends WebSecuritySliceSupport {
    @Autowired MockMvc mvc;
    @MockitoBean UserDao users;
    @MockitoBean AuditLogDao audits;
    @MockitoBean RoleDao roles;
    @MockitoBean AccountDao accounts;
    @MockitoBean AdminService adminService;
    private static final String BODY = "{\"name\":\"New Admin\",\"email\":\"new@example.test\",\"phone\":\"9876543210\",\"initialPassword\":\"Temporary#123\"}";

    MockHttpSession session(String role) {
        var session = new MockHttpSession();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new LoginPrincipal(200L, "Admin", "any@example.test", role, UserStatus.ACTIVE), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role))));
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return session;
    }
    String csrf(MockHttpSession session) throws Exception {
        return mvc.perform(get("/api/admin/users").session(session)).andReturn().getResponse().getHeader("X-CSRF-TOKEN");
    }
    void role() {
        Role role = new Role(); role.setRoleName("ADMIN"); role.setRoleId(2L);
        when(roles.findByRoleName("ADMIN")).thenReturn(Optional.of(role));
    }

    @Test void createsOnlyAdminWithHashAndNoBankAccount() throws Exception {
        role();
        when(users.saveAndFlush(any())).thenAnswer(call -> {
            User user = call.getArgument(0);
            assertNull(user.getUserId());
            assertEquals("ADMIN", user.getRole().getRoleName());
            assertEquals(UserStatus.ACTIVE, user.getStatus());
            assertEquals(0, user.getFailedLoginAttempts());
            assertNull(user.getLockedUntil()); assertNull(user.getLastLoginAt());
            assertNotNull(user.getCreatedAt()); assertEquals(user.getCreatedAt(), user.getUpdatedAt());
            assertTrue(new BCryptPasswordEncoder().matches("Temporary#123", user.getPasswordHash()));
            user.setUserId(300L); return user;
        });
        var admin = session("ADMIN");
        mvc.perform(post("/api/admin/users").session(admin).header("X-CSRF-TOKEN", csrf(admin))
                .contentType("application/json").content(BODY))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.userId").value(300))
                .andExpect(jsonPath("$.role").value("ADMIN")).andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.password").doesNotExist()).andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.initialPassword").doesNotExist()).andExpect(jsonPath("$.loginPinHash").doesNotExist());
        verifyNoInteractions(accounts);
    }
    @Test void customerForbiddenAndAnonymousUnauthorized() throws Exception {
        var customer = session("CUSTOMER");
        mvc.perform(post("/api/admin/users").session(customer).header("X-CSRF-TOKEN", csrf(customer))
                .contentType("application/json").content(BODY)).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/users").contentType("application/json").content(BODY))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(users, roles, accounts);
    }
    @Test void csrfRequired() throws Exception {
        mvc.perform(post("/api/admin/users").session(session("ADMIN")).contentType("application/json").content(BODY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(users, roles, accounts);
    }
    @ParameterizedTest @ValueSource(strings = {"email", "phone"})
    void duplicateReturns409(String field) throws Exception {
        if (field.equals("email")) when(users.findByEmail("new@example.test")).thenReturn(Optional.of(new User()));
        else when(users.existsByPhone("9876543210")).thenReturn(true);
        var admin = session("ADMIN");
        mvc.perform(post("/api/admin/users").session(admin).header("X-CSRF-TOKEN", csrf(admin))
                .contentType("application/json").content(BODY)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").exists());
        verify(users, never()).saveAndFlush(any()); verifyNoInteractions(accounts);
    }
    @ParameterizedTest @ValueSource(strings = {"role", "userId", "status", "passwordHash", "pin"})
    void extraFieldsRejected(String field) throws Exception {
        var admin = session("ADMIN");
        String body = BODY.substring(0, BODY.length()-1) + ",\"" + field + "\":\"injected\"}";
        mvc.perform(post("/api/admin/users").session(admin).header("X-CSRF-TOKEN", csrf(admin))
                .contentType("application/json").content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(users, roles, accounts);
    }
    @ParameterizedTest @ValueSource(strings = {"{}", "null", "\"\"", "[]"})
    void malformedBodyRejected(String body) throws Exception {
        var admin = session("ADMIN");
        mvc.perform(post("/api/admin/users").session(admin).header("X-CSRF-TOKEN", csrf(admin))
                .contentType("application/json").content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(users, roles, accounts);
    }
    @ParameterizedTest @ValueSource(strings = {"short", "        ", "ééééééééééééééééééééééééééééééééééééé"})
    void invalidPasswordRejected(String password) throws Exception {
        var admin = session("ADMIN");
        mvc.perform(post("/api/admin/users").session(admin).header("X-CSRF-TOKEN", csrf(admin))
                .contentType("application/json").content(BODY.replace("Temporary#123", password)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(users, roles, accounts);
    }
    @Test void concurrentDuplicateDoesNotExposeOracleDetails() throws Exception {
        role();
        when(users.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("internal",
                new SQLException("ORA-00001 SYS.C008749", "23000", 1)));
        var admin = session("ADMIN");
        mvc.perform(post("/api/admin/users").session(admin).header("X-CSRF-TOKEN", csrf(admin))
                .contentType("application/json").content(BODY)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A record with these details already exists"));
    }
    @Test void missingAdminRoleFailsClosed() throws Exception {
        var admin = session("ADMIN");
        mvc.perform(post("/api/admin/users").session(admin).header("X-CSRF-TOKEN", csrf(admin))
                .contentType("application/json").content(BODY)).andExpect(status().isServiceUnavailable());
        verify(users, never()).saveAndFlush(any());
    }

    @ParameterizedTest @ValueSource(strings = {"name", "email", "phone", "initialPassword"})
    void missingRequiredFieldIs400(String field) throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var body = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(BODY);
        body.remove(field);
        var admin = session("ADMIN");
        mvc.perform(post("/api/admin/users").session(admin).header("X-CSRF-TOKEN", csrf(admin))
                .contentType("application/json").content(body.toString())).andExpect(status().isBadRequest());
        verifyNoInteractions(users, roles, accounts);
    }

    @Test void requestSerializationAndLoggingDoNotExposePassword() throws Exception {
        var request = new AdminProvisionRequest("Name", "new@example.test", "9876543210", "Temporary#123");
        assertFalse(request.toString().contains("Temporary#123"));
        assertFalse(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(request).contains("Temporary#123"));
    }
}
