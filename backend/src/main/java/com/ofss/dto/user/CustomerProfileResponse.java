package com.ofss.dto.user;

import com.ofss.beans.User;
import com.ofss.beans.UserStatus;

public record CustomerProfileResponse(
        String userId, String fullName, String email,
        String mobileNumber, UserStatus status) {

    public static CustomerProfileResponse from(User user) {
        return new CustomerProfileResponse(user.getUserId().toString(),
                user.getFullName(), user.getEmail(), user.getMobileNumber(), user.getStatus());
    }
}
