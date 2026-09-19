package com.ofss.security;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class RefreshCookieOriginFilter extends OncePerRequestFilter {

    private static final Set<String> PROTECTED_PATHS = Set.of(
            "/api/v1/auth/refresh",
            "/api/v1/auth/logout");

    private final BrowserSecurityProperties properties;
    private final SecurityProblemWriter problemWriter;

    public RefreshCookieOriginFilter(
            BrowserSecurityProperties properties,
            SecurityProblemWriter problemWriter) {
        this.properties = properties;
        this.problemWriter = problemWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod())
                || !PROTECTED_PATHS.contains(pathWithinApplication(request));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        List<String> origins = Collections.list(
                request.getHeaders(HttpHeaders.ORIGIN));

        if (origins.size() != 1
                || !properties.permits(origins.getFirst())) {
            problemWriter.write(
                    request,
                    response,
                    HttpStatus.FORBIDDEN,
                    "Request origin rejected",
                    "Cookie-based authentication operations require an approved browser origin.",
                    "INVALID_REQUEST_ORIGIN");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private static String pathWithinApplication(
            HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        return contextPath.isEmpty()
                ? requestUri
                : requestUri.substring(contextPath.length());
    }
}
