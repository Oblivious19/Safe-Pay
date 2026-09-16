package com.ofss.beans;

import java.io.Serializable;

/** Safe session identity and login response; never contains credentials or a JPA entity. */
public record LoginPrincipal(Long userId, String name, String email, String role, UserStatus status)
        implements Serializable {}
