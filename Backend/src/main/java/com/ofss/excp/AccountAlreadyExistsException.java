package com.ofss.excp;

/** Thrown when a user attempts to create a second SafePay account. */
public class AccountAlreadyExistsException extends RuntimeException {

    public AccountAlreadyExistsException(String message) {
        super(message);
    }
}
