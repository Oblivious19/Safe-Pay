package com.ofss.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SafePayPinSetupRequest(
        @NotBlank(message = "SafePay PIN is required")
        @Pattern(regexp = "\\d{6}", message = "SafePay PIN must be 6 digits")
        String pin,
        @NotBlank(message = "Confirm your SafePay PIN")
        String confirmation) {
}
