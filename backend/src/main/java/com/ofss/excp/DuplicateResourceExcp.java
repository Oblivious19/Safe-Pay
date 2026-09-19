package com.ofss.excp;

import java.util.Objects;

public class DuplicateResourceExcp extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String errorCode;

    public DuplicateResourceExcp(
            String errorCode,
            String message) {

        this(errorCode, message, null);
    }

    public DuplicateResourceExcp(
            String errorCode,
            String message,
            Throwable cause) {

        super(
                Objects.requireNonNull(
                        message,
                        "message is required"),
                cause);

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