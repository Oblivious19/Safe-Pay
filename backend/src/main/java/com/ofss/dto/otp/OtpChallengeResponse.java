package com.ofss.dto.otp;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Objects;

import com.ofss.beans.OtpChallenge;
import com.ofss.beans.OtpChallengeStatus;

public record OtpChallengeResponse(
        String challengeId,
        String transactionId,
        OtpChallengeStatus status,
        String maskedDestination,
        OffsetDateTime expiresAt,
        OffsetDateTime resendAvailableAt,
        int remainingIssues,
        OffsetDateTime serverTime) {

    public static OtpChallengeResponse from(
            OtpChallenge challenge,
            String maskedDestination,
            Duration resendCooldown,
            int remainingIssues,
            OffsetDateTime serverTime) {

        Objects.requireNonNull(challenge, "challenge is required");

        if (challenge.getOtpChallengeId() == null
                || challenge.getOtpChallengeId() <= 0L) {
            throw new IllegalArgumentException(
                    "challenge must already be persisted");
        }

        Long transactionId = Objects.requireNonNull(
                        challenge.getTransaction(),
                        "challenge transaction is required")
                .getTransactionId();

        if (transactionId == null || transactionId <= 0L) {
            throw new IllegalArgumentException(
                    "challenge transaction must already be persisted");
        }

        if (maskedDestination == null
                || maskedDestination.isBlank()) {
            throw new IllegalArgumentException(
                    "maskedDestination is required");
        }

        if (remainingIssues < 0) {
            throw new IllegalArgumentException(
                    "remainingIssues cannot be negative");
        }

        Duration cooldown = Objects.requireNonNull(
                resendCooldown,
                "resendCooldown is required");

        return new OtpChallengeResponse(
                challenge.getOtpChallengeId().toString(),
                transactionId.toString(),
                challenge.getStatus(),
                maskedDestination,
                challenge.getExpiresAt(),
                challenge.getCreatedAt().plus(cooldown),
                remainingIssues,
                Objects.requireNonNull(
                        serverTime,
                        "serverTime is required"));
    }
}
