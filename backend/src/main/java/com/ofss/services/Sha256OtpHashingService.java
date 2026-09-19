package com.ofss.services;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

import org.springframework.stereotype.Service;

@Service
public class Sha256OtpHashingService
        implements OtpHashingService {

    static final String FORMAT_PREFIX = "SHA-256";
    static final int SALT_LENGTH_BYTES = 16;

    private final SecureRandom secureRandom;

    public Sha256OtpHashingService(SecureRandom secureRandom) {
        this.secureRandom = Objects.requireNonNull(
                secureRandom,
                "secureRandom is required");
    }

    @Override
    public String hash(OtpCode otpCode) {
        Objects.requireNonNull(otpCode, "otpCode is required");

        byte[] salt = new byte[SALT_LENGTH_BYTES];
        secureRandom.nextBytes(salt);

        byte[] digest = digest(salt, otpCode.value());

        return FORMAT_PREFIX
                + "$"
                + Base64.getEncoder().encodeToString(salt)
                + "$"
                + Base64.getEncoder().encodeToString(digest);
    }

    @Override
    public boolean matches(
            OtpCode candidate,
            String encodedHash) {

        Objects.requireNonNull(candidate, "candidate is required");

        ParsedHash parsedHash = parse(encodedHash);

        if (parsedHash == null) {
            return false;
        }

        byte[] candidateDigest = digest(
                parsedHash.salt(),
                candidate.value());

        return MessageDigest.isEqual(
                candidateDigest,
                parsedHash.digest());
    }

    private static byte[] digest(byte[] salt, String otpValue) {
        MessageDigest messageDigest = sha256();
        messageDigest.update(salt);
        return messageDigest.digest(
                otpValue.getBytes(StandardCharsets.US_ASCII));
    }

    private static ParsedHash parse(String encodedHash) {
        if (encodedHash == null || encodedHash.isBlank()) {
            return null;
        }

        String[] parts = encodedHash.split("\\$", -1);

        if (parts.length != 3
                || !FORMAT_PREFIX.equals(parts[0])) {
            return null;
        }

        try {
            byte[] salt = Base64.getDecoder().decode(parts[1]);
            byte[] digest = Base64.getDecoder().decode(parts[2]);

            if (salt.length != SALT_LENGTH_BYTES
                    || digest.length != 32) {
                return null;
            }

            return new ParsedHash(salt, digest);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance(FORMAT_PREFIX);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable",
                    exception);
        }
    }

    private record ParsedHash(byte[] salt, byte[] digest) {
    }
}
