package com.ofss.beans;

import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotNull;

public final class AdminDtos {
    private AdminDtos() {}

    public record UserStatusRequest(@NotNull UserStatus status) {
        @JsonAnySetter
        public void rejectExtraField(String name, Object value) {
            throw new IllegalArgumentException("Only status may be supplied");
        }
    }

    public record AccountStatusRequest(@NotNull AccountStatus status) {
        @JsonAnySetter
        public void rejectExtraField(String name, Object value) {
            throw new IllegalArgumentException("Only status may be supplied");
        }
    }

    public record UserResponse(Long userId, String name, String email, String phone, String role,
            UserStatus status, LocalDateTime createdAt, LocalDateTime updatedAt) {
        public static UserResponse from(User user) {
            return new UserResponse(user.getUserId(), user.getName(), user.getEmail(), user.getPhone(),
                    user.getRole().getRoleName(), user.getStatus(), user.getCreatedAt(), user.getUpdatedAt());
        }
    }

    public record AccountResponse(Long accountId, Long userId, String accountNumber,
            AccountType accountType, AccountStatus status, LocalDateTime updatedAt) {}
}
