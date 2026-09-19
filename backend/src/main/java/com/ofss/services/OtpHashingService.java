package com.ofss.services;

public interface OtpHashingService {

    String hash(OtpCode otpCode);

    boolean matches(OtpCode candidate, String encodedHash);
}
