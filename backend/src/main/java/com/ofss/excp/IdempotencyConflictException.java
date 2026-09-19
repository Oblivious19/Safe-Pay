package com.ofss.excp;

public class IdempotencyConflictException
        extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String errorCode;

    private IdempotencyConflictException(
            String errorCode,
            String safeMessage) {

        super(safeMessage);
        this.errorCode = errorCode;
    }

    public static IdempotencyConflictException differentRequest() {
        return new IdempotencyConflictException(
                "IDEMPOTENCY_KEY_REUSED",
                "The idempotency key has already been used for a different request.");
    }

    public static IdempotencyConflictException requestInProgress() {
        return new IdempotencyConflictException(
                "IDEMPOTENCY_REQUEST_IN_PROGRESS",
                "An equivalent request is already being processed.");
    }

    public static IdempotencyConflictException expired() {
        return new IdempotencyConflictException(
                "IDEMPOTENCY_KEY_EXPIRED",
                "The idempotency key has expired; submit the request with a new key.");
    }

    public String getErrorCode() {
        return errorCode;
    }
}
