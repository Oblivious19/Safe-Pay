package com.ofss.beans;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record BeneficiaryStatusRequest(@NotNull @Pattern(regexp = "ACTIVE|INACTIVE") String status) {
    @JsonAnySetter
    public void rejectExtraField(String name, Object value) {
        throw new IllegalArgumentException("Only ACTIVE or INACTIVE status may be supplied");
    }
}
