package com.ofss.security;

public interface AccessTokenService {

    AccessToken issue(SafePayPrincipal principal);
}
