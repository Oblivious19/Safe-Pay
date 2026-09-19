package com.ofss.controller;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.ofss.beans.UserStatus;
import com.ofss.excp.GlobalExceptionHandler;
import com.ofss.security.SafePayPrincipal;
import com.ofss.services.AdminOperationsService;
import com.ofss.dto.admin.OperationalFailureResponse;

class AdminOperationsControllerTest {
    private final AdminOperationsService service = mock(AdminOperationsService.class);
    private MockMvc mvc;
    private Authentication auth;
    @BeforeEach void setup() {
        var principal = new SafePayPrincipal(10L, "admin@example.invalid", "test-hash", UserStatus.ACTIVE,
                0L, List.of(new SimpleGrantedAuthority("SYSTEM_ADMIN")));
        auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        mvc = MockMvcBuilders.standaloneSetup(new AdminOperationsController(service))
                .setControllerAdvice(new GlobalExceptionHandler(Clock.systemUTC())).build();
    }
    @Test void statsUsesPrincipalRatherThanSuppliedActorId() throws Exception {
        mvc.perform(get("/api/v1/admin/operations/stats").principal(auth).param("administratorId", "999"))
                .andExpect(status().isOk());
        verify(service).getStatistics(10L);
    }
    @Test void failureDefaultsAndSourceFilterAreBound() throws Exception {
        mvc.perform(get("/api/v1/admin/operations/failures").principal(auth)).andExpect(status().isOk());
        verify(service).searchFailures(10L, null, null, null, null, 0, 20);
        mvc.perform(get("/api/v1/admin/operations/failures").principal(auth)
                .param("source", "NOTIFICATION").param("transactionId", "55").param("size", "5")).andExpect(status().isOk());
        verify(service).searchFailures(10L, OperationalFailureResponse.Source.NOTIFICATION, 55L, null, null, 0, 5);
    }
    @Test void invalidSourceAndDateFailBeforeServiceAndNoHealthRouteExists() throws Exception {
        mvc.perform(get("/api/v1/admin/operations/failures").principal(auth).param("source", "ALL")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/admin/operations/failures").principal(auth).param("from", "today")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/admin/operations/health").principal(auth)).andExpect(status().isNotFound());
        verifyNoInteractions(service);
    }
}
