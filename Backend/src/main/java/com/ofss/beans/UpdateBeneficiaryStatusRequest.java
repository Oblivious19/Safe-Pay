package com.ofss.beans;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateBeneficiaryStatusRequest(
        @NotBlank @Pattern(regexp = "ACTIVE|INACTIVE", message = "must be ACTIVE or INACTIVE") String status,
        @NotBlank @Email @Size(max = 150) String userEmail) {
}
