package com.ofss.controller;

import java.util.List;
import java.util.Map;
import com.ofss.beans.LoginRequest;
import com.ofss.beans.LoginPrincipal;
import com.ofss.services.LoginService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;

@RestController
public class LoginController {
    private final LoginService service;
    private final HttpSessionSecurityContextRepository repository;
    private final ChangeSessionIdAuthenticationStrategy sessionStrategy;

    public LoginController(LoginService service, HttpSessionSecurityContextRepository repository,
            ChangeSessionIdAuthenticationStrategy sessionStrategy) {
        this.service = service;
        this.repository = repository;
        this.sessionStrategy = sessionStrategy;
    }

    @PostMapping(value = "/api/auth/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    public LoginPrincipal login(@RequestBody LoginRequest input,
            HttpServletRequest request, HttpServletResponse response) {
        input.validate();
        LoginPrincipal principal = input.getPhone() == null
                ? service.login(input.getEmail(), input.getPassword())
                : service.loginByPhone(input.getPhone(), input.getPassword());
        var authentication = UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of());
        sessionStrategy.onAuthentication(authentication, request, response);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        repository.saveContext(context, request, response);
        return (LoginPrincipal) context.getAuthentication().getPrincipal();
    }

    @ExceptionHandler(BadCredentialsException.class)
    ResponseEntity<Map<String, String>> invalidCredentials() {
        return ResponseEntity.status(401).body(Map.of("message", "Invalid email or password"));
    }

    @ExceptionHandler(DisabledException.class)
    ResponseEntity<Map<String, String>> inactiveUser() {
        return ResponseEntity.status(403).body(Map.of("message", "User is not active"));
    }

    @ExceptionHandler(LockedException.class)
    ResponseEntity<Map<String, String>> lockedUser() {
        return ResponseEntity.status(403).body(Map.of("message", "Login is locked. Please try again after the lock period expires."));
    }
}
