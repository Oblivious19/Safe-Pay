package com.ofss.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ofss.beans.RoleName;
import com.ofss.beans.UserStatus;
import com.ofss.common.CorrelationIdFilter;
import com.ofss.dto.admin.AdminUserSecurityResponse;
import com.ofss.excp.GlobalExceptionHandler;
import com.ofss.security.SafePayPrincipal;
import com.ofss.services.AdminUserSecurityService;

@ExtendWith(MockitoExtension.class)
class AdminUserSecurityControllerTest {

    private static final String CORRELATION = "admin-controller-test";

    @Mock private AdminUserSecurityService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(
                Instant.parse("2026-09-17T11:00:00Z"),
                ZoneOffset.UTC);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new AdminUserSecurityController(service))
                .setControllerAdvice(new GlobalExceptionHandler(clock))
                .addFilters(new CorrelationIdFilter())
                .build();
    }

    @Test
    void directoryBindsCanonicalFiltersAndPrincipal() throws Exception {
        var response = new com.ofss.common.api.PagedResponse<>(List.of(response(UserStatus.ACTIVE)),
                0, 20, 1, 1, true, true);
        when(service.searchUsers(10L, "customer", RoleName.CUSTOMER, UserStatus.ACTIVE, 0, 20))
                .thenReturn(response);
        mockMvc.perform(authenticated(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/admin/users")).param("q", "customer").param("role", "CUSTOMER")
                        .param("status", "ACTIVE").param("administratorId", "999"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].userId").value("20"))
                .andExpect(jsonPath("$.items[0].passwordHash").doesNotExist());
        verify(service).searchUsers(10L, "customer", RoleName.CUSTOMER, UserStatus.ACTIVE, 0, 20);
    }

    @Test
    void detailUsesPathIdAndRejectsInvalidRoleFilter() throws Exception {
        when(service.getUserDetails(10L, 20L)).thenReturn(response(UserStatus.ACTIVE));
        mockMvc.perform(authenticated(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/admin/users/20")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.userId").value("20"));
        mockMvc.perform(authenticated(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/admin/users")).param("role", "ADMIN"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updatesOnlyPathSelectedUsersStatus() throws Exception {
        when(service.updateStatus(
                10L, 20L, UserStatus.LOCKED, CORRELATION))
                .thenReturn(response(UserStatus.LOCKED));

        mockMvc.perform(authenticated(patch(
                        "/api/v1/admin/users/20/status"))
                        .header(CorrelationIdFilter.HEADER_NAME, CORRELATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"LOCKED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("20"))
                .andExpect(jsonPath("$.status").value("LOCKED"));
    }

    @Test
    void assignsCanonicalRoleFromPath() throws Exception {
        when(service.assignRole(
                10L, 20L, RoleName.AUDITOR, CORRELATION))
                .thenReturn(response(UserStatus.ACTIVE));
        mockMvc.perform(authenticated(put(
                        "/api/v1/admin/users/20/roles/AUDITOR"))
                        .header(CorrelationIdFilter.HEADER_NAME, CORRELATION))
                .andExpect(status().isOk());
        verify(service).assignRole(
                10L, 20L, RoleName.AUDITOR, CORRELATION);
    }

    @Test
    void removesCanonicalRoleFromPath() throws Exception {
        when(service.removeRole(
                10L, 20L, RoleName.AUDITOR, CORRELATION))
                .thenReturn(response(UserStatus.ACTIVE));
        mockMvc.perform(authenticated(delete(
                        "/api/v1/admin/users/20/roles/AUDITOR"))
                        .header(CorrelationIdFilter.HEADER_NAME, CORRELATION))
                .andExpect(status().isOk());
    }

    @Test
    void revokesSessionsForPathSelectedUser() throws Exception {
        when(service.revokeSessions(10L, 20L, CORRELATION))
                .thenReturn(response(UserStatus.ACTIVE));
        mockMvc.perform(authenticated(post(
                        "/api/v1/admin/users/20/sessions/revoke"))
                        .header(CorrelationIdFilter.HEADER_NAME, CORRELATION))
                .andExpect(status().isOk());
        verify(service).revokeSessions(10L, 20L, CORRELATION);
    }

    private static MockHttpServletRequestBuilder authenticated(
            MockHttpServletRequestBuilder request) {
        SafePayPrincipal principal = new SafePayPrincipal(
                10L,
                "admin@safepay.test",
                "hash",
                UserStatus.ACTIVE,
                0L,
                Set.of(new SimpleGrantedAuthority("SYSTEM_ADMIN")));
        return request.principal(
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        principal.getAuthorities()));
    }

    private static AdminUserSecurityResponse response(UserStatus status) {
        return new AdminUserSecurityResponse(
                "20",
                "Target User",
                "target@safepay.test",
                null,
                status.name(),
                List.of(RoleName.CUSTOMER),
                1L,
                OffsetDateTime.parse("2026-09-17T11:00:00Z"));
    }
}
