package com.ofss.excp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IdempotencyConflictExceptionTest {

    @Test
    void createsSafeDifferentRequestConflict() {
        IdempotencyConflictException exception =
                IdempotencyConflictException.differentRequest();

        assertThat(exception.getErrorCode())
                .isEqualTo("IDEMPOTENCY_KEY_REUSED");
        assertThat(exception.getMessage())
                .isEqualTo(
                        "The idempotency key has already been used for a different request.")
                .doesNotContain("actual-key");
    }

    @Test
    void createsSafeInProgressConflict() {
        IdempotencyConflictException exception =
                IdempotencyConflictException.requestInProgress();

        assertThat(exception.getErrorCode())
                .isEqualTo("IDEMPOTENCY_REQUEST_IN_PROGRESS");
        assertThat(exception.getMessage())
                .isEqualTo(
                        "An equivalent request is already being processed.");
    }

    @Test
    void createsSafeExpiredKeyConflict() {
        IdempotencyConflictException exception =
                IdempotencyConflictException.expired();

        assertThat(exception.getErrorCode())
                .isEqualTo("IDEMPOTENCY_KEY_EXPIRED");
        assertThat(exception.getMessage())
                .isEqualTo(
                        "The idempotency key has expired; submit the request with a new key.");
    }
}
