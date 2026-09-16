package com.ofss.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ofss.beans.UserStatus;
import com.ofss.repository.UserDao;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Uses real JPA transactions to prove both identifiers share persisted lockout state. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:safepay-mobile-password;MODE=Oracle;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@ActiveProfiles("local")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MobilePasswordLoginIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserDao users;

    @Test
    void registeredMobileUsesExistingPasswordAndPersistsSharedLockoutAcrossIdentifierSwitches() throws Exception {
        String email = "mobile-password@example.test", phone = "0123456789", password = "MobilePassword#2026";
        var registration = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("name", "Mobile customer", "email", email,
                        "phone", phone, "password", password))))
                .andExpect(status().isCreated()).andReturn();
        long userId = mapper.readTree(registration.getResponse().getContentAsString()).path("userId").asLong();
        var firstLogin = login("phone", phone, password, 200);
        assertEquals(userId, mapper.readTree(firstLogin.getResponse().getContentAsString()).path("userId").asLong());
        assertNotNull(firstLogin.getRequest().getSession(false));
        assertEquals(0, users.findById(userId).orElseThrow().getFailedLoginAttempts());

        for (int attempt = 1; attempt <= 5; attempt++) {
            boolean byPhone = attempt % 2 == 1;
            var failed = login(byPhone ? "phone" : "email", byPhone ? phone : email, "wrong", attempt == 5 ? 403 : 401);
            assertNull(failed.getRequest().getSession(false));
            assertEquals(attempt, users.findById(userId).orElseThrow().getFailedLoginAttempts());
        }
        var locked = users.findById(userId).orElseThrow();
        assertEquals(UserStatus.LOCKED, locked.getStatus());
        assertNotNull(locked.getLockedUntil());
        login("email", email, password, 403);
        login("phone", phone, password, 403);
        assertEquals(5, users.findById(userId).orElseThrow().getFailedLoginAttempts());

        locked.setLockedUntil(LocalDateTime.now().minusSeconds(1));
        users.saveAndFlush(locked);
        login("phone", phone, password, 200);
        var active = users.findById(userId).orElseThrow();
        assertEquals(UserStatus.ACTIVE, active.getStatus());
        assertEquals(0, active.getFailedLoginAttempts());
        assertNull(active.getLockedUntil());
    }

    private org.springframework.test.web.servlet.MvcResult login(String identifier, String value, String password, int expected) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of(identifier, value, "password", password))))
                .andExpect(status().is(expected)).andReturn();
    }
}
