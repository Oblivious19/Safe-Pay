package com.ofss.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;

import org.junit.jupiter.api.Test;

class RefreshTokenCodecTest {

    private final RefreshTokenCodec codec =
            new RefreshTokenCodec(new SecureRandom());

    @Test
    void generates256BitUrlSafeOpaqueToken() {
        assertThat(codec.generate())
                .hasSize(43)
                .matches("[A-Za-z0-9_-]+");
    }

    @Test
    void generatesDifferentTokens() {
        assertThat(codec.generate()).isNotEqualTo(codec.generate());
    }

    @Test
    void hashesDeterministicallyWithoutReturningRawToken() {
        String hash = codec.hash("opaque-refresh-token");
        assertThat(hash).hasSize(64);
        assertThat(hash).isEqualTo(codec.hash("opaque-refresh-token"));
        assertThat(hash).doesNotContain("opaque-refresh-token");
    }

    @Test
    void rejectsBlankRefreshToken() {
        assertThatThrownBy(() -> codec.hash("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
