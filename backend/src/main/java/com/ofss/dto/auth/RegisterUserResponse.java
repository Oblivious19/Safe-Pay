package com.ofss.dto.auth;

import java.util.Objects;

import com.ofss.beans.RoleName;
import com.ofss.beans.User;
import com.ofss.beans.UserStatus;

public record RegisterUserResponse(
        String userId,
        String fullName,
        String email,
        String mobileNumber,
        UserStatus status,
        RoleName role) {

    public static RegisterUserResponse fromCustomer(User user) {
        Objects.requireNonNull(user, "user is required");

        if (user.getUserId() == null) {
            throw new IllegalArgumentException(
                    "user must already be persisted");
        }

        return new RegisterUserResponse(
                user.getUserId().toString(),
                user.getFullName(),
                user.getEmail(),
                user.getMobileNumber(),
                user.getStatus(),
                RoleName.CUSTOMER);
    }
}