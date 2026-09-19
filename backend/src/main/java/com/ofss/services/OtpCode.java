package com.ofss.services;

import java.util.Objects;

public final class OtpCode {

    private final String value;

    OtpCode(String value, int requiredLength) {
        this.value = Objects.requireNonNull(
                value,
                "OTP value is required");

        if (value.length() != requiredLength
                || !value.chars().allMatch(character ->
                        character >= '0' && character <= '9')) {
            throw new IllegalArgumentException(
                    "OTP value must contain exactly "
                            + requiredLength
                            + " numeric digits");
        }
    }

    public String value() {
        return value;
    }

    @Override
    public String toString() {
        return "OtpCode[PROTECTED]";
    }
}
