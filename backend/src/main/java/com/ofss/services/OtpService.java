package com.ofss.services;

public interface OtpService {

    OtpChallengeResult issue(
            Long customerUserId,
            Long transactionId);

    OtpChallengeResult issue(
            Long customerUserId,
            Long transactionId,
            OperationContext context);

    OtpChallengeResult resend(
            Long customerUserId,
            Long transactionId);

    OtpChallengeResult resend(
            Long customerUserId,
            Long transactionId,
            OperationContext context);

    OtpVerificationResult verify(
            Long customerUserId,
            Long transactionId,
            Long challengeId,
            String otp);

    OtpVerificationResult verify(
            Long customerUserId,
            Long transactionId,
            Long challengeId,
            String otp,
            OperationContext context);
}
