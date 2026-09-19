package com.ofss.services;

public class SettlementInvariantException extends RuntimeException {

    private static final long serialVersionUID = 1L;
    private final String errorCode;

    public SettlementInvariantException(String errorCode, String message) {
        super(message);
        if (errorCode == null || errorCode.isBlank()) {
            throw new IllegalArgumentException("errorCode is required");
        }
        this.errorCode = errorCode.trim().toUpperCase();
    }

    public String getErrorCode() {
        return errorCode;
    }
}
