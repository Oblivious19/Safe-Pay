package com.ofss.common;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import com.ofss.security.BrowserSecurityProperties;
import com.ofss.security.StompSecurityChannelInterceptor;

@Configuration(proxyBeanMethods = false)
@EnableWebSocketMessageBroker
public class WebSocketConfig
        implements WebSocketMessageBrokerConfigurer {

    public static final String ENDPOINT = "/ws";
    public static final String USER_PREFIX = "/user";
    public static final String APPLICATION_PREFIX = "/app";

    private final BrowserSecurityProperties browserProperties;
    private final StompSecurityChannelInterceptor securityInterceptor;

    public WebSocketConfig(
            BrowserSecurityProperties browserProperties,
            StompSecurityChannelInterceptor securityInterceptor) {
        this.browserProperties = browserProperties;
        this.securityInterceptor = securityInterceptor;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/queue");
        registry.setUserDestinationPrefix(USER_PREFIX);
        registry.setApplicationDestinationPrefixes(APPLICATION_PREFIX);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint(ENDPOINT)
                .setAllowedOrigins(
                        browserProperties.allowedOrigins()
                                .toArray(String[]::new));
    }

    @Override
    public void configureClientInboundChannel(
            ChannelRegistration registration) {
        registration.interceptors(securityInterceptor);
    }
}
