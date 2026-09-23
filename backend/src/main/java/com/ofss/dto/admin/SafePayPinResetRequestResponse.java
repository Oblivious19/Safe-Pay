package com.ofss.dto.admin;

import com.ofss.beans.User;

public record SafePayPinResetRequestResponse(Long userId, String fullName, String email, String mobileNumber, String status) {
    public static SafePayPinResetRequestResponse from(User user) {
        return new SafePayPinResetRequestResponse(user.getUserId(), user.getFullName(), user.getEmail(), user.getMobileNumber(), user.getSafePayPinResetStatus().name());
    }
}
