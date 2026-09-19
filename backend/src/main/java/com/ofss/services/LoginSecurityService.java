package com.ofss.services;

public interface LoginSecurityService {

    void prepareForAuthentication(String loginIdentifier);

    void recordFailure(
            String loginIdentifier,
            String correlationId);
}
