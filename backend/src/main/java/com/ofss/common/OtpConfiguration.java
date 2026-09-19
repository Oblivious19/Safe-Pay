package com.ofss.common;

import java.security.SecureRandom;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ofss.services.OtpPolicyProperties;
import com.ofss.services.OtpEmailProperties;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
        OtpPolicyProperties.class,
        OtpEmailProperties.class
})
public class OtpConfiguration {

    @Bean
    public SecureRandom otpSecureRandom() {
        return new SecureRandom();
    }
}
