package com.ofss.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class OperationContextTest {

    @Test
    void normalizesRequestMetadata() {
        OperationContext context = OperationContext.request(
                "  CORR-12  ",
                "  IDEMP-34  ");

        assertThat(context.correlationId()).isEqualTo("CORR-12");
        assertThat(context.idempotencyKey()).isEqualTo("IDEMP-34");
    }

    @Test
    void permitsInternalOperationsWithoutIdempotencyKey() {
        OperationContext context = OperationContext.internal();

        assertThat(context.correlationId())
                .startsWith("SYSTEM-")
                .hasSize(43);
        assertThat(context.idempotencyKey()).isNull();
    }

    @Test
    void rejectsMissingOrBlankCorrelationId() {
        assertThatThrownBy(() -> new OperationContext(null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("correlationId is required");
        assertThatThrownBy(() -> new OperationContext("  ", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("correlationId cannot be blank");
    }

    @Test
    void enforcesDatabaseColumnLengths() {
        assertThatThrownBy(() -> new OperationContext(
                "C".repeat(65),
                null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("correlationId has an invalid length");
        assertThatThrownBy(() -> new OperationContext(
                "CORR",
                "K".repeat(129)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("idempotencyKey has an invalid length");
    }
}
