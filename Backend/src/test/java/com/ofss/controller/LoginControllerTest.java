package com.ofss.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.Optional;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ofss.beans.*;
import com.ofss.config.LoginSecurityConfig;
import com.ofss.config.WebConfig;
import com.ofss.repository.UserDao;
import com.ofss.services.LoginService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(LoginController.class)
@Import({LoginService.class, LoginSecurityConfig.class, WebConfig.class})
class LoginControllerTest extends WebSecuritySliceSupport {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired HttpSessionSecurityContextRepository repository;
    @MockitoBean UserDao users;
    private User user;
    private static final String REQUEST = """
        {"email":"customer@example.com", "password":"Test@12345"}
        """;

    @BeforeEach
    void setup() {
        Role role = new Role();
        role.setRoleName("CUSTOMER");
        user = new User();
        user.setUserId(103L);
        user.setName("Test Customer");
        user.setEmail("customer@example.com");
        user.setPhone("9876543210");
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        user.setFailedLoginAttempts(2);
        user.setPasswordHash(new BCryptPasswordEncoder().encode("Test@12345"));
    }

    @Test
    void validLoginReturnsOnlySafeFieldsAndPersistsSessionIdentity() throws Exception {
        when(users.findForLoginByEmail(user.getEmail())).thenReturn(Optional.of(user));
        MockHttpSession original = new MockHttpSession();
        String oldId = original.getId();
        var result = mvc.perform(post("/api/auth/login").session(original)
                .contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isOk()).andExpect(jsonPath("$.userId").value(103))
                .andExpect(jsonPath("$.name").value("Test Customer"))
                .andExpect(jsonPath("$.email").value(user.getEmail()))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.status").value("ACTIVE")).andReturn();
        String body = result.getResponse().getContentAsString();
        assertEquals(5, mapper.readTree(body).size());
        assertFalse(body.contains(user.getPasswordHash()));
        assertFalse(body.contains("Test@12345"));
        assertNotNull(user.getLastLoginAt());
        assertEquals(0, user.getFailedLoginAttempts());
        verify(users).save(user);
        var session = result.getRequest().getSession(false);
        assertNotNull(session);
        assertNotEquals(oldId, session.getId());
        SecurityContext context = (SecurityContext) session.getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertTrue(context.getAuthentication().isAuthenticated());
        assertNull(context.getAuthentication().getCredentials());
        assertEquals(103L, ((LoginPrincipal) context.getAuthentication().getPrincipal()).userId());
        MockHttpServletRequest next = new MockHttpServletRequest();
        next.setSession((MockHttpSession) session);
        assertEquals(context.getAuthentication(), repository.loadDeferredContext(next).get().getAuthentication());
    }

    @Test
    void firstWrongPasswordIncrementsCountWithoutCreatingSession() throws Exception {
        user.setFailedLoginAttempts(0);
        when(users.findForLoginByEmail(user.getEmail())).thenReturn(Optional.of(user));
        var result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(REQUEST.replace("Test@12345", "wrong")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password")).andReturn();
        verify(users).save(user);
        assertEquals(1, user.getFailedLoginAttempts());
        assertEquals(UserStatus.ACTIVE, user.getStatus());
        assertNull(user.getLockedUntil());
        assertNull(user.getLastLoginAt());
        assertNull(result.getRequest().getSession(false));
    }

    @Test
    void unknownEmailReturns401() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
        verify(users, never()).save(any());
        verify(users).findForLoginByEmail("customer@example.com");
        verifyNoMoreInteractions(users);
    }

