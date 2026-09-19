package com.ofss.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.SecureRandom;

import org.junit.jupiter.api.Test;

class SecureOtpCodeGeneratorTest {

    @Test
    void generatesExactlySixAsciiDigitsWithLeadingZeroes() {
        SecureRandom secureRandom = mock(SecureRandom.class);
        when(secureRandom.nextInt(1_000_000)).thenReturn(42);

        OtpCode code = generator(secureRandom).generate();

        assertThat(code.value()).isEqualTo("000042");
        assertThat(code.value()).matches("[0-9]{6}");
        verify(secureRandom).nextInt(1_000_000);
    }

    @Test
    void supportsUpperEdgeWithoutExpandingLength() {
        SecureRandom secureRandom = mock(SecureRandom.class);
        when(secureRandom.nextInt(1_000_000)).thenReturn(999_999);

        assertThat(generator(secureRandom).generate().value())
                .isEqualTo("999999");
    }

    @Test
    void protectedStringRepresentationNeverRevealsRawOtp() {
        SecureRandom secureRandom = mock(SecureRandom.class);
        when(secureRandom.nextInt(1_000_000)).thenReturn(123_456);

        OtpCode code = generator(secureRandom).generate();

        assertThat(code.toString())
                .isEqualTo("OtpCode[PROTECTED]")
                .doesNotContain("123456");
    }

    @Test
    void rejectsNonAsciiOrWrongLengthCodeMaterial() {
        assertThatThrownBy(() -> new OtpCode("12345", 6))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "OTP value must contain exactly 6 numeric digits");
        assertThatThrownBy(() -> new OtpCode("12345A", 6))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "OTP value must contain exactly 6 numeric digits");
    }

    private static SecureOtpCodeGenerator generator(
            SecureRandom secureRandom) {

        return new SecureOtpCodeGenerator(
                secureRandom,
                OtpPolicyPropertiesTest.approvedPolicy());
    }
}
