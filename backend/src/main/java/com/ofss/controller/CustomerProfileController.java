package com.ofss.controller;

import java.util.Objects;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ofss.dto.user.CustomerProfileResponse;
import com.ofss.security.AuthenticatedUser;
import com.ofss.services.UserService;

@RestController
@RequestMapping("/api/v1/users/me")
@PreAuthorize("hasAuthority('CUSTOMER')")
public class CustomerProfileController {

    private final UserService service;

    public CustomerProfileController(UserService service) {
        this.service = Objects.requireNonNull(service, "service is required");
    }

    @GetMapping
    public CustomerProfileResponse getOwnProfile(Authentication authentication) {
        return service.getOwnProfile(AuthenticatedUser.userId(authentication));
    }
}
