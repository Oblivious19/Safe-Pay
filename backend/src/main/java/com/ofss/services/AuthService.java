package com.ofss.services;

import com.ofss.dto.auth.LoginRequest;

public interface AuthService {

    AuthenticationResult login(
            LoginRequest request,
            String correlationId);

    AuthenticationResult refresh(
            String refreshToken,
            String correlationId);

    void logout(
            Long userId,
            String refreshToken,
            String correlationId);
}
