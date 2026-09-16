package com.ofss.beans;

import java.time.LocalDateTime;

/** Customer profile fields only: no credentials, lock metadata or entity relationships. */
public record UserProfileResponse(Long userId, String name, String email, String phone,
        UserStatus status, LocalDateTime createdAt, LocalDateTime updatedAt) {
    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(user.getUserId(), user.getName(), user.getEmail(), user.getPhone(),
                user.getStatus(), user.getCreatedAt(), user.getUpdatedAt());
    }
}
