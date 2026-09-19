package com.ofss.services;

import java.util.Objects;

import com.ofss.dto.otp.OtpChallengeResponse;

public record OtpChallengeResult(
        OtpChallengeResponse response,
        String errorCode,
        String errorMessage) {

    public OtpChallengeResult {
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

    public static OtpChallengeResult accepted(
            OtpChallengeResponse response) {

        return new OtpChallengeResult(response, null, null);
    }

    public static OtpChallengeResult rejected(
            OtpChallengeResponse response,
            String errorCode,
            String errorMessage) {

        return new OtpChallengeResult(
                response,
                errorCode,
                errorMessage);
    }

    public boolean accepted() {
        return errorCode == null;
    }
}
