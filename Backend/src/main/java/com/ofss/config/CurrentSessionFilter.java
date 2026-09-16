package com.ofss.config;

import java.io.IOException;
import com.ofss.beans.LoginPrincipal;
import com.ofss.services.CurrentSessionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Revokes access on the next protected request after a database status or role change. */
final class CurrentSessionFilter extends OncePerRequestFilter {
    private final CurrentSessionService sessions;
    CurrentSessionFilter(CurrentSessionService sessions) { this.sessions = sessions; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof LoginPrincipal caller) {
            boolean current;
            try { current = sessions.isCurrent(caller); }
            catch (DataAccessException unavailable) {
                response.setStatus(503);
                response.setContentType("application/json");
                response.getWriter().write("{\"message\":\"Unable to verify session access; please try again\"}");
                return;
            }
            if (!current) {
                SecurityContextHolder.clearContext();
                var session = request.getSession(false);
                if (session != null) session.invalidate();
                response.setStatus(401);
                response.setContentType("application/json");
                response.getWriter().write("{\"message\":\"Your account access has changed; please sign in again\"}");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
