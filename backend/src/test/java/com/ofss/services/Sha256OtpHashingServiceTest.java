package com.ofss.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import java.util.Base64;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class Sha256OtpHashingServiceTest {

    private Sha256OtpHashingService service;

    @BeforeEach
    void setUp() {
        service = new Sha256OtpHashingService(
                new SecureRandom());
    }

    @Test
    void storesOnlyVersionSaltAndSha256Digest() {
        String encoded = service.hash(code("123456"));
        String[] parts = encoded.split("\\$", -1);

        assertThat(parts).hasSize(3);
        assertThat(parts[0]).isEqualTo("SHA-256");
        assertThat(Base64.getDecoder().decode(parts[1]))
                .hasSize(16);
        assertThat(Base64.getDecoder().decode(parts[2]))
                .hasSize(32);
        assertThat(encoded)
                .doesNotContain("123456")
                .hasSizeLessThanOrEqualTo(255);
    }

    @Test
    void correctCandidateMatchesStoredDigest() {
        String encoded = service.hash(code("654321"));

        assertThat(service.matches(code("654321"), encoded))
                .isTrue();
    }

    @Test
    void incorrectCandidateDoesNotMatch() {
        String encoded = service.hash(code("654321"));

        assertThat(service.matches(code("654320"), encoded))
                .isFalse();
    }

    @Test
    void freshSaltPreventsHashReuseForSameOtp() {
        OtpCode code = code("012345");

        String first = service.hash(code);
        String second = service.hash(code);

        assertThat(first).isNotEqualTo(second);
        assertThat(service.matches(code, first)).isTrue();
        assertThat(service.matches(code, second)).isTrue();
    }

    @Test
    void preservesLeadingZeroesAsPartOfSecret() {
        String encoded = service.hash(code("000042"));

        assertThat(service.matches(code("000042"), encoded))
                .isTrue();
        assertThat(service.matches(code("420000"), encoded))
                .isFalse();
    }

    @Test
    void malformedOrUnsupportedStoredMaterialFailsClosed() {
        assertThat(service.matches(code("123456"), null)).isFalse();
        assertThat(service.matches(code("123456"), "")).isFalse();
        assertThat(service.matches(
                code("123456"),
                "MD5$invalid$invalid")).isFalse();
        assertThat(service.matches(
                code("123456"),
                "SHA-256$%%%$%%%" )).isFalse();
        assertThat(service.matches(
                code("123456"),
                "SHA-256$YQ==$Yg==")).isFalse();
    }

    @Test
    void rejectsMissingOtpWithoutLeakingSecretMaterial() {
        assertThatThrownBy(() -> service.hash(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("otpCode is required");
        assertThatThrownBy(() -> service.matches(null, "stored"))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("candidate is required")
                .hasMessageNotContaining("stored");
    }

    private static OtpCode code(String value) {
        return new OtpCode(value, 6);
    }
}
