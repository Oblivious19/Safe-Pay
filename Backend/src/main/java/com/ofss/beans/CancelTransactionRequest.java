package com.ofss.beans;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelTransactionRequest(@NotBlank @Email @Size(max = 150) String userEmail) {
}
