package com.ofss.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ofss.beans.SafePayPinResetStatus;
import com.ofss.beans.User;
import com.ofss.dto.admin.SafePayPinResetRequestResponse;
import com.ofss.excp.BusinessRuleException;
import com.ofss.repository.UserDao;

@RestController
@RequestMapping("/api/v1/admin/safe-pay-pin-resets")
@PreAuthorize("hasAuthority('SYSTEM_ADMIN')")
public class AdminSafePayPinResetController {
    private final UserDao userDao;
    public AdminSafePayPinResetController(UserDao userDao) { this.userDao = userDao; }

    @GetMapping
    public List<SafePayPinResetRequestResponse> pending() {
        return userDao.findBySafePayPinResetStatus(SafePayPinResetStatus.PENDING).stream()
                .map(SafePayPinResetRequestResponse::from).toList();
    }

    @PostMapping("/{userId}/approve")
    @Transactional
    public ResponseEntity<SafePayPinResetRequestResponse> approve(@PathVariable Long userId) {
        return ResponseEntity.ok(decide(userId, true));
    }

    @PostMapping("/{userId}/reject")
    @Transactional
    public ResponseEntity<SafePayPinResetRequestResponse> reject(@PathVariable Long userId) {
        return ResponseEntity.ok(decide(userId, false));
    }

    private SafePayPinResetRequestResponse decide(Long userId, boolean approved) {
        User user = userDao.findByIdForUpdate(userId).orElseThrow(() -> new BusinessRuleException("USER_NOT_FOUND", "User was not found."));
        try { user.decideSafePayPinReset(approved, userDao.currentDatabaseTime()); }
        catch (IllegalStateException exception) { throw new BusinessRuleException("PIN_RESET_NOT_PENDING", "This PIN reset request is no longer pending."); }
        return SafePayPinResetRequestResponse.from(userDao.save(user));
    }
}
