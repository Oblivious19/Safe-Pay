package com.ofss.services;

import java.util.Objects;

import com.ofss.dto.otp.OtpVerificationResponse;

public record OtpVerificationResult(
        OtpVerificationResponse response,
        String errorCode,
        String errorMessage) {

    public OtpVerificationResult {
        Objects.requireNonNull(response, "response is required");

        boolean successful = errorCode == null
                && errorMessage == null;
        boolean failed = errorCode != null
                && !errorCode.isBlank()
                && errorMessage != null
                && !errorMessage.isBlank();

        if (!successful && !failed) {
            throw new IllegalArgumentException(
                    "errorCode and errorMessage must both be supplied or omitted");
        }
    }

    public static OtpVerificationResult verified(
            OtpVerificationResponse response) {

        return new OtpVerificationResult(response, null, null);
    }

    public static OtpVerificationResult rejected(
            OtpVerificationResponse response,
            String errorCode,
            String errorMessage) {

        return new OtpVerificationResult(
                response,
                errorCode,
                errorMessage);
    }

    public boolean verified() {
        return errorCode == null;
    }
}
