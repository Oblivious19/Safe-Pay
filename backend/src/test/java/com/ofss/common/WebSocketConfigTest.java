package com.ofss.common;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.config.SimpleBrokerRegistration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.StompWebSocketEndpointRegistration;

import com.ofss.security.BrowserSecurityProperties;
import com.ofss.security.StompSecurityChannelInterceptor;

@ExtendWith(MockitoExtension.class)
class WebSocketConfigTest {

    @Mock private MessageBrokerRegistry messageBrokerRegistry;
    @Mock private SimpleBrokerRegistration simpleBrokerRegistration;
    @Mock private StompEndpointRegistry endpointRegistry;
    @Mock private StompWebSocketEndpointRegistration endpointRegistration;
    @Mock private ChannelRegistration channelRegistration;
    @Mock private StompSecurityChannelInterceptor securityInterceptor;

    private WebSocketConfig config;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        config = new WebSocketConfig(
                new BrowserSecurityProperties(
                        java.util.List.of("http://localhost:8000")),
                securityInterceptor);
    }

    @Test
    void configuresOnlyPrivateQueueBrokerAndUserPrefix() {
        when(messageBrokerRegistry.enableSimpleBroker("/queue"))
                .thenReturn(simpleBrokerRegistration);

        config.configureMessageBroker(messageBrokerRegistry);

        verify(messageBrokerRegistry).enableSimpleBroker("/queue");
        verify(messageBrokerRegistry).setUserDestinationPrefix("/user");
        verify(messageBrokerRegistry)
                .setApplicationDestinationPrefixes("/app");
    }

    @Test
    void exposesSingleExplicitOriginStompEndpointWithoutSockJs() {
        when(endpointRegistry.addEndpoint("/ws"))
                .thenReturn(endpointRegistration);

        config.registerStompEndpoints(endpointRegistry);

        verify(endpointRegistry).addEndpoint("/ws");
        verify(endpointRegistration).setAllowedOrigins(
                "http://localhost:8000");
    }

    @Test
    void installsSecurityInterceptorOnInboundFrames() {
        config.configureClientInboundChannel(channelRegistration);
        verify(channelRegistration).interceptors(securityInterceptor);
    }
}
