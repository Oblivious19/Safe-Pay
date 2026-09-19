package com.ofss.dto.otp;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record VerifyOtpRequest(
        @NotNull(message = "challengeId is required")
        @Positive(message = "challengeId must be positive")
        Long challengeId,

        @NotNull(message = "otp is required")
        @Pattern(
                regexp = "[0-9]{6}",
                message = "otp must contain exactly 6 numeric digits")
        String otp) {
}
