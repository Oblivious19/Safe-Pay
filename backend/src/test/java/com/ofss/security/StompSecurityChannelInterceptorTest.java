package com.ofss.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import com.ofss.beans.UserStatus;
import com.ofss.services.SecurityIncidentAuditService;

@ExtendWith(MockitoExtension.class)
class StompSecurityChannelInterceptorTest {

    @Mock private JwtDecoder jwtDecoder;
    @Mock private SafePayJwtAuthenticationConverter jwtConverter;
    @Mock private SecurityIncidentAuditService incidentAuditService;

    private StompSecurityChannelInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new StompSecurityChannelInterceptor(
                jwtDecoder,
                jwtConverter,
                incidentAuditService);
    }

    @Test
    void authenticatesConnectFromBearerNativeHeader() {
        Jwt jwt = jwt();
        var authentication = authentication("CUSTOMER");
        when(jwtDecoder.decode("access-token")).thenReturn(jwt);
        when(jwtConverter.convert(jwt)).thenReturn(authentication);
        Message<?> message = message(
                StompCommand.CONNECT,
                null,
                null,
                "Bearer access-token");

        interceptor.preSend(message, null);

        assertThat(StompHeaderAccessor.wrap(message).getUser())
                .isEqualTo(authentication);
    }

    @Test
    void rejectsConnectWithoutBearerToken() {
        assertThatThrownBy(() -> interceptor.preSend(
                message(StompCommand.CONNECT, null, null, null),
                null))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void convertsDecoderFailureToGenericCredentialFailure() {
        when(jwtDecoder.decode("bad-token"))
                .thenThrow(new IllegalArgumentException("signature details"));
        assertThatThrownBy(() -> interceptor.preSend(
                message(
                        StompCommand.CONNECT,
                        null,
                        null,
                        "Bearer bad-token"),
                null))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageNotContaining("signature details");
    }

    @Test
    void customerMaySubscribeToPrivateNotificationQueue() {
        Message<?> message = message(
                StompCommand.SUBSCRIBE,
                "/user/queue/notifications",
                authentication("CUSTOMER"),
                null);
        assertThat(interceptor.preSend(message, null)).isSameAs(message);
    }

    @Test
    void riskOfficerMaySubscribeToPrivateReviewQueue() {
        Message<?> message = message(
                StompCommand.SUBSCRIBE,
                "/user/queue/risk-reviews",
                authentication("RISK_OFFICER"),
                null);
        assertThat(interceptor.preSend(message, null)).isSameAs(message);
    }

    @Test
    void customerCannotSubscribeToReviewQueue() {
        assertThatThrownBy(() -> interceptor.preSend(
                message(
                        StompCommand.SUBSCRIBE,
                        "/user/queue/risk-reviews",
                        authentication("CUSTOMER"),
                        null),
                null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void rejectsSharedAndCrossUserSubscriptionShapes() {
        for (String destination : Set.of(
                "/queue/notifications",
                "/topic/transactions",
                "/user/other@example.com/queue/notifications")) {
            assertThatThrownBy(() -> interceptor.preSend(
                    message(
                            StompCommand.SUBSCRIBE,
                            destination,
                            authentication("CUSTOMER"),
                            null),
                    null))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    @Test
    void deniesEveryClientSendFrame() {
        assertThatThrownBy(() -> interceptor.preSend(
                message(
                        StompCommand.SEND,
                        "/app/settle",
                        authentication("CUSTOMER"),
                        null),
                null))
                .isInstanceOf(AccessDeniedException.class);
    }

    private static Message<byte[]> message(
            StompCommand command,
            String destination,
            java.security.Principal principal,
            String authorization) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        if (destination != null) {
            accessor.setDestination(destination);
        }
        if (principal != null) {
            accessor.setUser(principal);
        }
        if (authorization != null) {
            accessor.setNativeHeader("Authorization", authorization);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(
                new byte[0],
                accessor.getMessageHeaders());
    }

    private static UsernamePasswordAuthenticationToken authentication(
            String authority) {
        SafePayPrincipal principal = new SafePayPrincipal(
                101L,
                "customer@example.com",
                "hash",
                UserStatus.ACTIVE,
                0L,
                Set.of(new SimpleGrantedAuthority(authority)));
        return new UsernamePasswordAuthenticationToken(
                principal,
                null,
                principal.getAuthorities());
    }

    private static Jwt jwt() {
        return new Jwt(
                "token",
                Instant.parse("2026-09-17T10:00:00Z"),
                Instant.parse("2026-09-17T10:15:00Z"),
                Map.of("alg", "HS256"),
                Map.of("sub", "customer@example.com"));
    }
}
