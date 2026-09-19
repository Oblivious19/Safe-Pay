package com.ofss.common;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import com.ofss.security.AuthenticationPolicyProperties;
import com.ofss.security.BrowserSecurityProperties;
import com.ofss.security.JwtSecurityProperties;
import com.ofss.security.RefreshCookieProperties;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
        JwtSecurityProperties.class,
        AuthenticationPolicyProperties.class,
        RefreshCookieProperties.class,
        BrowserSecurityProperties.class
})
public class SecurityBeansConfiguration {
}
