package com.ofss.dto.beneficiary;

import com.ofss.beans.BeneficiaryStatus;

import jakarta.validation.constraints.NotNull;

public record UpdateBeneficiaryStatusRequest(

        @NotNull(message = "status is required")
        BeneficiaryStatus status) {
}
