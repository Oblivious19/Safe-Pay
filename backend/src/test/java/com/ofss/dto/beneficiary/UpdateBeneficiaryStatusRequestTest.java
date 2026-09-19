package com.ofss.dto.beneficiary;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.ofss.beans.BeneficiaryStatus;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class UpdateBeneficiaryStatusRequestTest {

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
    void acceptsCanonicalStatus() {
        UpdateBeneficiaryStatusRequest request =
                new UpdateBeneficiaryStatusRequest(
                        BeneficiaryStatus.DISABLED);

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void rejectsMissingStatus() {
        UpdateBeneficiaryStatusRequest request =
                new UpdateBeneficiaryStatusRequest(null);

        assertThat(validator.validate(request))
                .extracting(violation ->
                        violation.getPropertyPath().toString())
                .contains("status");
    }
}
