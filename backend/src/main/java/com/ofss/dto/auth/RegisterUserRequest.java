package com.ofss.dto.auth;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterUserRequest(

        @NotBlank(message = "fullName is required")
        @Size(
                min = 2,
                max = 120,
                message = "fullName must contain between 2 and 120 characters")
        String fullName,

        @Email(message = "email must be valid")
        @Size(
                max = 254,
                message = "email must not exceed 254 characters")
        String email,

        @Pattern(
                regexp = "^\\+[1-9][0-9]{7,14}$",
                message = "mobileNumber must use E.164 format")
        String mobileNumber,

        @NotBlank(message = "password is required")
        @Size(
                min = 12,
                max = 72,
                message = "password must contain between 12 and 72 characters")
        String password) {

    private static final int BCRYPT_MAX_INPUT_BYTES = 72;

    public RegisterUserRequest {
        fullName = normalizeRequiredText(fullName);
        email = normalizeEmail(email);
        mobileNumber = normalizeOptionalText(mobileNumber);
    }

    @JsonIgnore
    @AssertTrue(message = "either email or mobileNumber is required")
    public boolean isContactProvided() {
        return email != null || mobileNumber != null;
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

    private static String normalizeRequiredText(String value) {
        return value == null ? null : value.trim();
    }

    private static String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }

        String normalizedValue = value.trim();
        return normalizedValue.isEmpty() ? null : normalizedValue;
    }

    private static String normalizeEmail(String value) {
        String normalizedValue = normalizeOptionalText(value);

        return normalizedValue == null
                ? null
                : normalizedValue.toLowerCase(Locale.ROOT);
    }
}