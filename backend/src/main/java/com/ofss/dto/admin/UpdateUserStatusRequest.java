package com.ofss.dto.admin;

import com.ofss.beans.UserStatus;

import jakarta.validation.constraints.NotNull;

public record UpdateUserStatusRequest(
        @NotNull(message = "status is required")
        UserStatus status) {
}
