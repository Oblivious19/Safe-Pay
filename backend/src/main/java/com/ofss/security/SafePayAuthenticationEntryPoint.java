package com.ofss.security;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class SafePayAuthenticationEntryPoint
        implements AuthenticationEntryPoint {

    private final SecurityProblemWriter problemWriter;

    public SafePayAuthenticationEntryPoint(
            SecurityProblemWriter problemWriter) {
        this.problemWriter = problemWriter;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException)
            throws IOException, ServletException {
        problemWriter.write(
                request,
                response,
                HttpStatus.UNAUTHORIZED,
                "Authentication required",
                "A valid SafePay access token is required.",
                "AUTHENTICATION_REQUIRED");
    }
}
