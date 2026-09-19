package com.ofss.dto.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

class AuthorizeTransactionRequestTest {

    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        validator = Validation
                .buildDefaultValidatorFactory()
                .getValidator();
    }

    @Test
    void acceptsExplicitCustomerConfirmation() {
        assertThat(validator.validate(
                new AuthorizeTransactionRequest(true)))
                .isEmpty();
    }

    @Test
    void rejectsAuthorizationWithoutConfirmation() {
        assertThat(validator.validate(
                new AuthorizeTransactionRequest(false)))
                .singleElement()
                .satisfies(violation -> {
                    assertThat(violation.getPropertyPath().toString())
                            .isEqualTo("confirmed");
                    assertThat(violation.getMessage())
                            .isEqualTo(
                                    "confirmed must be true to authorize the payment");
                });
    }
}
