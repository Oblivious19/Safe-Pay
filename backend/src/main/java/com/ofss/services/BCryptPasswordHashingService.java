package com.ofss.services;

import java.nio.charset.StandardCharsets;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class BCryptPasswordHashingService implements PasswordHashingService {

    private static final int BCRYPT_MAX_INPUT_BYTES = 72;

    private final PasswordEncoder passwordEncoder;

    public BCryptPasswordHashingService(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public String hash(CharSequence rawPassword) {
        String validatedPassword = validateRawPassword(rawPassword);
        return passwordEncoder.encode(validatedPassword);
    }

    @Override
    public boolean matches(
            CharSequence rawPassword,
            String storedPasswordHash) {

        String validatedPassword = validateRawPassword(rawPassword);

        if (storedPasswordHash == null || storedPasswordHash.isBlank()) {
            return false;
        }

        return passwordEncoder.matches(
                validatedPassword,
                storedPasswordHash);
    }

    private String validateRawPassword(CharSequence rawPassword) {
        if (rawPassword == null || rawPassword.toString().isBlank()) {
            throw new IllegalArgumentException(
                    "rawPassword is required");
        }

        String password = rawPassword.toString();
        int inputBytes = password.getBytes(StandardCharsets.UTF_8).length;

        if (inputBytes > BCRYPT_MAX_INPUT_BYTES) {
            throw new IllegalArgumentException(
                    "rawPassword must not exceed 72 UTF-8 bytes");
        }

        return password;
    }
}