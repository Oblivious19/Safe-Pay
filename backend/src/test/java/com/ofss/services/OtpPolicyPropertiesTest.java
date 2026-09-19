package com.ofss.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class OtpPolicyPropertiesTest {

    @Test
    void acceptsExactApprovedV1Policy() {
        OtpPolicyProperties properties = approvedPolicy();

        assertThat(properties.validity())
                .isEqualTo(Duration.ofMinutes(5));
        assertThat(properties.maxAttempts()).isEqualTo(3);
        assertThat(properties.resendCooldown())
                .isEqualTo(Duration.ofSeconds(30));
        assertThat(properties.maxIssuesPerCycle()).isEqualTo(3);
        assertThat(properties.codeLength()).isEqualTo(6);
    }

    @Test
    void rejectsDifferentValidity() {
        assertThatThrownBy(() -> new OtpPolicyProperties(
                Duration.ofMinutes(4),
                3,
                Duration.ofSeconds(30),
                3,
                6))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("validity must be PT5M for SafePay V1");
    }

    @Test
    void rejectsDifferentAttemptLimit() {
        assertThatThrownBy(() -> new OtpPolicyProperties(
                Duration.ofMinutes(5),
                4,
                Duration.ofSeconds(30),
                3,
                6))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("maxAttempts must be 3 for SafePay V1");
    }

    @Test
    void rejectsDifferentResendCooldown() {
        assertThatThrownBy(() -> new OtpPolicyProperties(
                Duration.ofMinutes(5),
                3,
                Duration.ofSeconds(60),
                3,
                6))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "resendCooldown must be PT30S for SafePay V1");
    }

    @Test
    void rejectsDifferentIssueLimit() {
        assertThatThrownBy(() -> new OtpPolicyProperties(
                Duration.ofMinutes(5),
                3,
                Duration.ofSeconds(30),
                4,
                6))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "maxIssuesPerCycle must be 3 for SafePay V1");
    }

    @Test
    void rejectsDifferentCodeLength() {
        assertThatThrownBy(() -> new OtpPolicyProperties(
                Duration.ofMinutes(5),
                3,
                Duration.ofSeconds(30),
                3,
                8))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("codeLength must be 6 for SafePay V1");
    }

    public static OtpPolicyProperties approvedPolicy() {
        return new OtpPolicyProperties(
                Duration.ofMinutes(5),
                3,
                Duration.ofSeconds(30),
                3,
                6);
    }
}
