package com.safepay.assistant;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
@Configuration
public class SecurityConfiguration {
    @Bean SecurityFilterChain assistantSecurity(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(a->a.anyRequest().permitAll())
            // Authentication is delegated to SafePay; every private controller operation checks that session.
            .csrf(c->c.csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
            .headers(h->h.contentSecurityPolicy(c->c.policyDirectives("default-src 'self'; script-src 'self'; style-src 'self'; connect-src 'self'; img-src 'self' data:; frame-ancestors 'none'; base-uri 'none'; form-action 'self'")))
            .exceptionHandling(e->e.accessDeniedHandler((request,response,error)->{
                response.setStatus(403);response.setContentType("application/json");
                response.getWriter().write("{\"message\":\"Refresh the page to obtain a valid security token\"}");
            }))
            .formLogin(f->f.disable()).httpBasic(b->b.disable()).logout(l->l.disable()).requestCache(c->c.disable()).build();
    }
}
