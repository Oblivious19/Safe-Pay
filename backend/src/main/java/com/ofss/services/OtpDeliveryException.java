package com.ofss.services;

public class OtpDeliveryException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public OtpDeliveryException(String message) {
        super(message);
    }

    public OtpDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
