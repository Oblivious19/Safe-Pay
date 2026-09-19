package com.ofss.services;

public record IdempotencyExecutionResult<T>(
        int httpStatus,
        T responseBody,
        Long transactionId,
        boolean replayed) {

    public IdempotencyExecutionResult {
        if (httpStatus < 100 || httpStatus > 599) {
            throw new IllegalArgumentException(
                    "httpStatus must be between 100 and 599");
        }

        if (transactionId != null && transactionId <= 0L) {
            throw new IllegalArgumentException(
                    "transactionId must be positive when supplied");
        }
    }

    public static <T> IdempotencyExecutionResult<T> executed(
            int httpStatus,
            T responseBody,
            Long transactionId) {

        return new IdempotencyExecutionResult<>(
                httpStatus,
                responseBody,
                transactionId,
                false);
    }

    public static <T> IdempotencyExecutionResult<T> replayed(
            int httpStatus,
            T responseBody,
            Long transactionId) {

        return new IdempotencyExecutionResult<>(
                httpStatus,
                responseBody,
                transactionId,
                true);
    }
}
