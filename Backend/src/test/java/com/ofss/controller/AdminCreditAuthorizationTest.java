package com.ofss.controller;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import com.ofss.beans.*;
import com.ofss.beans.AdminCreditDtos.*;
import com.ofss.config.*;
import com.ofss.services.AdminCreditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminCreditController.class)
@Import({AdminSecurityConfig.class, LoginSecurityConfig.class})
class AdminCreditAuthorizationTest extends WebSecuritySliceSupport {
    @Autowired MockMvc mvc;
    @MockitoBean AdminCreditService service;
    MockHttpSession admin;
    @BeforeEach void setup() { admin = session("ADMIN"); }
    MockHttpSession session(String role) {
        var session = new MockHttpSession();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new LoginPrincipal(200L,"Caller","caller@example.test",role,UserStatus.ACTIVE),null,List.of()));
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,context);
        return session;
    }
    String csrf(MockHttpSession caller) throws Exception {
        return mvc.perform(get("/api/admin/users/103/accounts").session(caller)).andReturn().getResponse().getHeader("X-CSRF-TOKEN");
    }
    @Test void creditUsesActorFromSessionAndRequiredKeyWithExactDecimalReceipt() throws Exception {
        when(service.credit(77L,200L,new BigDecimal("10.01"),"credit-key")).thenReturn(new Receipt(
                77L,"10.01","9999999999999980.00","9999999999999990.01",LocalDateTime.now(),"Simulated bank interest"));
        mvc.perform(post("/api/admin/accounts/77/interest-credits").session(admin).header("X-CSRF-TOKEN",csrf(admin))
                .header("Idempotency-Key","credit-key").param("actorId","999").contentType("application/json").content("{\"amount\":\"10.01\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.balanceAfter").value("9999999999999990.01"));
        verify(service).credit(77L,200L,new BigDecimal("10.01"),"credit-key");
    }
    @Test void detailsListContainsEveryOwnedAccountAndNoCredentials() throws Exception {
        when(service.accounts(103L)).thenReturn(List.of(new AccountView(1L,"500000000001","SAVINGS","ACTIVE","5000.00"),
                new AccountView(2L,"500000000002","CURRENT","BLOCKED","10000.00")));
        mvc.perform(get("/api/admin/users/103/accounts").session(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2)).andExpect(jsonPath("$[1].accountId").value(2))
                .andExpect(jsonPath("$[0].user").doesNotExist());
    }
    @Test void csrfAndIdempotencyHeaderAreRequired() throws Exception {
        mvc.perform(post("/api/admin/accounts/77/interest-credits").session(admin).header("Idempotency-Key","k")
                .contentType("application/json").content("{\"amount\":1}" )).andExpect(status().isForbidden());
        String csrf = csrf(admin); clearInvocations(service);
        mvc.perform(post("/api/admin/accounts/77/interest-credits").session(admin).header("X-CSRF-TOKEN",csrf)
                .contentType("application/json").content("{\"amount\":1}" )).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @ParameterizedTest @ValueSource(strings={"{}","{\"amount\":0}","{\"amount\":-1}","{\"amount\":1.001}",
            "{\"amount\":10000000000000000}","{\"amount\":1,\"actorId\":999}","{\"amount\":1,\"balance\":100}"})
    void invalidOrExtraFieldsDoNotCredit(String body) throws Exception {
        String token=csrf(admin); clearInvocations(service);
        mvc.perform(post("/api/admin/accounts/77/interest-credits").session(admin).header("X-CSRF-TOKEN",token)
                .header("Idempotency-Key","k").contentType("application/json").content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void anonymousAndCustomerCannotAccessAdminCreditsOrAccountDetails() throws Exception {
        mvc.perform(get("/api/admin/users/103/accounts")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/accounts/77/interest-credits").contentType("application/json").content("{\"amount\":1}"))
                .andExpect(status().isUnauthorized());
        var customer=session("CUSTOMER");
        mvc.perform(get("/api/admin/users/103/accounts").session(customer)).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/accounts/77/interest-credits").session(customer).header("X-CSRF-TOKEN",csrf(customer))
                .header("Idempotency-Key","k").contentType("application/json").content("{\"amount\":1}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
}