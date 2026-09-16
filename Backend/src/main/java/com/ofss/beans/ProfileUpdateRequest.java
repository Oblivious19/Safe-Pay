package com.ofss.beans;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.*;

public record ProfileUpdateRequest(@NotBlank @Size(max = 100) String name,
        @NotBlank @Email @Size(max = 150) String email,
        @NotBlank @Pattern(regexp = "[0-9]{10}") String phone, @Size(max = 72) String password) {
    public ProfileUpdateRequest {
        name = name == null ? null : name.strip();
        email = email == null ? null : email.strip();
        phone = phone == null ? null : phone.strip();
    }
    @JsonAnySetter
    public void rejectExtraField(String name, Object value) {
        throw new IllegalArgumentException("Only name, email, phone and optional password may be supplied");
    }
}
