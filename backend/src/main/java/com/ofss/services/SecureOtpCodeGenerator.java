package com.ofss.services;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.Objects;

import org.springframework.stereotype.Service;

@Service
public class SecureOtpCodeGenerator implements OtpCodeGenerator {

    private final SecureRandom secureRandom;
    private final OtpPolicyProperties policy;
    private final int upperBound;

    public SecureOtpCodeGenerator(
            SecureRandom secureRandom,
            OtpPolicyProperties policy) {

        this.secureRandom = Objects.requireNonNull(
                secureRandom,
                "secureRandom is required");
        this.policy = Objects.requireNonNull(
                policy,
                "policy is required");
        this.upperBound = powerOfTen(policy.codeLength());
    }

    @Override
    public OtpCode generate() {
        int value = secureRandom.nextInt(upperBound);

        return new OtpCode(
                String.format(
                        Locale.ROOT,
                        "%0" + policy.codeLength() + "d",
                        value),
                policy.codeLength());
    }

    private static int powerOfTen(int exponent) {
        int result = 1;

        for (int index = 0; index < exponent; index++) {
            result = Math.multiplyExact(result, 10);
        }

        return result;
    }
}
