package com.ofss.beans;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VerificationRequest(@NotBlank @Size(max = 72) String password) {
    @JsonAnySetter
    public void rejectExtraField(String name, Object value) {
        throw new IllegalArgumentException("Only password may be supplied");
    }
}
