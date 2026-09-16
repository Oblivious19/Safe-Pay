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
import com.ofss.services.LoginService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({LoginController.class, AccountController.class})
@Import({LoginService.class, LoginSecurityConfig.class, CustomerResourceSecurityConfig.class,
        LogoutSecurityConfig.class})
class LogoutTest extends WebSecuritySliceSupport {
    @Autowired MockMvc mvc;
    @MockitoBean UserDao users;
    @MockitoBean AccountDao accounts;

    private MockHttpSession login() throws Exception {
        Role role = new Role(); role.setRoleName("CUSTOMER");
        User user = new User(); user.setUserId(103L); user.setName("Test");
        user.setEmail("logout@example.com"); user.setRole(role); user.setStatus(UserStatus.ACTIVE);
        user.setPasswordHash(new BCryptPasswordEncoder().encode("Test@12345"));
        when(users.findForLoginByEmail(user.getEmail())).thenReturn(Optional.of(user));
        Account account = new Account(); account.setAccountId(1000001L); account.setUser(user);
        when(accounts.findFirstByUserUserIdOrderByAccountId(103L)).thenReturn(Optional.of(account));
        return (MockHttpSession) mvc.perform(post("/api/auth/login").contentType("application/json")
                .content("{\"email\":\"logout@example.com\",\"password\":\"Test@12345\"}"))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }

    private String csrf(MockHttpSession session) throws Exception {
        return mvc.perform(get("/api/accounts/current").session(session)).andExpect(status().isOk())
                .andReturn().getResponse().getHeader("X-CSRF-TOKEN");
    }

    @Test
    void authenticatedLogoutInvalidatesSessionAndClearsContext() throws Exception {
        MockHttpSession session = login();
        SecurityContext stored = (SecurityContext) session.getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        mvc.perform(post("/api/auth/logout").session(session).header("X-CSRF-TOKEN",csrf(session)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(content().json("{\"message\":\"Logged out successfully\"}"))
                .andExpect(cookie().maxAge("JSESSIONID",0))
                .andExpect(cookie().value("JSESSIONID",org.hamcrest.Matchers.nullValue()));
        assertTrue(session.isInvalid());
        assertNull(stored.getAuthentication());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void protectedGetReturns401AfterLogoutIncludingStaleCookie() throws Exception {
        MockHttpSession session = login();
        String previousId = session.getId();
        mvc.perform(post("/api/auth/logout").session(session).header("X-CSRF-TOKEN",csrf(session)))
                .andExpect(status().isOk());
        assertTrue(session.isInvalid());
        // The container cannot resolve a cookie to an invalidated server session.
        mvc.perform(get("/api/accounts/current").cookie(new Cookie("JSESSIONID",previousId)))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/transactions").cookie(new Cookie("JSESSIONID",previousId)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousAndRepeatedLogoutAreSafeWithoutCreatingSession() throws Exception {
        for (int i = 0; i < 2; i++) {
            var result = mvc.perform(post("/api/auth/logout").cookie(new Cookie("JSESSIONID","expired")))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith("application/json"))
                    .andExpect(content().json("{\"message\":\"Logged out successfully\"}"))
                    .andReturn();
            assertNull(result.getRequest().getSession(false));
        }
        verifyNoInteractions(users,accounts);
    }

    @Test
    void missingCsrfCannotLogOutAuthenticatedUser() throws Exception {
        MockHttpSession session = login();
        mvc.perform(post("/api/auth/logout").session(session)).andExpect(status().isForbidden());
        assertFalse(session.isInvalid());
        mvc.perform(get("/api/accounts/current").session(session)).andExpect(status().isOk());
    }

    @Test
    void getCannotLogOutUser() throws Exception {
        MockHttpSession session = login();
        mvc.perform(get("/api/auth/logout").session(session)).andExpect(status().isForbidden());
        assertFalse(session.isInvalid());
        mvc.perform(get("/api/accounts/current").session(session)).andExpect(status().isOk());
    }
}

