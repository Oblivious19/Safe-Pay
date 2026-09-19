package com.ofss.excp;

public class OtpVerificationFailureException
        extends BusinessRuleException {

    private static final long serialVersionUID = 1L;

    private final int remainingAttempts;

    public OtpVerificationFailureException(
            String errorCode,
            String message,
            int remainingAttempts) {

        super(errorCode, message);

        if (remainingAttempts < 0) {
            throw new IllegalArgumentException(
                    "remainingAttempts cannot be negative");
        }

        this.remainingAttempts = remainingAttempts;
    }

    public int getRemainingAttempts() {
        return remainingAttempts;
    }
}
