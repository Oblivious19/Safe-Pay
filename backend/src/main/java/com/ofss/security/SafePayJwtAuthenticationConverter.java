package com.ofss.security;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class SafePayJwtAuthenticationConverter
        implements Converter<Jwt, AbstractAuthenticationToken> {

    private static final String INVALID_TOKEN =
            "Invalid access token";

    private final SafePayUserDetailsService userDetailsService;

    public SafePayJwtAuthenticationConverter(
            SafePayUserDetailsService userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Long userId = parseUserId(jwt.getClaimAsString("user_id"));
        Number securityVersion = jwt.getClaim("security_version");
        List<String> tokenAuthorities =
                jwt.getClaimAsStringList("authorities");

        if (securityVersion == null || tokenAuthorities == null) {
            throw invalidToken();
        }

        SafePayPrincipal principal =
                userDetailsService.loadByUserId(userId);

        if (!principal.isEnabled()
                || !principal.isAccountNonLocked()
                || principal.getSecurityVersion()
                        != securityVersion.longValue()
                || !principal.getUsername().equals(jwt.getSubject())) {
            throw invalidToken();
        }

        Set<String> currentAuthorities = new HashSet<>();
        principal.getAuthorities().forEach(authority ->
                currentAuthorities.add(authority.getAuthority()));

        if (!currentAuthorities.equals(
                new HashSet<>(tokenAuthorities))) {
            throw invalidToken();
        }

        return new UsernamePasswordAuthenticationToken(
                principal,
                null,
                principal.getAuthorities());
    }

    private static Long parseUserId(String value) {
        try {
            long userId = Long.parseLong(value);
            if (userId <= 0) {
                throw invalidToken();
            }
            return userId;
        } catch (RuntimeException exception) {
            throw invalidToken();
        }
    }

    private static BadCredentialsException invalidToken() {
        return new BadCredentialsException(INVALID_TOKEN);
    }
}
