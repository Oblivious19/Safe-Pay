package com.ofss.dto.auth;

public record CsrfTokenResponse(
        String headerName,
        String parameterName,
        String token) {
}
