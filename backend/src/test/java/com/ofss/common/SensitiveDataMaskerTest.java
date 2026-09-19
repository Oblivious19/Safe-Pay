package com.ofss.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SensitiveDataMaskerTest {

    @Test
    void masksAllButLastFourCharacters() {
        assertThat(SensitiveDataMasker.maskAccountNumber(
                "123456789012"))
                .isEqualTo("********9012");
    }

    @Test
    void fullyMasksShortAccountNumber() {
        assertThat(SensitiveDataMasker.maskAccountNumber(
                "1234"))
                .isEqualTo("****");
    }

    @Test
    void rejectsMissingAccountNumber() {
        assertThatThrownBy(
                () -> SensitiveDataMasker
                        .maskAccountNumber("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("accountNumber is required");
    }

    @Test
    void masksUpiHandleAndPreservesProvider() {
        assertThat(SensitiveDataMasker.maskUpiId(
                "merchant.pay@examplebank"))
                .isEqualTo(
                        "m**********y@examplebank");
    }

    @Test
    void fullyMasksShortUpiHandle() {
        assertThat(SensitiveDataMasker.maskUpiId(
                "ab@examplebank"))
                .isEqualTo("**@examplebank");
    }

    @Test
    void fullyMasksNonStructuredUpiIdentifier() {
        String identifier = "invalid-upi-id";

        assertThat(SensitiveDataMasker.maskUpiId(identifier))
                .isEqualTo("*".repeat(identifier.length()));
    }

    @Test
    void masksEmailLocalPartAndPreservesDomain() {
        assertThat(SensitiveDataMasker.maskEmail(
                "aditya.rao@gmail.com"))
                .isEqualTo("a*********@gmail.com");
    }

    @Test
    void masksSingleCharacterEmailLocalPart() {
        assertThat(SensitiveDataMasker.maskEmail("a@example.com"))
                .isEqualTo("a*@example.com");
    }

    @Test
    void rejectsMissingOrMalformedEmail() {
        assertThatThrownBy(() ->
                SensitiveDataMasker.maskEmail("mobile-only"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("email is invalid");
        assertThatThrownBy(() ->
                SensitiveDataMasker.maskEmail("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("email is required");
    }
}
