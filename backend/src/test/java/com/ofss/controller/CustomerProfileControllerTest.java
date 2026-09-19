package com.ofss.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ofss.beans.User;
import com.ofss.beans.UserStatus;
import com.ofss.excp.GlobalExceptionHandler;
import com.ofss.repository.UserDao;
import com.ofss.security.SafePayPrincipal;
import com.ofss.services.UserServiceImpl;

class CustomerProfileControllerTest {
    private final UserDao users = mock(UserDao.class);
    private MockMvc mvc;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        var principal = new SafePayPrincipal(101L, "customer@example.invalid", "test-hash", UserStatus.ACTIVE,
                0L, List.of(new SimpleGrantedAuthority("CUSTOMER")));
        authentication = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        mvc = MockMvcBuilders.standaloneSetup(new CustomerProfileController(new UserServiceImpl(users)))
                .setControllerAdvice(new GlobalExceptionHandler(Clock.systemUTC())).build();
    }

    @Test
    void usesPrincipalAndReturnsOnlyFiveSafeFields() throws Exception {
        User user = User.createActiveUser("Example", "customer@example.invalid", "+919000000001",
                "sensitive-hash", OffsetDateTime.parse("2026-09-19T10:00:00Z"));
        ReflectionTestUtils.setField(user, "userId", 101L);
        when(users.findById(101L)).thenReturn(Optional.of(user));
        mvc.perform(get("/api/v1/users/me").principal(authentication).param("userId", "999"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$.userId").value("101"))
                .andExpect(jsonPath("$.fullName").value("Example"))
                .andExpect(jsonPath("$.email").value("customer@example.invalid"))
                .andExpect(jsonPath("$.mobileNumber").value("+919000000001"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.securityVersion").doesNotExist());
        verify(users).findById(101L);
        verify(users, never()).findById(999L);
    }

    @Test
    void missingProfileReturnsStable404() throws Exception {
        mvc.perform(get("/api/v1/users/me").principal(authentication))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.errorCode").value("USER_NOT_FOUND"));
    }

}
