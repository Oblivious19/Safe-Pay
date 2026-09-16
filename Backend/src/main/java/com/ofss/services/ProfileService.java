package com.ofss.services;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import com.ofss.beans.*;
import com.ofss.excp.*;
import com.ofss.repository.ProfileRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {
    private final ProfileRepository profiles;
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();
    public ProfileService(ProfileRepository profiles) { this.profiles = profiles; }

    @Transactional
    public UserProfileResponse update(Long callerId, ProfileUpdateRequest request) {
        if (request == null || request.name() == null || request.name().isBlank()
                || request.email() == null || request.email().isBlank()
                || request.phone() == null || !request.phone().matches("[0-9]{10}")) {
            throw new IllegalArgumentException("Valid name, email and 10-digit phone are required");
        }
        requireBytes(request.name(), 100, "Name");
        requireBytes(request.email(), 150, "Email");
        if (request.password() != null) requireBytes(request.password(), 72, "Password");
        User user = profiles.lockProfile(callerId).orElseThrow(() -> new ResourceNotFoundExcp("User not found"));
        if (user.getStatus() != UserStatus.ACTIVE || user.getRole() == null
                || !"CUSTOMER".equals(user.getRole().getRoleName())) {
            throw new TransactionValidationException(403, "An active CUSTOMER is required to edit this profile");
        }
        if (profiles.existsByEmailAndUserIdNot(request.email(), callerId)) throw new DuplicateEmailException();
        if (profiles.existsByPhoneAndUserIdNot(request.phone(), callerId)) throw new DuplicatePhoneException();
        user.setName(request.name());
        user.setEmail(request.email());
        user.setPhone(request.phone());
        if (request.password() != null && !request.password().isBlank()) {
            user.setPasswordHash(passwords.encode(request.password()));
        }
        user.setUpdatedAt(LocalDateTime.now());
        return UserProfileResponse.from(profiles.saveAndFlush(user));
    }

    private void requireBytes(String value, int maximum, String field) {
        if (value.getBytes(StandardCharsets.UTF_8).length > maximum) {
            throw new IllegalArgumentException(field + " must not exceed " + maximum + " UTF-8 bytes");
        }
    }
}
