package com.ofss.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.ofss.beans.*;
import com.ofss.beans.AdminReportDtos.*;
import com.ofss.config.AdminSecurityConfig;
import com.ofss.config.LoginSecurityConfig;
import com.ofss.repository.AdminReportRepository;
import com.ofss.services.AdminReportService;

@WebMvcTest(AdminReportController.class)
@Import({AdminSecurityConfig.class, LoginSecurityConfig.class, AdminReportService.class})
class AdminReportTest extends WebSecuritySliceSupport {
    @Autowired MockMvc mvc;
    @MockitoBean AdminReportRepository reports;
    private static final String ROOT = "/api/admin/reports/transactions";
    private Summary sample() {
        return new Summary(20, 8, 3, 2, 1, 4, 9, new BigDecimal("100000.03"), new BigDecimal("40000.01"));
    }
    private MockHttpSession session(String role) {
        var session = new MockHttpSession();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new LoginPrincipal(200L, "Caller", "caller@example.com", role, UserStatus.ACTIVE), null, List.of()));
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return session;
    }

    @Test
    void adminGetsAllSevenCountsAndSafeDecimalDto() throws Exception {
        when(reports.summary()).thenReturn(sample());
        mvc.perform(get(ROOT + "/summary").session(session("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalTransactions").value(20))
                .andExpect(jsonPath("$.settledTransactions").value(8))
                .andExpect(jsonPath("$.protectedTransactions").value(3))
                .andExpect(jsonPath("$.cancelledTransactions").value(2))
                .andExpect(jsonPath("$.rejectedTransactions").value(1))
                .andExpect(jsonPath("$.hardHolds").value(4))
                .andExpect(jsonPath("$.highRiskTransactions").value(9))
                .andExpect(jsonPath("$.totalAmount").value(100000.03))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.user").doesNotExist());
        verify(reports).summary();
    }

    @Test
    void adminDailyRangeReturnsDtosAndUsesDates() throws Exception {
        var from = LocalDate.of(2026, 9, 1); var to = LocalDate.of(2026, 9, 13);
        when(reports.daily(from, to)).thenReturn(List.of(new Daily(from, sample())));
        mvc.perform(get(ROOT + "/daily").session(session("ADMIN")).param("from", from.toString()).param("to", to.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].date").value("2026-09-01"))
                .andExpect(jsonPath("$[0].summary.totalTransactions").value(20));
        verify(reports).daily(from, to);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/summary", "/daily?from=2026-09-01&to=2026-09-13"})
    void customerCannotSpoofAdmin(String path) throws Exception {
        mvc.perform(get(ROOT + path).session(session("CUSTOMER")).param("role", "ADMIN").param("userId", "200"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(reports);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/summary", "/daily?from=2026-09-01&to=2026-09-13"})
    void anonymousGets401(String path) throws Exception {
        mvc.perform(get(ROOT + path)).andExpect(status().isUnauthorized());
        verifyNoInteractions(reports);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "?from=2026-09-01", "?from=bad&to=2026-09-13",
            "?from=2026-09-14&to=2026-09-13", "?from=2025-01-01&to=2026-09-13"})
    void invalidDatesReturn400BeforeQuery(String query) throws Exception {
        mvc.perform(get(ROOT + "/daily" + query).session(session("ADMIN")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").exists());
        verifyNoInteractions(reports);
    }

    @Test
    void missingViewsFailSafelyRatherThanReturnFakeZeroCounts() throws Exception {
        when(reports.summary()).thenThrow(new DataAccessResourceFailureException("ORA-00942 internal SQL"));
        mvc.perform(get(ROOT + "/summary").session(session("ADMIN")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().json("{\"message\":\"Reporting is temporarily unavailable\"}"));
    }

    @Test
    void emptyReportsAreValid() throws Exception {
        when(reports.summary()).thenReturn(new Summary(0,0,0,0,0,0,0,BigDecimal.ZERO,BigDecimal.ZERO));
        mvc.perform(get(ROOT + "/summary").session(session("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalTransactions").value(0));
        mvc.perform(get(ROOT + "/daily?from=2026-09-13&to=2026-09-13").session(session("ADMIN")))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test
    void reportingHasNoWriteEndpoint() throws Exception {
        var admin = session("ADMIN");
        when(reports.summary()).thenReturn(sample());
        String token = mvc.perform(get(ROOT + "/summary").session(admin)).andReturn().getResponse().getHeader("X-CSRF-TOKEN");
        clearInvocations(reports);
        mvc.perform(post(ROOT + "/summary").session(admin).header("X-CSRF-TOKEN", token))
                .andExpect(status().isForbidden());
        verifyNoInteractions(reports);
    }
}

