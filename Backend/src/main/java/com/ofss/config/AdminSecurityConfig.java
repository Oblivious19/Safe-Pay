package com.ofss.config;

import java.io.IOException;
import com.ofss.beans.LoginPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.filter.OncePerRequestFilter;

/** Isolated admin routes; customer and login chains are unchanged. */
@Configuration
public class AdminSecurityConfig {
    @Bean
    SecurityFilterChain adminResources(HttpSecurity http, HttpSessionSecurityContextRepository repository, com.ofss.services.CurrentSessionService currentSessions)
            throws Exception {
        AuthorizationManager<RequestAuthorizationContext> admin = (auth, request) ->
                new AuthorizationDecision(auth.get().isAuthenticated()
                        && auth.get().getPrincipal() instanceof LoginPrincipal caller
                        && "ADMIN".equals(caller.role()));
        return http.securityMatcher("/api/admin", "/api/admin/**")
                .securityContext(context -> context.securityContextRepository(repository).requireExplicitSave(true))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.GET, "/api/admin/transactions/hard-holds").access(admin)
                        .requestMatchers(HttpMethod.POST, "/api/admin/transactions/*/approve", "/api/admin/transactions/*/decline").access(admin)
                        .requestMatchers(HttpMethod.GET, "/api/admin/users", "/api/admin/users/*", "/api/admin/users/*/accounts", "/api/admin/accounts").access(admin)
                        .requestMatchers(HttpMethod.POST, "/api/admin/users", "/api/admin/accounts/*/interest-credits").access(admin)
                        .requestMatchers(HttpMethod.PUT, "/api/admin/accounts/*").access(admin)
                        .requestMatchers(HttpMethod.GET, "/api/admin/reports/transactions/summary",
                                "/api/admin/reports/transactions/daily").access(admin)
                        .requestMatchers(HttpMethod.PATCH, "/api/admin/users/*/status", "/api/admin/accounts/*/status").access(admin)
                        .anyRequest().denyAll())
                .cors(cors -> {})
                .csrf(csrf -> csrf.csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .addFilterBefore(new CurrentSessionFilter(currentSessions), CsrfFilter.class)
                .addFilterBefore(new OncePerRequestFilter() {
                    @Override
                    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                            FilterChain chain) throws ServletException, IOException {
                        var authentication = SecurityContextHolder.getContext().getAuthentication();
                        if (authentication == null || !authentication.isAuthenticated()
                                || !(authentication.getPrincipal() instanceof LoginPrincipal)) {
                            response.setStatus(401);
                            response.setContentType("application/json");
                            response.getWriter().write("{\"message\":\"Login is required\"}");
                            return;
                        }
                        chain.doFilter(request, response);
                    }
                }, CsrfFilter.class)
                .addFilterAfter(new OncePerRequestFilter() {
                    @Override
                    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                            FilterChain chain) throws ServletException, IOException {
                        if ("GET".equals(request.getMethod())) {
                            CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
                            if (token != null) response.setHeader("X-CSRF-TOKEN", token.getToken());
                        }
                        chain.doFilter(request, response);
                    }
                }, CsrfFilter.class)
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> {
                            response.setStatus(401);
                            response.setContentType("application/json");
                            response.getWriter().write("{\"message\":\"Login is required\"}");
                        })
                        .accessDeniedHandler((request, response, exception) -> {
                            response.setStatus(403);
                            response.setContentType("application/json");
                            response.getWriter().write("{\"message\":\"Access denied or invalid CSRF token\"}");
                        }))
                .formLogin(form -> form.disable()).httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable()).requestCache(cache -> cache.disable())
                .build();
    }
}

