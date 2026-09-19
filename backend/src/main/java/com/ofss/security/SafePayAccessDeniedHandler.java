package com.ofss.security;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;

import com.ofss.common.CorrelationIdFilter;
import com.ofss.services.SecurityIncidentAuditService;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class SafePayAccessDeniedHandler
        implements AccessDeniedHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            SafePayAccessDeniedHandler.class);

    private final SecurityProblemWriter problemWriter;
    private final SecurityIncidentAuditService incidentAuditService;

    @Autowired
    public SafePayAccessDeniedHandler(
            SecurityProblemWriter problemWriter,
            SecurityIncidentAuditService incidentAuditService) {
        this.problemWriter = problemWriter;
        this.incidentAuditService = incidentAuditService;
    }

    public SafePayAccessDeniedHandler(
            SecurityProblemWriter problemWriter) {
        this(problemWriter, null);
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException)
            throws IOException, ServletException {
        auditDeniedRequest(request);
        problemWriter.write(
                request,
                response,
                HttpStatus.FORBIDDEN,
                "Access denied",
                "The authenticated user is not authorized for this operation.",
                "ACCESS_DENIED");
    }

    private void auditDeniedRequest(HttpServletRequest request) {
        if (incidentAuditService == null) {
            return;
        }
        Object correlation = request.getAttribute(
                CorrelationIdFilter.REQUEST_ATTRIBUTE);
        try {
            incidentAuditService.recordForbidden(
                    SecurityContextHolder.getContext().getAuthentication(),
                    "HTTP_ACCESS_DENIED",
                    correlation instanceof String value ? value : null);
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "Unable to persist forbidden-access audit evidence",
                    exception);
        }
    }
}
