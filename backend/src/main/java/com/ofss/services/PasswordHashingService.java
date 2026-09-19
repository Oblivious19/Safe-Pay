package com.ofss.services;

public interface PasswordHashingService {

    String hash(CharSequence rawPassword);

    boolean matches(CharSequence rawPassword, String storedPasswordHash);
}