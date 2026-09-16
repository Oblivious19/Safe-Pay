package com.ofss.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

/** Logout only; the existing login and customer-resource chains stay unchanged. */
@Configuration
public class LogoutSecurityConfig {
    @Bean
    SecurityFilterChain logoutSecurity(HttpSecurity http, HttpSessionSecurityContextRepository repository)
            throws Exception {
        return http.securityMatcher("/api/auth/logout")
                .securityContext(context -> context.securityContextRepository(repository).requireExplicitSave(true))
                .cors(cors -> {})
                .csrf(csrf -> csrf
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        .requireCsrfProtectionMatcher(request -> {
                            if (!"POST".equals(request.getMethod())) return false;
                            var session = request.getSession(false);
                            if (session == null) return false;
                            Object stored = session.getAttribute(
                                    HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
                            return stored instanceof SecurityContext context
                                    && context.getAuthentication() != null
                                    && context.getAuthentication().isAuthenticated();
                        }))
                .logout(logout -> logout
                        .logoutRequestMatcher(request -> "POST".equals(request.getMethod()))
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessHandler((request, response, authentication) -> {
                            response.setStatus(200);
                            response.setContentType("application/json");
                            response.setCharacterEncoding("UTF-8");
                            response.getWriter().write("{\"message\":\"Logged out successfully\"}");
                        }))
                .authorizeHttpRequests(requests -> requests.anyRequest().denyAll())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .requestCache(cache -> cache.disable())
                .build();
    }
}
