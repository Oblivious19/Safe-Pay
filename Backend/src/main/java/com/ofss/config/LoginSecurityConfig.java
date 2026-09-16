package com.ofss.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;

@Configuration
public class LoginSecurityConfig {
    @Bean
    public HttpSessionSecurityContextRepository loginContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public ChangeSessionIdAuthenticationStrategy loginSessionStrategy() {
        return new ChangeSessionIdAuthenticationStrategy();
    }

    // Prevent Boot from creating a generated-password fallback user. Only the
    // explicit database-backed LoginService authenticates in this phase.
    @Bean
    public AuthenticationManager authenticationManager() {
        return authentication -> { throw new BadCredentialsException("Unsupported authentication method"); };
    }

    @Bean
    public SecurityFilterChain loginSecurity(HttpSecurity http,
            HttpSessionSecurityContextRepository repository) throws Exception {
        return http.securityMatcher("/api/auth/login")
                .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
                .securityContext(context -> context.securityContextRepository(repository).requireExplicitSave(true))
                .cors(cors -> {})
                // This URL accepts application/json only; CORS rejects untrusted
                // browser origins. Never extend this exemption to session-based
                // account/transaction endpoints when those are secured later.
                .csrf(csrf -> csrf.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .requestCache(cache -> cache.disable())
                .build();
    }
}
