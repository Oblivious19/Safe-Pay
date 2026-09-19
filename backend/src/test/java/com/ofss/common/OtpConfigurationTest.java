package com.ofss.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.ofss.services.OtpEmailProperties;
import com.ofss.services.OtpEmailRoutingMode;
import com.ofss.services.OtpPolicyProperties;

class OtpConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(OtpConfiguration.class)
                    .withPropertyValues(
                            "safepay.otp.validity=PT5M",
                            "safepay.otp.max-attempts=3",
                            "safepay.otp.resend-cooldown=PT30S",
                            "safepay.otp.max-issues-per-cycle=3",
                            "safepay.otp.code-length=6",
                            "safepay.otp.email.enabled=false",
                            "safepay.otp.email.routing-mode=FIXED_OVERRIDE");

    @Test
    void bindsApprovedPolicyAndCreatesSecureRandom() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(
                    OtpPolicyProperties.class);
            assertThat(context).hasSingleBean(SecureRandom.class);

            OtpPolicyProperties properties = context.getBean(
                    OtpPolicyProperties.class);

            assertThat(properties.validity())
                    .isEqualTo(Duration.ofMinutes(5));
            assertThat(properties.maxIssuesPerCycle())
                    .isEqualTo(3);
        });
    }

    @Test
    void bindsFixedRecipientOverrideWithoutExposingItToClients() {
        contextRunner
                .withPropertyValues(
                        "safepay.otp.email.enabled=true",
                        "safepay.otp.email.from=safepay-dev@gmail.com",
                        "safepay.otp.email.routing-mode=FIXED_OVERRIDE",
                        "safepay.otp.email.recipient-override=otp-receiver@gmail.com")
                .run(context -> {
                    assertThat(context).hasSingleBean(
                            OtpEmailProperties.class);

                    OtpEmailProperties properties = context.getBean(
                            OtpEmailProperties.class);

                    assertThat(properties.routingMode())
                            .isEqualTo(
                                    OtpEmailRoutingMode.FIXED_OVERRIDE);
                    assertThat(properties.resolveRecipient(
                            "customer@example.com"))
                            .isEqualTo("otp-receiver@gmail.com");
                });
    }

    @Test
    void rejectsConfigurationThatDriftsFromDecisionRegister() {
        contextRunner
                .withPropertyValues("safepay.otp.code-length=8")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(
                                    IllegalArgumentException.class)
                            .rootCause()
                            .hasMessage(
                                    "codeLength must be 6 for SafePay V1");
                });
    }
}