    @Test
    void repeatedWrongPasswordsIncrementThenSuccessfulLoginResetsCount() throws Exception {
        user.setFailedLoginAttempts(0);
        when(users.findForLoginByEmail(user.getEmail())).thenReturn(Optional.of(user));
        for (int attempt = 1; attempt <= 3; attempt++) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content(REQUEST.replace("Test@12345", "wrong")))
                    .andExpect(status().isUnauthorized());
            assertEquals(attempt, user.getFailedLoginAttempts());
            assertNull(user.getLastLoginAt());
            assertEquals(UserStatus.ACTIVE, user.getStatus());
            assertNull(user.getLockedUntil());
        }
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isOk());
        assertEquals(0, user.getFailedLoginAttempts());
        assertNotNull(user.getLastLoginAt());
        verify(users, times(4)).save(user);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"email\":\"customer@example.com\"}",
            "{\"password\":\"Test@12345\"}",
            "{\"email\":\"customer@example.com\",\"password\":\" \"}",
            "{\"email\":\" \",\"password\":\"Test@12345\"}"})
    void missingOrBlankCredentialsDoNotModifyUsers(String request) throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(users);
    }

    @Test
    void phoneAndPasswordEstablishTheSameSafeSessionIdentity() throws Exception {
        when(users.findForLoginByPhone(user.getPhone())).thenReturn(Optional.of(user));
        MockHttpSession original = new MockHttpSession();
        String oldId = original.getId();
        var result = mvc.perform(post("/api/auth/login").session(original)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"9876543210\",\"password\":\"Test@12345\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.userId").value(103))
                .andExpect(jsonPath("$.email").value(user.getEmail()))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.password").doesNotExist()).andReturn();
        assertEquals(5, mapper.readTree(result.getResponse().getContentAsString()).size());
        assertEquals(0, user.getFailedLoginAttempts());
        var session = result.getRequest().getSession(false);
        assertNotNull(session);
        assertNotEquals(oldId, session.getId());
        SecurityContext context = (SecurityContext) session.getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertEquals(103L, ((LoginPrincipal) context.getAuthentication().getPrincipal()).userId());
        assertNull(context.getAuthentication().getCredentials());
        verify(users).findForLoginByPhone("9876543210");
        verify(users, never()).findForLoginByEmail(anyString());
        verify(users).save(user);
    }

    @Test
    void unknownPhoneReturnsUnauthorizedWithoutCreatingASession() throws Exception {
        var result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"9876543210\",\"password\":\"Test@12345\"}"))
                .andExpect(status().isUnauthorized()).andReturn();
        assertNull(result.getRequest().getSession(false));
        verify(users).findForLoginByPhone("9876543210");
        verify(users, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"email\":\"customer@example.com\",\"phone\":\"9876543210\",\"password\":\"x\"}",
        "{\"email\":null,\"phone\":\"9876543210\",\"password\":\"x\"}",
        "{\"phone\":9876543210,\"password\":\"x\"}",
        "{\"phone\":\"987654321\",\"password\":\"x\"}",
        "{\"phone\":\"98765432101\",\"password\":\"x\"}",
        "{\"phone\":\"98 7654321\",\"password\":\"x\"}",
        "{\"phone\":\"+919876543210\",\"password\":\"x\"}",
        "{\"phone\":\"9876543210\",\"password\":42}",
        "{\"phone\":\"9876543210\",\"password\":null}",
        "{\"phone\":\"9876543210\",\"password\":\" \"}",
        "{\"phone\":\"9876543210\",\"pin\":\"1234\"}",
        "{\"phone\":\"9876543210\",\"password\":\"x\",\"role\":\"ADMIN\"}",
        "{\"email\":\"not-an-email\",\"password\":\"x\"}",
        "{\"email\":\" customer@example.com\",\"password\":\"x\"}",
        "[]", "null"
    })
    void malformedOrMixedCredentialsReturn400BeforeDatabaseAccess(String request) throws Exception {
        var result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isBadRequest()).andReturn();
        verifyNoInteractions(users);
        assertNull(result.getRequest().getSession(false));
    }

    @ParameterizedTest
    @EnumSource(value = UserStatus.class, names = {"SUSPENDED", "INACTIVE"})
    void wrongPasswordForNonActiveUserDoesNotIncrement(UserStatus status) throws Exception {
        user.setStatus(status);
        when(users.findForLoginByEmail(user.getEmail())).thenReturn(Optional.of(user));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(REQUEST.replace("Test@12345", "wrong")))
                .andExpect(status().isUnauthorized());
        assertEquals(2, user.getFailedLoginAttempts());
        assertNull(user.getLastLoginAt());
        verify(users, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = UserStatus.class, names = {"SUSPENDED", "INACTIVE", "LOCKED"})
    void nonActiveUserReturns403WithoutUpdatingUser(UserStatus status) throws Exception {
        user.setStatus(status);
        when(users.findForLoginByEmail(user.getEmail())).thenReturn(Optional.of(user));
        var result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isForbidden()).andReturn();
        assertNull(result.getRequest().getSession(false));
        assertNull(user.getLastLoginAt());
        assertEquals(2, user.getFailedLoginAttempts());
        verify(users, never()).save(any());
    }

    @Test
    void browserSimpleRequestCannotSubmitLogin() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.TEXT_PLAIN).content(REQUEST))
                .andExpect(status().isUnsupportedMediaType());
        verifyNoInteractions(users);
    }

    @Test
    void fifthFailureAndCorrectPasswordDuringLockReturn403WithoutSession() throws Exception {
        user.setFailedLoginAttempts(4);
        when(users.findForLoginByEmail(user.getEmail())).thenReturn(Optional.of(user));
        var failed = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(REQUEST.replace("Test@12345", "wrong")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Login is locked. Please try again after the lock period expires."))
                .andReturn();
        assertNull(failed.getRequest().getSession(false));
        var lockedUntil = user.getLockedUntil();
        var correct = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isForbidden()).andReturn();
        assertNull(correct.getRequest().getSession(false));
        assertEquals(UserStatus.LOCKED, user.getStatus());
        assertEquals(5, user.getFailedLoginAttempts());
        assertEquals(lockedUntil, user.getLockedUntil());
        assertNull(user.getLastLoginAt());
        verify(users, times(1)).save(user);
    }

    @Test
    void expiredLockWithCorrectPasswordCreatesActiveSession() throws Exception {
        user.setStatus(UserStatus.LOCKED);
        user.setFailedLoginAttempts(5);
        user.setLockedUntil(java.time.LocalDateTime.now().minusMinutes(1));
        when(users.findForLoginByEmail(user.getEmail())).thenReturn(Optional.of(user));
        var result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE")).andReturn();
        assertNotNull(result.getRequest().getSession(false));
        assertEquals(UserStatus.ACTIVE, user.getStatus());
        assertEquals(0, user.getFailedLoginAttempts());
        assertNull(user.getLockedUntil());
        assertNotNull(user.getLastLoginAt());
        verify(users).save(user);
    }

    @Test
    void untrustedBrowserOriginCannotSubmitLogin() throws Exception {
        mvc.perform(post("/api/auth/login").header("Origin", "https://untrusted.example")
                .contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isForbidden());
        verifyNoInteractions(users);
    }
}

