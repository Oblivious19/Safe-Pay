package com.ofss.services;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Objects;

import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

public final class EmailOtpDeliveryGateway
        implements OtpDeliveryGateway {

    private static final String GMAIL_HOST = "smtp.gmail.com";
    private static final int GMAIL_PORT = 587;
    private static final String FIVE_SECONDS = "5000";

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final OtpEmailProperties otpEmailProperties;

    public EmailOtpDeliveryGateway(
            JavaMailSender mailSender,
            MailProperties mailProperties,
            OtpEmailProperties otpEmailProperties) {

        this.mailSender = Objects.requireNonNull(
                mailSender,
                "mailSender is required");

        validateMailConfiguration(
                Objects.requireNonNull(
                        mailProperties,
                        "mailProperties is required"));

        this.otpEmailProperties = Objects.requireNonNull(
                otpEmailProperties,
                "otpEmailProperties is required");
        this.otpEmailProperties.validateEnabledConfiguration();
        this.fromAddress = this.otpEmailProperties
                .requireFromAddress();
    }

    @Override
    public void deliver(
            String recipientEmail,
            OtpCode otpCode,
            OffsetDateTime expiresAt) {

        String recipient = otpEmailProperties.resolveRecipient(
                recipientEmail);
        OtpCode protectedCode = Objects.requireNonNull(
                otpCode,
                "otpCode is required");
        OffsetDateTime expiry = Objects.requireNonNull(
                expiresAt,
                "expiresAt is required");

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(recipient);
        message.setSubject("SafePay payment verification code");
        message.setText(
                "Your SafePay verification code is "
                        + protectedCode.value()
                        + ". It expires at "
                        + expiry
                        + ". Do not share this code."
        );

        try {
            mailSender.send(message);
        } catch (MailException deliveryFailure) {
            throw new OtpDeliveryException(
                    "OTP email could not be delivered",
                    deliveryFailure);
        }
    }

    private static void validateMailConfiguration(
            MailProperties properties) {

        Map<String, String> smtp = properties.getProperties();

        boolean valid = GMAIL_HOST.equals(properties.getHost())
                && Integer.valueOf(GMAIL_PORT).equals(
                        properties.getPort())
                && hasText(properties.getUsername())
                && hasText(properties.getPassword())
                && "true".equalsIgnoreCase(
                        smtp.get("mail.smtp.auth"))
                && "true".equalsIgnoreCase(
                        smtp.get("mail.smtp.starttls.enable"))
                && "true".equalsIgnoreCase(
                        smtp.get("mail.smtp.starttls.required"))
                && FIVE_SECONDS.equals(
                        smtp.get("mail.smtp.connectiontimeout"))
                && FIVE_SECONDS.equals(
                        smtp.get("mail.smtp.timeout"))
                && FIVE_SECONDS.equals(
                        smtp.get("mail.smtp.writetimeout"));

        if (!valid) {
            throw new IllegalStateException(
                    "OTP email configuration is incomplete or unsafe");
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
