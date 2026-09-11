package com.ofss.excp;

public class IdempotencyConflictException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public IdempotencyConflictException(String message) {
        super(message);
    }
}
