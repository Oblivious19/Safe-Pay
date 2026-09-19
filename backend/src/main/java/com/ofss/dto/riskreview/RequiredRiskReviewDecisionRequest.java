package com.ofss.dto.riskreview;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RequiredRiskReviewDecisionRequest(
        @NotBlank(message = "reason is required")
        @Size(
                max = 1000,
                message = "reason must not exceed 1000 characters")
        String reason) {
}
