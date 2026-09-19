package com.ofss.dto.transaction;

import jakarta.validation.constraints.AssertTrue;

public record AuthorizeTransactionRequest(

        @AssertTrue(
                message = "confirmed must be true to authorize the payment")
        boolean confirmed) {
}
