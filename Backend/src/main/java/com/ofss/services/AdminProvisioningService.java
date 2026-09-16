package com.ofss.services;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import com.ofss.beans.*;
import com.ofss.beans.AdminDtos.UserResponse;
import com.ofss.excp.DuplicateEmailException;
import com.ofss.excp.DuplicatePhoneException;
import com.ofss.repository.AuditLogDao;
import com.ofss.repository.RoleDao;
import com.ofss.repository.UserDao;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates an administrator with an initial password and no customer bank account. */
@Service
public class AdminProvisioningService {
    private final UserDao users;
    private final RoleDao roles;
    private final AuditLogDao audits;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    public AdminProvisioningService(UserDao users, RoleDao roles, AuditLogDao audits) {
        this.users = users; this.roles = roles; this.audits = audits;
    }

    @Transactional
    public UserResponse create(AdminProvisionRequest request, Long actorId) {
        if (actorId == null || actorId <= 0 || request == null) throw new IllegalArgumentException("Authenticated administrator is required");
        String password = request.initialPassword();
        if (password == null || password.isBlank() || password.length() < 8
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Initial password must have at least 8 characters and at most 72 UTF-8 bytes");
        }
        requireText(request.name(), 100, "Name");
        requireText(request.email(), 150, "Email");
        if (request.phone() == null || !request.phone().matches("[6-9][0-9]{9}")) throw new IllegalArgumentException("Enter a valid mobile number");
        if (users.findByEmail(request.email()).isPresent()) throw new DuplicateEmailException();
        if (users.existsByPhone(request.phone())) throw new DuplicatePhoneException();
        Role role = roles.findByRoleName("ADMIN").orElseThrow(() -> new IllegalStateException("ADMIN role is not configured"));
        User user = new User(); user.setName(request.name()); user.setEmail(request.email()); user.setPhone(request.phone());
        user.setRole(role); user.setStatus(UserStatus.ACTIVE); user.setFailedLoginAttempts(0);
        user.setLockedUntil(null); user.setLastLoginAt(null); user.setPasswordHash(encoder.encode(password));
        LocalDateTime now = LocalDateTime.now(); user.setCreatedAt(now); user.setUpdatedAt(now);
        User saved = users.saveAndFlush(user);
        AuditLog audit = new AuditLog(); audit.setUserId(actorId); audit.setAction("ADMIN_CREATED:" + saved.getUserId());
        audit.setNewState(UserStatus.ACTIVE.name()); audit.setCreatedAt(now); audits.saveAndFlush(audit);
        return UserResponse.from(saved);
    }
    private void requireText(String value, int maxBytes, String field) {
        if (value == null || value.isBlank() || value.getBytes(StandardCharsets.UTF_8).length > maxBytes) {
            throw new IllegalArgumentException(field + " is required and must not exceed " + maxBytes + " UTF-8 bytes");
        }
    }
}