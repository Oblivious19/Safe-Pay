package com.ofss.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.util.List;
import com.ofss.beans.*;
import com.ofss.beans.AdminAccountDtos;
import com.ofss.config.*;
import com.ofss.services.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({VerificationController.class, ProfileController.class, AdminAccountController.class})
@Import({CustomerResourceSecurityConfig.class, AdminSecurityConfig.class, LoginSecurityConfig.class})
class FeatureAuthorizationTest extends WebSecuritySliceSupport {
    @Autowired MockMvc mvc;
    @MockitoBean VerificationService verifications;
    @MockitoBean ProfileService profiles;
    @MockitoBean AdminAccountService accounts;

    private MockHttpSession session(String role) {
        MockHttpSession session = new MockHttpSession();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new LoginPrincipal(7L, "Old", "old@example.com", role, UserStatus.ACTIVE), null, List.of()));
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return session;
    }
    private String csrf(MockHttpSession session, boolean admin) throws Exception {
        // Customer GET is intentionally unmapped in this slice, but the security filter issues its token.
        return mvc.perform(get(admin ? "/api/admin/accounts" : "/api/transactions").session(session))
                .andReturn().getResponse().getHeader("X-CSRF-TOKEN");
    }

    @Test
    void verificationRequiresCustomerSessionCsrfAndKey() throws Exception {
        String path = "/api/transactions/21/verify";
        mvc.perform(post(path).contentType("application/json").content("{\"password\":\"correct\"}"))
                .andExpect(status().isUnauthorized());
        var customer = session("CUSTOMER");
        mvc.perform(post(path).session(customer).header("Idempotency-Key", "k")
                .contentType("application/json").content("{\"password\":\"correct\"}"))
                .andExpect(status().isForbidden());
        String token = csrf(customer, false);
        mvc.perform(post(path).session(customer).header("X-CSRF-TOKEN", token)
                .contentType("application/json").content("{\"password\":\"correct\"}"))
                .andExpect(status().isBadRequest());
        var admin = session("ADMIN");
        mvc.perform(post(path).session(admin).header("Idempotency-Key", "k").header("X-CSRF-TOKEN", csrf(admin, false))
                .contentType("application/json").content("{\"password\":\"correct\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(verifications);
    }

    @Test
    void incorrectStepUpPasswordKeepsSessionAndReturnsSafe403() throws Exception {
        var customer = session("CUSTOMER");
        when(verifications.verify(21L, 7L, "wrong", "k")).thenThrow(new BadCredentialsException("private details"));
        mvc.perform(post("/api/transactions/21/verify").session(customer).header("Idempotency-Key", "k")
                .header("X-CSRF-TOKEN", csrf(customer, false)).contentType("application/json")
                .content("{\"password\":\"wrong\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Incorrect password; payment remains on hold"));
        assertNotNull(customer.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY));
    }

    @Test
    void verificationRejectsSpoofedApprovalAndOwnerFields() throws Exception {
        var customer = session("CUSTOMER");
        mvc.perform(post("/api/transactions/21/verify").session(customer).header("Idempotency-Key", "k")
                .header("X-CSRF-TOKEN", csrf(customer, false)).contentType("application/json")
                .content("{\"password\":\"correct\",\"approved\":true,\"userId\":8}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(verifications);
    }

    @Test
    void profileUpdateUsesSessionOwnerAndRefreshesSavedIdentity() throws Exception {
        var customer = session("CUSTOMER");
        when(profiles.update(eq(7L), any())).thenReturn(new UserProfileResponse(7L, "New", "new@example.com",
                "1234567890", UserStatus.ACTIVE, null, null));
        mvc.perform(put("/api/users/current").session(customer).header("X-CSRF-TOKEN", csrf(customer, false))
                .contentType("application/json").content("{\"name\":\"New\",\"email\":\"new@example.com\",\"phone\":\"1234567890\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value("new@example.com"))
                .andExpect(jsonPath("$.password").doesNotExist());
        var context = (SecurityContext) customer.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertEquals("new@example.com", ((LoginPrincipal) context.getAuthentication().getPrincipal()).email());
        verify(profiles).update(eq(7L), any());
    }

    @Test
    void profileDoesNotPermitOtherIdOrRoleInjection() throws Exception {
        var customer = session("CUSTOMER");
        String token = csrf(customer, false);
        mvc.perform(put("/api/users/8").session(customer).header("X-CSRF-TOKEN", token)
                .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/users/current").session(customer).header("X-CSRF-TOKEN", token)
                .contentType("application/json").content("{\"name\":\"New\",\"email\":\"new@example.com\",\"phone\":\"1234567890\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(profiles);
    }

    @Test
    void adminCanListAndEditAccountBalanceTypeButCustomerCannot() throws Exception {
        var admin = session("ADMIN");
        var response = new AdminAccountDtos.Response(11L, 7L, "500000000011", AccountType.CURRENT,
                new BigDecimal("20000.00"), AccountStatus.ACTIVE, null);
        when(accounts.list()).thenReturn(List.of(response));
        when(accounts.update(eq(11L), any())).thenReturn(response);
        mvc.perform(get("/api/admin/accounts").session(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].balance").value(20000)).andExpect(jsonPath("$[0].user").doesNotExist());
        mvc.perform(put("/api/admin/accounts/11").session(admin).header("X-CSRF-TOKEN", csrf(admin, true))
                .contentType("application/json").content("{\"balance\":20000.00,\"accountType\":\"CURRENT\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accountType").value("CURRENT"));
        var customer = session("CUSTOMER");
        mvc.perform(get("/api/admin/accounts").session(customer)).andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/accounts/11").session(customer).header("X-CSRF-TOKEN", csrf(customer, true))
                .contentType("application/json").content("{\"balance\":20000.00,\"accountType\":\"CURRENT\"}"))
                .andExpect(status().isForbidden());
        verify(accounts, times(1)).update(eq(11L), any());
    }

    @Test
    void adminBalanceEditRejectsStatusInjectionAndMissingCsrf() throws Exception {
        var admin = session("ADMIN");
        mvc.perform(put("/api/admin/accounts/11").session(admin).contentType("application/json")
                .content("{\"balance\":20000.00,\"accountType\":\"CURRENT\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/accounts/11").session(admin).header("X-CSRF-TOKEN", csrf(admin, true))
                .contentType("application/json").content("{\"balance\":20000.00,\"accountType\":\"CURRENT\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isBadRequest());
        verify(accounts, never()).update(anyLong(), any());
    }
}

