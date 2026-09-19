package com.ofss.dto.riskreview;

import jakarta.validation.constraints.Size;

public record RiskReviewDecisionRequest(
        @Size(
                max = 1000,
                message = "reason must not exceed 1000 characters")
        String reason) {
}
