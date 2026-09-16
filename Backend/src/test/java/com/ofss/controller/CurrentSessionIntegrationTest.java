package com.ofss.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.util.List;
import java.util.UUID;
import com.ofss.beans.*;
import com.ofss.repository.RoleDao;
import com.ofss.repository.UserDao;
import com.ofss.services.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:safepay-session-access;MODE=Oracle;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"})
@ActiveProfiles("local")
@AutoConfigureMockMvc
@Transactional
class CurrentSessionIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserService registration;
    @Autowired UserDao users;
    @Autowired RoleDao roles;
    @Autowired JdbcTemplate jdbc;

    @ParameterizedTest
    @CsvSource({"CUSTOMER,SUSPENDED", "CUSTOMER,LOCKED", "CUSTOMER,INACTIVE",
            "ADMIN,SUSPENDED", "ADMIN,LOCKED", "ADMIN,INACTIVE"})
    void databaseStatusRevokesExistingSessionOnNextRequest(String role, String target) throws Exception {
        User user = createUser(role);
        MockHttpSession session = session(user, role);
        String path = role.equals("ADMIN") ? "/api/admin/users" : "/api/accounts";
        mvc.perform(get(path).session(session)).andExpect(status().isOk());
        jdbc.update("update users set status=? where user_id=?", target, user.getUserId());
        mvc.perform(get(path).session(session)).andExpect(status().isUnauthorized());
        assertTrue(session.isInvalid());
    }

    @Test void storedRoleChangeRevokesOldAdministratorSession() throws Exception {
        User user = createUser("ADMIN");
        MockHttpSession session = session(user, "ADMIN");
        jdbc.update("update users set role_id=? where user_id=?",
                roles.findByRoleName("CUSTOMER").orElseThrow().getRoleId(), user.getUserId());
        mvc.perform(get("/api/admin/users").session(session)).andExpect(status().isUnauthorized());
        assertTrue(session.isInvalid());
    }

    @Test void newDatabaseProjectionDoesNotExposeCredentials() throws Exception {
        User user = createUser("CUSTOMER");
        var result = mvc.perform(get("/api/accounts").session(session(user, "CUSTOMER")))
                .andExpect(status().isOk()).andReturn();
        String body = result.getResponse().getContentAsString();
        assertFalse(body.contains("password"));
        assertFalse(body.contains(user.getPasswordHash()));
    }

    private User createUser(String role) {
        User user = new User(); user.setName("Session access check");
        user.setEmail("session-" + UUID.randomUUID() + "@example.test");
        user.setPhone(String.format("%010d", Math.abs(UUID.randomUUID().getLeastSignificantBits() % 9000000000L)));
        user.setPasswordHash("SessionAccess#2026");
        User saved = registration.register(user);
        if (role.equals("ADMIN")) saved.setRole(roles.findByRoleName("ADMIN").orElseThrow());
        return users.saveAndFlush(saved);
    }

    private MockHttpSession session(User user, String role) {
        var session = new MockHttpSession();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new LoginPrincipal(user.getUserId(), user.getName(), user.getEmail(), role, UserStatus.ACTIVE),
                null, List.of()));
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return session;
    }
}
