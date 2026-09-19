package com.ofss.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;

class RefreshCookieOriginFilterTest {

    private RefreshCookieOriginFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RefreshCookieOriginFilter(
                new BrowserSecurityProperties(List.of(
                        "http://localhost:8000")),
                new SecurityProblemWriter(
                        new ObjectMapper(),
                        Clock.fixed(
                                Instant.parse("2026-09-17T10:00:00Z"),
                                ZoneOffset.UTC)));
    }

    @Test
    void acceptsRefreshFromTheConfiguredOrigin() throws Exception {
        MockHttpServletRequest request = request("/api/v1/auth/refresh");
        request.addHeader(HttpHeaders.ORIGIN, "http://localhost:8000");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isSameAs(request);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void rejectsMissingOriginOnRefresh() throws Exception {
        MockHttpServletResponse response = execute(request(
                "/api/v1/auth/refresh"));
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString())
                .contains("INVALID_REQUEST_ORIGIN");
    }

    @Test
    void rejectsUnapprovedOriginOnLogout() throws Exception {
        MockHttpServletRequest request = request("/api/v1/auth/logout");
        request.addHeader(HttpHeaders.ORIGIN, "http://evil.example");
        MockHttpServletResponse response = execute(request);
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void doesNotApplyCookieOriginPolicyToLogin() throws Exception {
        MockHttpServletRequest request = request("/api/v1/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        assertThat(chain.getRequest()).isSameAs(request);
    }

    private MockHttpServletResponse execute(MockHttpServletRequest request)
            throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    private static MockHttpServletRequest request(String path) {
        return new MockHttpServletRequest("POST", path);
    }
}
