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

class RegisterUserRequestTest {

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
    void acceptsAndNormalizesEmailRegistration() {
        RegisterUserRequest request = new RegisterUserRequest(
                "  Aditya Rao  ",
                "  CUSTOMER@EXAMPLE.COM  ",
                null,
                "SafePay@2026");

        Set<ConstraintViolation<RegisterUserRequest>> violations =
                validator.validate(request);

        assertThat(violations).isEmpty();
        assertThat(request.fullName()).isEqualTo("Aditya Rao");
        assertThat(request.email())
                .isEqualTo("customer@example.com");
        assertThat(request.mobileNumber()).isNull();
    }

    @Test
    void acceptsMobileRegistration() {
        RegisterUserRequest request = new RegisterUserRequest(
                "SafePay Customer",
                null,
                "  +919876543210  ",
                "SafePay@2026");

        Set<ConstraintViolation<RegisterUserRequest>> violations =
                validator.validate(request);

        assertThat(violations).isEmpty();
        assertThat(request.mobileNumber())
                .isEqualTo("+919876543210");
    }

    @Test
    void rejectsRequestWithoutContactInformation() {
        RegisterUserRequest request = new RegisterUserRequest(
                "SafePay Customer",
                "   ",
                "   ",
                "SafePay@2026");

        Set<ConstraintViolation<RegisterUserRequest>> violations =
                validator.validate(request);

        assertThat(violations)
                .extracting(violation ->
                        violation.getPropertyPath().toString())
                .contains("contactProvided");

        assertThat(request.email()).isNull();
        assertThat(request.mobileNumber()).isNull();
    }

    @Test
    void rejectsMalformedContactValues() {
        RegisterUserRequest request = new RegisterUserRequest(
                "SafePay Customer",
                "invalid-email",
                "9876543210",
                "SafePay@2026");

        Set<ConstraintViolation<RegisterUserRequest>> violations =
                validator.validate(request);

        assertThat(violations)
                .extracting(violation ->
                        violation.getPropertyPath().toString())
                .contains("email", "mobileNumber");
    }

    @Test
    void rejectsShortPassword() {
        RegisterUserRequest request = new RegisterUserRequest(
                "SafePay Customer",
                "customer@example.com",
                null,
                "Short@1");

        Set<ConstraintViolation<RegisterUserRequest>> violations =
                validator.validate(request);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .contains(
                        "password must contain between 12 and 72 characters");
    }

    @Test
    void rejectsPasswordExceedingBcryptByteLimit() {
        RegisterUserRequest request = new RegisterUserRequest(
                "SafePay Customer",
                "customer@example.com",
                null,
                "€".repeat(25));

        Set<ConstraintViolation<RegisterUserRequest>> violations =
                validator.validate(request);

        assertThat(violations)
                .extracting(violation ->
                        violation.getPropertyPath().toString())
                .contains("passwordWithinBcryptLimit");

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .contains(
                        "password must not exceed 72 UTF-8 bytes");
    }
}