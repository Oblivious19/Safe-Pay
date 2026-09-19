package com.ofss.dto.riskreview;

import jakarta.validation.constraints.NotBlank;

public record RiskReviewNoteRequest(
        @NotBlank(message = "note is required")
        String note) {
}
