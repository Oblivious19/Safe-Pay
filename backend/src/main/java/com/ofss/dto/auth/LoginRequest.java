package com.ofss.dto.auth;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(

        @NotBlank(message = "loginIdentifier is required")
        @Size(
                max = 254,
                message = "loginIdentifier must not exceed 254 characters")
        String loginIdentifier,

        @NotBlank(message = "password is required")
        @Size(
                max = 72,
                message = "password must not exceed 72 characters")
        String password) {

    private static final int BCRYPT_MAX_INPUT_BYTES = 72;

    public LoginRequest {
        loginIdentifier =
                normalizeLoginIdentifier(loginIdentifier);
    }

    @JsonIgnore
    @AssertTrue(message = "password must not exceed 72 UTF-8 bytes")
    public boolean isPasswordWithinBcryptLimit() {
        if (password == null) {
            return true;
        }

        return password.getBytes(StandardCharsets.UTF_8).length
                <= BCRYPT_MAX_INPUT_BYTES;
    }

    private static String normalizeLoginIdentifier(String value) {
        if (value == null) {
            return null;
        }

        String normalizedValue = value.trim();

        if (normalizedValue.contains("@")) {
            return normalizedValue.toLowerCase(Locale.ROOT);
        }

        return normalizedValue;
    }
}