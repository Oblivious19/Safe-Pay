package com.ofss.security;

import java.security.Principal;
import java.util.List;
import java.util.Map;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ofss.services.SecurityIncidentAuditService;

@Component
public class StompSecurityChannelInterceptor
        implements ChannelInterceptor {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            StompSecurityChannelInterceptor.class);

    private static final String AUTHORIZATION = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final Map<String, String> DESTINATION_AUTHORITIES = Map.of(
            "/user/queue/notifications", "CUSTOMER",
            "/user/queue/transactions", "CUSTOMER",
            "/user/queue/risk-reviews", "RISK_OFFICER");

    private final JwtDecoder jwtDecoder;
    private final SafePayJwtAuthenticationConverter jwtConverter;
    private final SecurityIncidentAuditService incidentAuditService;

    public StompSecurityChannelInterceptor(
            JwtDecoder jwtDecoder,
            SafePayJwtAuthenticationConverter jwtConverter,
            SecurityIncidentAuditService incidentAuditService) {
        this.jwtDecoder = jwtDecoder;
        this.jwtConverter = jwtConverter;
        this.incidentAuditService = incidentAuditService;
    }

    @Override
    public Message<?> preSend(
            Message<?> message,
            MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
                message,
                StompHeaderAccessor.class);
        if (accessor == null) {
            throw new AccessDeniedException(
                    "A valid STOMP frame is required");
        }
        StompCommand command = accessor.getCommand();
        if (command == null) {
            return message;
        }

        if (command == StompCommand.CONNECT) {
            accessor.setUser(authenticate(accessor));
            return message;
        }

        Authentication authentication = authentication(accessor.getUser());
        if (command == StompCommand.SUBSCRIBE) {
            authorizeSubscription(
                    authentication,
                    accessor.getDestination());
        } else if (command == StompCommand.SEND) {
            auditDenied(authentication, "WEBSOCKET_SEND_DENIED");
            throw new AccessDeniedException(
                    "SafePay WebSocket is advisory and does not accept client commands");
        }
        return message;
    }

    private Authentication authenticate(StompHeaderAccessor accessor) {
        List<String> values = accessor.getNativeHeader(AUTHORIZATION);
        if (values == null || values.size() != 1) {
            throw invalidToken();
        }

        String value = values.getFirst();
        if (value == null
                || !value.startsWith(BEARER_PREFIX)
                || value.length() == BEARER_PREFIX.length()) {
            throw invalidToken();
        }

        try {
            Jwt jwt = jwtDecoder.decode(
                    value.substring(BEARER_PREFIX.length()).trim());
            Authentication authentication = jwtConverter.convert(jwt);
            if (authentication == null || !authentication.isAuthenticated()) {
                throw invalidToken();
            }
            return authentication;
        } catch (RuntimeException exception) {
            throw invalidToken();
        }
    }

    private static Authentication authentication(Principal principal) {
        if (!(principal instanceof Authentication authentication)
                || !authentication.isAuthenticated()) {
            throw invalidToken();
        }
        return authentication;
    }

    private void authorizeSubscription(
            Authentication authentication,
            String destination) {
        String requiredAuthority = DESTINATION_AUTHORITIES.get(destination);
        if (requiredAuthority == null) {
            auditDenied(
                    authentication,
                    "WEBSOCKET_DESTINATION_DENIED");
            throw new AccessDeniedException(
                    "WebSocket destination is not permitted");
        }

        boolean authorized = authentication.getAuthorities().stream()
                .anyMatch(authority -> requiredAuthority.equals(
                        authority.getAuthority()));
        if (!authorized) {
            auditDenied(
                    authentication,
                    "WEBSOCKET_AUTHORITY_DENIED");
            throw new AccessDeniedException(
                    "WebSocket subscription is not permitted");
        }
    }

    private void auditDenied(
            Authentication authentication,
            String reasonCode) {
        try {
            incidentAuditService.recordForbidden(
                    authentication,
                    reasonCode,
                    null);
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "Unable to persist forbidden WebSocket audit evidence",
                    exception);
        }
    }

    private static BadCredentialsException invalidToken() {
        return new BadCredentialsException(
                "A valid access token is required for WebSocket access");
    }
}
