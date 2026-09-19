package com.ofss.scheduler;

import java.time.Duration;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "safepay.protection-scheduler")
public record ProtectionSchedulerProperties(
        boolean enabled,
        Duration pollInterval,
        int batchSize) {

    public static final int MAX_BATCH_SIZE = 50;

    public ProtectionSchedulerProperties {
        Objects.requireNonNull(
                pollInterval,
                "pollInterval is required");

        if (pollInterval.isZero() || pollInterval.isNegative()) {
            throw new IllegalArgumentException(
                    "pollInterval must be positive");
        }

        if (batchSize < 1 || batchSize > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException(
                    "batchSize must be between 1 and "
                            + MAX_BATCH_SIZE);
        }
    }
}
