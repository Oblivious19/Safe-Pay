package com.ofss.beans;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;

public record AdminProvisionRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Email @Size(max = 150) String email,
        @NotBlank @Pattern(regexp = "[6-9][0-9]{9}") String phone,
        @NotBlank @Size(min = 8, max = 72)
        @JsonAlias("temporaryPassword") @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String initialPassword) {
    public AdminProvisionRequest {
        name = name == null ? null : name.strip();
        email = email == null ? null : email.strip();
        phone = phone == null ? null : phone.strip();
    }
    @JsonAnySetter public void rejectExtraField(String key, Object value) {
        throw new IllegalArgumentException("Only name, email, phone and initialPassword may be supplied");
    }
    @Override public String toString() { return "AdminProvisionRequest[redacted]"; }
}