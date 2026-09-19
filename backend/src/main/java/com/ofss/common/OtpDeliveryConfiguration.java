package com.ofss.common;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

import com.ofss.services.DisabledOtpDeliveryGateway;
import com.ofss.services.EmailOtpDeliveryGateway;
import com.ofss.services.OtpDeliveryGateway;
import com.ofss.services.OtpEmailProperties;

@Configuration(proxyBeanMethods = false)
public class OtpDeliveryConfiguration {

    @Bean
    @ConditionalOnProperty(
            name = "safepay.otp.email.enabled",
            havingValue = "true")
    public OtpDeliveryGateway emailOtpDeliveryGateway(
            JavaMailSender mailSender,
            MailProperties mailProperties,
            OtpEmailProperties otpEmailProperties) {

        return new EmailOtpDeliveryGateway(
                mailSender,
                mailProperties,
                otpEmailProperties);
    }

    @Bean
    @ConditionalOnProperty(
            name = "safepay.otp.email.enabled",
            havingValue = "false",
            matchIfMissing = true)
    public OtpDeliveryGateway disabledOtpDeliveryGateway() {
        return new DisabledOtpDeliveryGateway();
    }
}
