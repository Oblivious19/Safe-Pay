package com.ofss.security;

import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ofss.common.CorrelationIdFilter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class SecurityProblemWriter {

    private final ObjectMapper objectMapper;
    private final Clock clock;

    public SecurityProblemWriter(
            ObjectMapper objectMapper,
            Clock clock) {
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public void write(
            HttpServletRequest request,
            HttpServletResponse response,
            HttpStatus status,
            String title,
            String detail,
            String errorCode) throws IOException {

        String correlationId = resolveCorrelationId(request);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                status,
                detail);
        problem.setTitle(title);
        problem.setType(URI.create(
                "urn:safepay:problem:"
                        + errorCode.toLowerCase(Locale.ROOT)));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("errorCode", errorCode);
        problem.setProperty("traceId", correlationId);
        problem.setProperty(
                "timestamp",
                OffsetDateTime.now(clock).toString());

        response.setStatus(status.value());
        response.setContentType(
                MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setHeader(
                CorrelationIdFilter.HEADER_NAME,
                correlationId);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }

    private static String resolveCorrelationId(
            HttpServletRequest request) {
        Object attribute = request.getAttribute(
                CorrelationIdFilter.REQUEST_ATTRIBUTE);
        if (attribute instanceof String value
                && !value.isBlank()) {
            return value;
        }
        return UUID.randomUUID().toString();
    }
}
