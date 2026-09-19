package com.ofss.dto.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

class CreateTransactionRequestTest {

    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        validator = Validation
                .buildDefaultValidatorFactory()
                .getValidator();
    }

    @Test
    void acceptsValidRequestAndNormalizesOptionalText() {
        CreateTransactionRequest request =
                new CreateTransactionRequest(
                        10L,
                        20L,
                        new BigDecimal("5000.01"),
                        "  Vendor payment  ",
                        "  INV-100  ");

        assertThat(validator.validate(request)).isEmpty();
        assertThat(request.purpose()).isEqualTo("Vendor payment");
        assertThat(request.customerReference()).isEqualTo("INV-100");
    }

    @Test
    void requiresAccountBeneficiaryAndAmount() {
        CreateTransactionRequest request =
                new CreateTransactionRequest(
                        null,
                        null,
                        null,
                        null,
                        null);

        assertThat(propertyNames(validator.validate(request)))
                .containsExactlyInAnyOrder(
                        "sourceAccountId",
                        "beneficiaryId",
                        "amount");
    }

    @Test
    void rejectsNonPositiveIdentifiers() {
        CreateTransactionRequest request =
                new CreateTransactionRequest(
                        0L,
                        -1L,
                        new BigDecimal("1.00"),
                        null,
                        null);

        assertThat(propertyNames(validator.validate(request)))
                .containsExactlyInAnyOrder(
                        "sourceAccountId",
                        "beneficiaryId");
    }

    @Test
    void rejectsEveryInvalidOracleMoneyShape() {
        for (String amountText : new String[] {
                "-1.00",
                "0.00",
                "0.99",
                "1.001",
                "5000.001",
                "10000000000000000.00"
        }) {
            CreateTransactionRequest request =
                    new CreateTransactionRequest(
                            10L,
                            20L,
                            new BigDecimal(amountText),
                            null,
                            null);

            assertThat(propertyNames(validator.validate(request)))
                    .contains("amount");
        }
    }

    @Test
    void rejectsOverlongOptionalText() {
        CreateTransactionRequest request =
                new CreateTransactionRequest(
                        10L,
                        20L,
                        new BigDecimal("1.00"),
                        "P".repeat(281),
                        "R".repeat(101));

        assertThat(propertyNames(validator.validate(request)))
                .containsExactlyInAnyOrder(
                        "purpose",
                        "customerReference");
    }

    @Test
    void acceptsExactTextAndAmountLimits() {
        CreateTransactionRequest request =
                new CreateTransactionRequest(
                        Long.MAX_VALUE,
                        Long.MAX_VALUE,
                        new BigDecimal("9999999999999999.99"),
                        "P".repeat(280),
                        "R".repeat(100), com.ofss.beans.PaymentCategory.MEDICAL);

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void categoryIsMandatoryOnlyStrictlyAboveThreshold() {
        for (String amount : new String[]{"10.00", "5000.00", "100000.00"}) {
            assertThat(validator.validate(new CreateTransactionRequest(10L, 20L,
                    new BigDecimal(amount), null, null))).isEmpty();
            assertThat(validator.validate(new CreateTransactionRequest(10L, 20L,
                    new BigDecimal(amount), null, null, com.ofss.beans.PaymentCategory.MEDICAL))).isNotEmpty();
        }
        assertThat(validator.validate(new CreateTransactionRequest(10L, 20L,
                new BigDecimal("100000.01"), null, null))).isNotEmpty();
        for (var category : com.ofss.beans.PaymentCategory.values()) {
            assertThat(validator.validate(new CreateTransactionRequest(10L, 20L,
                    new BigDecimal("100000.01"), "Reason", null, category))).isEmpty();
        }
    }

    @Test
    void othersReasonUsesExistingTrimmedPurposeWith140CharacterLimit() {
        for (String purpose : new String[]{"", "   ", "\t\n", "x".repeat(141)}) {
            assertThat(validator.validate(new CreateTransactionRequest(10L, 20L,
                    new BigDecimal("100000.01"), purpose, null, com.ofss.beans.PaymentCategory.OTHERS))).isNotEmpty();
        }
        CreateTransactionRequest request = new CreateTransactionRequest(10L, 20L,
                new BigDecimal("100000.01"), "  " + "x".repeat(140) + "  ", null,
                com.ofss.beans.PaymentCategory.OTHERS);
        assertThat(request.purpose()).hasSize(140);

        assertThat(validator.validate(request)).isEmpty();
    }

    private static Set<String> propertyNames(
            Set<ConstraintViolation<CreateTransactionRequest>>
                    violations) {

        return violations.stream()
                .map(violation -> violation
                        .getPropertyPath()
                        .toString())
                .collect(java.util.stream.Collectors.toSet());
    }
}
