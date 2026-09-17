package com.ofss.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.List;
import com.ofss.beans.*;
import com.ofss.config.*;
import com.ofss.services.AdminApprovalService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminApprovalController.class)
@Import({AdminSecurityConfig.class, LoginSecurityConfig.class})
class AdminApprovalAuthorizationTest extends WebSecuritySliceSupport {
    @Autowired MockMvc mvc;
    @MockitoBean AdminApprovalService service;
    LoginPrincipal principal(String role) {
        return new LoginPrincipal(200L,"Caller","caller@example.test",role,UserStatus.ACTIVE);
    }
    MockHttpSession session(String role) {
        var session = new MockHttpSession();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal(role),null,List.of()));
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return session;
    }
    String token(MockHttpSession session) throws Exception {
        return mvc.perform(get("/api/admin/transactions/hard-holds").session(session))
                .andReturn().getResponse().getHeader("X-CSRF-TOKEN");
    }
    @Test void customerAndAnonymousCannotReadOrApprove() throws Exception {
        mvc.perform(get("/api/admin/transactions/hard-holds")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/transactions/21/approve")).andExpect(status().isUnauthorized());
        var customer = session("CUSTOMER");
        String token = token(customer);
        mvc.perform(get("/api/admin/transactions/hard-holds").session(customer)).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/transactions/21/approve").session(customer)
                .header("X-CSRF-TOKEN",token).header("Idempotency-Key","k")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void adminRequiresCsrfAndKeyAndUsesOnlySessionActor() throws Exception {
        var admin = session("ADMIN"); String token = token(admin); clearInvocations(service);
        mvc.perform(post("/api/admin/transactions/21/approve").session(admin).header("Idempotency-Key","k"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/transactions/21/approve").session(admin).header("X-CSRF-TOKEN",token))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
        mvc.perform(post("/api/admin/transactions/21/approve").session(admin).header("X-CSRF-TOKEN",token)
                .header("Idempotency-Key","k").param("actorId","999")).andExpect(status().isOk());
        verify(service).approve(21L,principal("ADMIN"),"k");
    }
    @Test void changedOrSuspendedAdminSessionCannotApprove() throws Exception {
        var admin = session("ADMIN"); String token = token(admin); clearInvocations(service);
        when(currentSessions.isCurrent(any())).thenReturn(false);
        mvc.perform(post("/api/admin/transactions/21/approve").session(admin).header("X-CSRF-TOKEN",token)
                .header("Idempotency-Key","k")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
    @Test void customerAndAnonymousCannotReadOrDecline() throws Exception {
        mvc.perform(get("/api/admin/transactions/hard-holds")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/transactions/21/decline")).andExpect(status().isUnauthorized());
        var customer = session("CUSTOMER");
        String token = token(customer);
        mvc.perform(get("/api/admin/transactions/hard-holds").session(customer)).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/transactions/21/decline").session(customer)
                .header("X-CSRF-TOKEN",token).header("Idempotency-Key","k")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void declineRequiresCsrfAndKeyAndUsesOnlySessionActor() throws Exception {
        var admin = session("ADMIN"); String token = token(admin); clearInvocations(service);
        mvc.perform(post("/api/admin/transactions/21/decline").session(admin).header("Idempotency-Key","k"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/transactions/21/decline").session(admin).header("X-CSRF-TOKEN",token))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
        mvc.perform(post("/api/admin/transactions/21/decline").session(admin).header("X-CSRF-TOKEN",token)
                .header("Idempotency-Key","k").param("actorId","999")).andExpect(status().isOk());
        verify(service).decline(21L,principal("ADMIN"),"k");
    }
    @Test void changedOrSuspendedAdminSessionCannotDecline() throws Exception {
        var admin = session("ADMIN"); String token = token(admin); clearInvocations(service);
        when(currentSessions.isCurrent(any())).thenReturn(false);
        mvc.perform(post("/api/admin/transactions/21/decline").session(admin).header("X-CSRF-TOKEN",token)
                .header("Idempotency-Key","k")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

}
