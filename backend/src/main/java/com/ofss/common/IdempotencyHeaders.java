package com.ofss.common;

public final class IdempotencyHeaders {

    public static final String IDEMPOTENCY_KEY =
            "Idempotency-Key";

    public static final String IDEMPOTENCY_REPLAYED =
            "Idempotency-Replayed";

    private IdempotencyHeaders() {
        // Utility class; instantiation is prohibited.
    }
}
