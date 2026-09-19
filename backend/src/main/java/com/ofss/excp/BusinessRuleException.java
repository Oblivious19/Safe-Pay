package com.ofss.excp;

import java.util.Objects;

public class BusinessRuleException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String errorCode;

    public BusinessRuleException(String errorCode, String message) {
        super(Objects.requireNonNull(message, "message is required"));

        if (errorCode == null || errorCode.isBlank()) {
            throw new IllegalArgumentException(
                    "errorCode is required");
        }

        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}