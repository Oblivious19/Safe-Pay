package com.ofss.security;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SafePayJwtAuthenticationConverter jwtConverter,
            SafePayAuthenticationEntryPoint authenticationEntryPoint,
            SafePayAccessDeniedHandler accessDeniedHandler,
            RefreshCookieOriginFilter originFilter,
            CsrfFilter refreshCookieCsrfFilter,
            CorsConfigurationSource corsConfigurationSource)
            throws Exception {

        http
                .cors(cors -> cors.configurationSource(
                        corsConfigurationSource))
                // The default session-oriented CSRF filter is replaced below
                // by an explicitly configured Spring CsrfFilter that protects
                // only refresh-cookie operations in this stateless API.
                .csrf(AbstractHttpConfigurer::disable)
                .addFilterBefore(originFilter, CsrfFilter.class)
                .addFilterAt(refreshCookieCsrfFilter, CsrfFilter.class)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp
                                .policyDirectives(
                                        "default-src 'none'; frame-ancestors 'none'"))
                        .referrerPolicy(referrer -> referrer
                                .policy(ReferrerPolicy.NO_REFERRER))
                        .permissionsPolicyHeader(permissions -> permissions
                                .policy(
                                        "camera=(), microphone=(), geolocation=(), payment=()"))
                        .frameOptions(frame -> frame.deny()))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/**")
                        .permitAll()
                        .requestMatchers(
                                "/api/v1/auth/register",
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/csrf",
                                "/actuator/health",
                                "/ws/**")
                        .permitAll()
                        .requestMatchers(
                                "/api/v1/admin/accounts",
                                "/api/v1/admin/accounts/**",
                                "/api/v1/admin/users",
                                "/api/v1/admin/users/**",
                                "/api/v1/admin/operations",
                                "/api/v1/admin/operations/**")
                        .hasAuthority("SYSTEM_ADMIN")
                        .requestMatchers(
                                "/api/v1/admin/risk-reviews/**")
                        .hasAuthority("RISK_OFFICER")
                        .requestMatchers("/api/v1/audit-logs/**")
                        .hasAuthority("AUDITOR")
                        .requestMatchers("/api/v1/audit", "/api/v1/audit/**")
                        .hasAuthority("AUDITOR")
                        .requestMatchers(
                                "/api/v1/transactions/*/audit")
                        .hasAnyAuthority(
                                "CUSTOMER",
                                "RISK_OFFICER",
                                "AUDITOR")
                        .requestMatchers(
                                "/api/v1/accounts",
                                "/api/v1/users/me",
                                "/api/v1/accounts/**",
                                "/api/v1/beneficiaries/**",
                                "/api/v1/transactions/**",
                                "/api/v1/notifications/**")
                        .hasAuthority("CUSTOMER")
                        .requestMatchers("/api/v1/auth/logout")
                        .authenticated()
                        .anyRequest()
                        .authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(
                                authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .authenticationEntryPoint(
                                authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                        .jwt(jwt -> jwt
                                .jwtAuthenticationConverter(
                                        jwtConverter)));

        return http.build();
    }

    @Bean
    CookieCsrfTokenRepository csrfTokenRepository(
            RefreshCookieProperties refreshCookieProperties) {
        CookieCsrfTokenRepository repository =
                CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> cookie
                .path("/")
                .secure(refreshCookieProperties.secure())
                .sameSite("Strict"));
        return repository;
    }

    @Bean
    CsrfFilter refreshCookieCsrfFilter(
            CookieCsrfTokenRepository repository,
            SafePayAccessDeniedHandler accessDeniedHandler) {
        CsrfTokenRequestAttributeHandler requestHandler =
                new CsrfTokenRequestAttributeHandler();
        requestHandler.setCsrfRequestAttributeName("_csrf");

        CsrfFilter filter = new CsrfFilter(repository);
        filter.setRequestHandler(requestHandler);
        filter.setAccessDeniedHandler(accessDeniedHandler);
        filter.setRequireCsrfProtectionMatcher(request ->
                "POST".equalsIgnoreCase(request.getMethod())
                        && ("/api/v1/auth/refresh"
                                .equals(request.getServletPath())
                        || "/api/v1/auth/logout"
                                .equals(request.getServletPath())));
        return filter;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            BrowserSecurityProperties browserProperties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(
                browserProperties.allowedOrigins());
        configuration.setAllowedMethods(List.of(
                "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(
                HttpHeaders.AUTHORIZATION,
                HttpHeaders.CONTENT_TYPE,
                HttpHeaders.ORIGIN,
                "X-CSRF-TOKEN",
                "X-XSRF-TOKEN",
                "X-Correlation-ID",
                "Idempotency-Key"));
        configuration.setExposedHeaders(List.of(
                "X-Correlation-ID",
                "Idempotency-Replayed"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
