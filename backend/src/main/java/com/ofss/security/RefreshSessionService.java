package com.ofss.security;

public interface RefreshSessionService {

    RefreshSessionResult issueForLogin(
            Long userId,
            String correlationId);

    RefreshRotationResult rotate(
            String rawRefreshToken,
            String correlationId);

    void logout(
            Long userId,
            String rawRefreshToken,
            String correlationId);
}
