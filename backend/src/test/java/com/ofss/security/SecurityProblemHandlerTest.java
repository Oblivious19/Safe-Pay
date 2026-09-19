package com.ofss.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ofss.common.CorrelationIdFilter;

class SecurityProblemHandlerTest {

    private ObjectMapper objectMapper;
    private SafePayAuthenticationEntryPoint entryPoint;
    private SafePayAccessDeniedHandler deniedHandler;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .addMixIn(
                        ProblemDetail.class,
                        ProblemDetailJacksonMixin.class);
        SecurityProblemWriter writer = new SecurityProblemWriter(
                objectMapper,
                Clock.fixed(
                        Instant.parse("2026-09-17T11:00:00Z"),
                        ZoneOffset.UTC));
        entryPoint = new SafePayAuthenticationEntryPoint(writer);
        deniedHandler = new SafePayAccessDeniedHandler(writer);
    }

    @Test
    void missingAuthenticationReturnsStableProblem401() throws Exception {
        MockHttpServletRequest request = request();
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(
                request,
                response,
                new BadCredentialsException("secret parser detail"));

        JsonNode body = objectMapper.readTree(
                response.getContentAsByteArray());
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(body.path("errorCode").asText())
                .isEqualTo("AUTHENTICATION_REQUIRED");
    }

    @Test
    void forbiddenAuthorityReturnsStableProblem403() throws Exception {
        MockHttpServletRequest request = request();
        MockHttpServletResponse response = new MockHttpServletResponse();

        deniedHandler.handle(
                request,
                response,
                new AccessDeniedException("internal policy detail"));

        JsonNode body = objectMapper.readTree(
                response.getContentAsByteArray());
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(body.path("errorCode").asText())
                .isEqualTo("ACCESS_DENIED");
    }

    @Test
    void securityFailurePreservesTrustedCorrelationId() throws Exception {
        MockHttpServletRequest request = request();
        request.setAttribute(
                CorrelationIdFilter.REQUEST_ATTRIBUTE,
                "security-correlation");
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(
                request,
                response,
                new BadCredentialsException("ignored"));

        assertThat(response.getHeader(
                CorrelationIdFilter.HEADER_NAME))
                .isEqualTo("security-correlation");
    }

    @Test
    void securityProblemNeverLeaksExceptionMessage() throws Exception {
        MockHttpServletRequest request = request();
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(
                request,
                response,
                new BadCredentialsException(
                        "JWT signature was wrong for secret abc"));

        assertThat(response.getContentAsString())
                .doesNotContain("signature", "secret abc");
    }

    private static MockHttpServletRequest request() {
        return MockMvcRequestBuilders.get("/api/v1/notifications")
                .buildRequest(new org.springframework.mock.web.MockServletContext());
    }
}
