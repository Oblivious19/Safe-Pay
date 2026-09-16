package com.ofss.excp;

public class DuplicatePhoneException extends RuntimeException {
    public DuplicatePhoneException() {
        super("Phone number is already registered");
    }
}
