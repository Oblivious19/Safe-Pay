package com.ofss.dto.otp;

import java.time.OffsetDateTime;
import java.util.Objects;

import com.ofss.beans.OtpChallenge;
import com.ofss.beans.OtpChallengeStatus;
import com.ofss.beans.TransactionDb;
import com.ofss.beans.TransactionState;

public record OtpVerificationResponse(
        String challengeId,
        String transactionId,
        OtpChallengeStatus challengeStatus,
        TransactionState transactionState,
        boolean verified,
        int remainingAttempts,
        OffsetDateTime verifiedAt,
        OffsetDateTime serverTime) {

    public static OtpVerificationResponse from(
            OtpChallenge challenge,
            TransactionDb transaction,
            boolean verified,
            OffsetDateTime serverTime) {

        Objects.requireNonNull(challenge, "challenge is required");
        Objects.requireNonNull(transaction, "transaction is required");

        if (challenge.getOtpChallengeId() == null
                || challenge.getOtpChallengeId() <= 0L
                || transaction.getTransactionId() == null
                || transaction.getTransactionId() <= 0L) {
            throw new IllegalArgumentException(
                    "challenge and transaction must already be persisted");
        }

        int remainingAttempts = Math.max(
                0,
                challenge.getMaxAttempts()
                        - challenge.getAttemptCount());

        return new OtpVerificationResponse(
                challenge.getOtpChallengeId().toString(),
                transaction.getTransactionId().toString(),
                challenge.getStatus(),
                transaction.getState(),
                verified,
                remainingAttempts,
                challenge.getVerifiedAt(),
                Objects.requireNonNull(
                        serverTime,
                        "serverTime is required"));
    }
}
