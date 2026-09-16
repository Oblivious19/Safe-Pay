package com.ofss.excp;

public class TransactionValidationException extends RuntimeException {
    private final int status;

    public TransactionValidationException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() { return status; }
}
