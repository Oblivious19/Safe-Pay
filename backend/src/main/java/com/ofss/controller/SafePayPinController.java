package com.ofss.controller;

import java.util.Objects;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ofss.beans.User;
import com.ofss.dto.user.SafePayPinSetupRequest;
import com.ofss.dto.user.SafePayPinStatusResponse;
import com.ofss.excp.BusinessRuleException;
import com.ofss.repository.UserDao;
import com.ofss.security.AuthenticatedUser;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/profile/safe-pay-pin")
@PreAuthorize("hasAuthority('CUSTOMER')")
public class SafePayPinController {
    private final UserDao userDao;
    private final PasswordEncoder passwordEncoder;

    public SafePayPinController(UserDao userDao, PasswordEncoder passwordEncoder) {
        this.userDao = Objects.requireNonNull(userDao, "userDao is required");
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "passwordEncoder is required");
    }

    @GetMapping("/status")
    public SafePayPinStatusResponse status(Authentication authentication) {
        User user = requireUser(AuthenticatedUser.userId(authentication));
        return response(user);
    }

    @PostMapping
    @Transactional
    public ResponseEntity<SafePayPinStatusResponse> setup(
            Authentication authentication,
            @Valid @RequestBody SafePayPinSetupRequest request) {
        if (!request.pin().equals(request.confirmation())) {
            throw new BusinessRuleException("SAFE_PAY_PIN_MISMATCH", "The SafePay PINs do not match.");
        }
        User user = userDao.findByIdForUpdate(AuthenticatedUser.userId(authentication))
                .orElseThrow(() -> new BusinessRuleException("USER_NOT_FOUND", "Your profile could not be found."));
        if (user.hasSafePayPin()
                && user.getSafePayPinResetStatus() != com.ofss.beans.SafePayPinResetStatus.APPROVED) {
            throw new BusinessRuleException("SAFE_PAY_PIN_ALREADY_SET", "Your SafePay PIN is already set.");
        }
        user.setSafePayPin(passwordEncoder.encode(request.pin()), userDao.currentDatabaseTime());
        userDao.save(user);
        return ResponseEntity.ok(response(user));
    }

    @PostMapping("/reset-request")
    @Transactional
    public ResponseEntity<SafePayPinStatusResponse> requestReset(Authentication authentication) {
        User user = userDao.findByIdForUpdate(AuthenticatedUser.userId(authentication))
                .orElseThrow(() -> new BusinessRuleException("USER_NOT_FOUND", "Your profile could not be found."));
        try { user.requestSafePayPinReset(userDao.currentDatabaseTime()); }
        catch (IllegalStateException exception) { throw new BusinessRuleException("SAFE_PAY_PIN_RESET_UNAVAILABLE", "A SafePay PIN reset cannot be requested right now."); }
        userDao.save(user);
        return ResponseEntity.ok(response(user));
    }

    private static SafePayPinStatusResponse response(User user) {
        return new SafePayPinStatusResponse(user.hasSafePayPin(), user.getSafePayPinResetStatus().name());
    }

    private User requireUser(Long userId) {
        return userDao.findById(userId)
                .orElseThrow(() -> new BusinessRuleException("USER_NOT_FOUND", "Your profile could not be found."));
    }
}
