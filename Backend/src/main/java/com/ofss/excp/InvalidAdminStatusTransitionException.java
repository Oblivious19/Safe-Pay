package com.ofss.excp;

public class InvalidAdminStatusTransitionException extends RuntimeException {
    public InvalidAdminStatusTransitionException(String message) { super(message); }
}
