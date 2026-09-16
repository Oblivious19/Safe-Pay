package com.ofss.controller;

import com.ofss.beans.*;
import com.ofss.services.ProfileService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users/current")
public class ProfileController {
    private final ProfileService profiles;
    private final HttpSessionSecurityContextRepository sessions;
    public ProfileController(ProfileService profiles, HttpSessionSecurityContextRepository sessions) {
        this.profiles = profiles;
        this.sessions = sessions;
    }

    @PutMapping(consumes = "application/json")
    public UserProfileResponse update(@AuthenticationPrincipal LoginPrincipal caller,
            @Valid @RequestBody ProfileUpdateRequest input, HttpServletRequest request, HttpServletResponse response) {
        UserProfileResponse profile = profiles.update(caller.userId(), input);
        var context = SecurityContextHolder.createEmptyContext();
        var previous = SecurityContextHolder.getContext().getAuthentication();
        var principal = new LoginPrincipal(profile.userId(), profile.name(), profile.email(), caller.role(), profile.status());
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, previous.getAuthorities()));
        SecurityContextHolder.setContext(context);
        sessions.saveContext(context, request, response);
        return profile;
    }
}
