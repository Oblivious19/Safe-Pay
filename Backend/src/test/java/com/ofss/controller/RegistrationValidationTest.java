package com.ofss.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

import java.sql.SQLException;
import java.util.Optional;
import com.ofss.beans.Role;
import com.ofss.beans.User;
import com.ofss.excp.GlobalExceptionHandler;
import com.ofss.repository.*;
import com.ofss.services.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RegistrationValidationTest {
    private UserDao users;
    private RoleDao roles;
    private AccountDao accounts;
    private MockMvc mvc;
    private static final String VALID = """
            {"name":"Demo", "email":"demo@example.com", "phone":"9876543210", "password":"Test@12345"}
            """;

    @BeforeEach
    void setup() {
        users = mock(UserDao.class);
        roles = mock(RoleDao.class);
        accounts = mock(AccountDao.class);
        mvc = MockMvcBuilders.standaloneSetup(new AuthController(new UserServiceImpl(users, accounts, roles)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void missingPasswordReturns400() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(content().json("{\"message\":\"Password is required\"}"));
        verifyNoInteractions(users, roles, accounts);
    }

    @Test
    void blankPasswordReturns400() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(VALID.replace("Test@12345", "   ")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Password is required"));
        verifyNoInteractions(users, roles, accounts);
    }

    @Test
    void passwordHashIsNotTheRequestField() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(VALID.replace("\"password\"", "\"passwordHash\"")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(users, roles, accounts);
    }

    @Test
    void duplicateEmailReturns409() throws Exception {
        when(users.findByEmail("demo@example.com")).thenReturn(Optional.of(new User()));
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Email is already registered"));
    }

    @Test
    void duplicatePhoneReturns409() throws Exception {
        when(users.existsByPhone("9876543210")).thenReturn(true);
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Phone number is already registered"));
    }

    @Test
    void validRegistrationHashesPasswordAndReturns201WithoutSecrets() throws Exception {
        when(roles.findByRoleName("CUSTOMER")).thenReturn(Optional.of(new Role()));
        when(accounts.nextAccountNumber()).thenReturn("500000000010");
        when(users.save(any(User.class))).thenAnswer(call -> {
            User user = call.getArgument(0);
            assertTrue(new BCryptPasswordEncoder().matches("Test@12345", user.getPasswordHash()));
            user.setUserId(106L);
            return user;
        });
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.userId").value(106))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void concurrentDatabaseDuplicateReturnsSafe409() throws Exception {
        databaseFailure(new SQLException("ORA-00001 unique constraint SYS.C008749", "23000", 1));
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isConflict())
                .andExpect(content().json("{\"message\":\"A record with these details already exists\"}"));
    }

    @Test
    void otherDatabaseFailureIsNotMisreportedAsDuplicate() throws Exception {
        databaseFailure(new SQLException("ORA-01400 cannot insert NULL secret SQL", "23000", 1400));
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isInternalServerError())
                .andExpect(content().json("{\"message\":\"Unable to save the record\"}"));
    }

    private void databaseFailure(SQLException cause) {
        when(roles.findByRoleName("CUSTOMER")).thenReturn(Optional.of(new Role()));
        when(users.save(any(User.class))).thenThrow(new DataIntegrityViolationException("SQL details", cause));
    }
}
