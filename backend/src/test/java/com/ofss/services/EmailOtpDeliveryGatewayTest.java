package com.ofss.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class EmailOtpDeliveryGatewayTest {

    private JavaMailSender mailSender;
    private MailProperties mailProperties;

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        mailProperties = approvedMailProperties();
    }

    @Test
    void fixedOverrideRoutesStoredRecipientToApprovedMailbox() {
        EmailOtpDeliveryGateway gateway = gateway();
        OffsetDateTime expiresAt = OffsetDateTime.parse(
                "2026-09-16T12:05:00Z");

        gateway.deliver(
                "customer@example.com",
                new OtpCode("123456", 6),
                expiresAt);

        ArgumentCaptor<SimpleMailMessage> captor =
                ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());

        SimpleMailMessage message = captor.getValue();
        assertThat(message.getFrom())
                .isEqualTo("safepay-dev@gmail.com");
        assertThat(message.getTo())
                .containsExactly("otp-receiver@gmail.com");
        assertThat(message.getSubject())
                .isEqualTo("SafePay payment verification code");
        assertThat(message.getText())
                .contains("123456")
                .contains(expiresAt.toString())
                .contains("Do not share this code");
    }

    @Test
    void storedUserModeUsesServerSuppliedRecipient() {
        EmailOtpDeliveryGateway gateway = new EmailOtpDeliveryGateway(
                mailSender,
                mailProperties,
                new OtpEmailProperties(
                        true,
                        "safepay-dev@gmail.com",
                        OtpEmailRoutingMode.STORED_USER,
                        null));

        gateway.deliver(
                "customer@example.com",
                new OtpCode("123456", 6),
                OffsetDateTime.parse("2026-09-16T12:05:00Z"));

        ArgumentCaptor<SimpleMailMessage> captor =
                ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());

        assertThat(captor.getValue().getTo())
                .containsExactly("customer@example.com");
    }

    @Test
    void sanitizesMailProviderFailure() {
        doThrow(new MailSendException(
                "SMTP rejected customer@example.com using secret"))
                .when(mailSender)
                .send(any(SimpleMailMessage.class));

        assertThatThrownBy(() -> gateway().deliver(
                "customer@example.com",
                new OtpCode("123456", 6),
                OffsetDateTime.parse("2026-09-16T12:05:00Z")))
                .isInstanceOf(OtpDeliveryException.class)
                .hasMessage("OTP email could not be delivered")
                .hasMessageNotContaining("customer@example.com")
                .hasMessageNotContaining("secret");
    }

    @Test
    void rejectsWrongHostOrPortWithoutExposingCredentials() {
        mailProperties.setHost("smtp.example.com");

        assertUnsafeConfiguration();

        mailProperties = approvedMailProperties();
        mailProperties.setPort(465);

        assertUnsafeConfiguration();
    }

    @Test
    void rejectsMissingUsernameOrAppPassword() {
        mailProperties.setUsername(" ");
        assertUnsafeConfiguration();

        mailProperties = approvedMailProperties();
        mailProperties.setPassword("");
        assertUnsafeConfiguration();
    }

    @Test
    void rejectsMissingStartTlsOrAuthentication() {
        mailProperties.getProperties().put(
                "mail.smtp.starttls.required",
                "false");
        assertUnsafeConfiguration();

        mailProperties = approvedMailProperties();
        mailProperties.getProperties().put(
                "mail.smtp.auth",
                "false");
        assertUnsafeConfiguration();
    }

    @Test
    void rejectsTimeoutDrift() {
        mailProperties.getProperties().put(
                "mail.smtp.timeout",
                "10000");

        assertUnsafeConfiguration();
    }

    @Test
    void rejectsMissingOrMalformedFromAddress() {
        assertThatThrownBy(() -> new EmailOtpDeliveryGateway(
                mailSender,
                mailProperties,
                new OtpEmailProperties(
                        true,
                        " ",
                        OtpEmailRoutingMode.FIXED_OVERRIDE,
                        "otp-receiver@gmail.com")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("OTP email from address is not configured");

        assertThatThrownBy(() -> new EmailOtpDeliveryGateway(
                mailSender,
                mailProperties,
                new OtpEmailProperties(
                        true,
                        "not-an-email",
                        OtpEmailRoutingMode.FIXED_OVERRIDE,
                        "otp-receiver@gmail.com")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("OTP email from address is invalid");
    }

    @Test
    void rejectsMissingOrMalformedFixedRecipientOverride() {
        assertThatThrownBy(() -> new EmailOtpDeliveryGateway(
                mailSender,
                mailProperties,
                new OtpEmailProperties(
                        true,
                        "safepay-dev@gmail.com",
                        OtpEmailRoutingMode.FIXED_OVERRIDE,
                        " ")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "OTP email recipient override is not configured");

        assertThatThrownBy(() -> new EmailOtpDeliveryGateway(
                mailSender,
                mailProperties,
                new OtpEmailProperties(
                        true,
                        "safepay-dev@gmail.com",
                        OtpEmailRoutingMode.FIXED_OVERRIDE,
                        "not-an-email")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("OTP email recipient override is invalid");
    }

    @Test
    void rejectsConflictingOverrideInStoredUserMode() {
        assertThatThrownBy(() -> new EmailOtpDeliveryGateway(
                mailSender,
                mailProperties,
                new OtpEmailProperties(
                        true,
                        "safepay-dev@gmail.com",
                        OtpEmailRoutingMode.STORED_USER,
                        "otp-receiver@gmail.com")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "OTP email recipient override must be empty "
                                + "when routing mode is STORED_USER");
    }

    @Test
    void disabledGatewayFailsWithoutInspectingSecretValues() {
        assertThatThrownBy(() -> new DisabledOtpDeliveryGateway()
                .deliver(
                        "customer@example.com",
                        new OtpCode("123456", 6),
                        OffsetDateTime.parse(
                                "2026-09-16T12:05:00Z")))
                .isInstanceOf(OtpDeliveryException.class)
                .hasMessage("OTP email delivery is unavailable")
                .hasMessageNotContaining("123456")
                .hasMessageNotContaining("customer@example.com");
    }

    private EmailOtpDeliveryGateway gateway() {
        return new EmailOtpDeliveryGateway(
                mailSender,
                mailProperties,
                new OtpEmailProperties(
                        true,
                        "safepay-dev@gmail.com",
                        OtpEmailRoutingMode.FIXED_OVERRIDE,
                        "otp-receiver@gmail.com"));
    }

    private void assertUnsafeConfiguration() {
        assertThatThrownBy(this::gateway)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "OTP email configuration is incomplete or unsafe")
                .hasMessageNotContaining("app-password-value")
                .hasMessageNotContaining("safepay-dev@gmail.com");
    }

    private static MailProperties approvedMailProperties() {
        MailProperties properties = new MailProperties();
        properties.setHost("smtp.gmail.com");
        properties.setPort(587);
        properties.setUsername("safepay-dev@gmail.com");
        properties.setPassword("app-password-value");
        properties.getProperties().put("mail.smtp.auth", "true");
        properties.getProperties().put(
                "mail.smtp.starttls.enable",
                "true");
        properties.getProperties().put(
                "mail.smtp.starttls.required",
                "true");
        properties.getProperties().put(
                "mail.smtp.connectiontimeout",
                "5000");
        properties.getProperties().put(
                "mail.smtp.timeout",
                "5000");
        properties.getProperties().put(
                "mail.smtp.writetimeout",
                "5000");
        return properties;
    }
}
