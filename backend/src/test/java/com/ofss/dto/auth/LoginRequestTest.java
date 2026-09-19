package com.ofss.dto.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class LoginRequestTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        validatorFactory =
                Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidatorFactory() {
        validatorFactory.close();
    }

    @Test
    void normalizesEmailLoginIdentifier() {
        LoginRequest request = new LoginRequest(
                "  CUSTOMER@EXAMPLE.COM  ",
                "SafePay@2026");

        assertThat(validator.validate(request)).isEmpty();
        assertThat(request.loginIdentifier())
                .isEqualTo("customer@example.com");
    }

    @Test
    void normalizesMobileButPreservesPasswordWhitespace() {
        LoginRequest request = new LoginRequest(
                "  +919876543210  ",
                " SafePay@2026 ");

        assertThat(validator.validate(request)).isEmpty();
        assertThat(request.loginIdentifier())
                .isEqualTo("+919876543210");
        assertThat(request.password())
                .isEqualTo(" SafePay@2026 ");
    }

    @Test
    void rejectsBlankCredentials() {
        LoginRequest request = new LoginRequest("   ", "   ");

        Set<ConstraintViolation<LoginRequest>> violations =
                validator.validate(request);

        assertThat(violations)
                .extracting(violation ->
                        violation.getPropertyPath().toString())
                .contains("loginIdentifier", "password");
    }

    @Test
    void rejectsPasswordExceedingBcryptByteLimit() {
        LoginRequest request = new LoginRequest(
                "customer@example.com",
                "€".repeat(25));

        Set<ConstraintViolation<LoginRequest>> violations =
                validator.validate(request);

        assertThat(violations)
                .extracting(violation ->
                        violation.getPropertyPath().toString())
                .contains("passwordWithinBcryptLimit");
    }
}