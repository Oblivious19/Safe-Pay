package com.ofss.services;

import com.ofss.dto.auth.LoginRequest;
import com.ofss.security.SafePayPrincipal;

public interface CredentialAuthenticationService {

    SafePayPrincipal authenticate(LoginRequest request);
}