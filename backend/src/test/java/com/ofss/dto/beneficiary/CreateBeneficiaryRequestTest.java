package com.ofss.dto.beneficiary;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.ofss.beans.BeneficiaryPaymentMethod;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class CreateBeneficiaryRequestTest {

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
    void acceptsAndNormalizesBankAccountRequest() {
        CreateBeneficiaryRequest request =
                new CreateBeneficiaryRequest(
                        "  Demo Supplier  ",
                        "  Office Vendor  ",
                        BeneficiaryPaymentMethod.BANK_ACCOUNT,
                        "  SafePay Demo Bank  ",
                        "  123456789012  ",
                        " abcd0123456 ",
                        null,
                        "  Supplier  ",
                        "  Monthly invoice  ");

        assertThat(validator.validate(request)).isEmpty();
        assertThat(request.beneficiaryName())
                .isEqualTo("Demo Supplier");
        assertThat(request.nickname())
                .isEqualTo("Office Vendor");
        assertThat(request.bankName())
                .isEqualTo("SafePay Demo Bank");
        assertThat(request.bankAccountNumber())
                .isEqualTo("123456789012");
        assertThat(request.ifscCode())
                .isEqualTo("ABCD0123456");
        assertThat(request.upiId()).isNull();
        assertThat(request.relationshipLabel())
                .isEqualTo("Supplier");
        assertThat(request.purposeNote())
                .isEqualTo("Monthly invoice");
    }

    @Test
    void acceptsAndNormalizesUpiRequest() {
        CreateBeneficiaryRequest request =
                new CreateBeneficiaryRequest(
                        "  Demo Merchant  ",
                        "   ",
                        BeneficiaryPaymentMethod.UPI,
                        null,
                        null,
                        null,
                        " Merchant.Pay@ExampleBank ",
                        "   ",
                        null);

        assertThat(validator.validate(request)).isEmpty();
        assertThat(request.beneficiaryName())
                .isEqualTo("Demo Merchant");
        assertThat(request.upiId())
                .isEqualTo("merchant.pay@examplebank");
        assertThat(request.nickname()).isNull();
        assertThat(request.relationshipLabel()).isNull();
    }

    @Test
    void rejectsMixedPaymentMethodFields() {
        CreateBeneficiaryRequest request =
                new CreateBeneficiaryRequest(
                        "Demo Supplier",
                        null,
                        BeneficiaryPaymentMethod.BANK_ACCOUNT,
                        "SafePay Demo Bank",
                        "123456789012",
                        "ABCD0123456",
                        "merchant@examplebank",
                        null,
                        null);

        Set<ConstraintViolation<CreateBeneficiaryRequest>>
                violations = validator.validate(request);

        assertThat(violations)
                .extracting(violation ->
                        violation.getPropertyPath().toString())
                .contains("paymentDetailsValid");
    }

    @Test
    void rejectsMissingBankAccountFields() {
        CreateBeneficiaryRequest request =
                new CreateBeneficiaryRequest(
                        "Demo Supplier",
                        null,
                        BeneficiaryPaymentMethod.BANK_ACCOUNT,
                        "SafePay Demo Bank",
                        null,
                        "ABCD0123456",
                        null,
                        null,
                        null);

        Set<ConstraintViolation<CreateBeneficiaryRequest>>
                violations = validator.validate(request);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .contains(
                        "payment details must match paymentMethod");
    }

    @Test
    void rejectsMalformedIfsc() {
        CreateBeneficiaryRequest request =
                new CreateBeneficiaryRequest(
                        "Demo Supplier",
                        null,
                        BeneficiaryPaymentMethod.BANK_ACCOUNT,
                        "SafePay Demo Bank",
                        "123456789012",
                        "INVALID",
                        null,
                        null,
                        null);

        Set<ConstraintViolation<CreateBeneficiaryRequest>>
                violations = validator.validate(request);

        assertThat(violations)
                .extracting(violation ->
                        violation.getPropertyPath().toString())
                .contains("ifscCode");
    }

    @Test
    void rejectsMissingPaymentMethod() {
        CreateBeneficiaryRequest request =
                new CreateBeneficiaryRequest(
                        "Demo Beneficiary",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null);

        Set<ConstraintViolation<CreateBeneficiaryRequest>>
                violations = validator.validate(request);

        assertThat(violations)
                .extracting(violation ->
                        violation.getPropertyPath().toString())
                .contains("paymentMethod");
    }
}
