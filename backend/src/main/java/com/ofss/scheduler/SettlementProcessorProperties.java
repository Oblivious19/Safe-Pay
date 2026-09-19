package com.ofss.scheduler;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "safepay.settlement-processor")
public record SettlementProcessorProperties(
        boolean enabled,
        Duration pollInterval,
        int batchSize,
        Long outboundClearingAccountId,
        List<Duration> retryDelays) {

    public static final Duration APPROVED_POLL_INTERVAL =
            Duration.ofSeconds(1);

    public static final int MAX_BATCH_SIZE = 25;

    public static final List<Duration> APPROVED_RETRY_DELAYS =
            List.of(
                    Duration.ofSeconds(5),
                    Duration.ofSeconds(30),
                    Duration.ofMinutes(1));

    public SettlementProcessorProperties {
        Objects.requireNonNull(
                pollInterval,
                "pollInterval is required");

        if (!APPROVED_POLL_INTERVAL.equals(pollInterval)) {
            throw new IllegalArgumentException(
                    "pollInterval must be PT1S for SafePay V1");
        }

        if (batchSize < 1 || batchSize > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException(
                    "batchSize must be between 1 and "
                            + MAX_BATCH_SIZE);
        }

        if (enabled
                && (outboundClearingAccountId == null
                        || outboundClearingAccountId <= 0L)) {
            throw new IllegalArgumentException(
                    "outboundClearingAccountId is required when enabled");
        }

        if (!enabled
                && outboundClearingAccountId != null
                && outboundClearingAccountId <= 0L) {
            throw new IllegalArgumentException(
                    "outboundClearingAccountId must be positive");
        }

        Objects.requireNonNull(
                retryDelays,
                "retryDelays is required");

        retryDelays = List.copyOf(retryDelays);

        if (!APPROVED_RETRY_DELAYS.equals(retryDelays)) {
            throw new IllegalArgumentException(
                    "retryDelays must be PT5S, PT30S, PT1M");
        }
    }

    public int maximumAutomaticRetries() {
        return retryDelays.size();
    }

    public Duration retryDelayForAttempt(int retryAttempt) {
        if (retryAttempt < 1
                || retryAttempt > maximumAutomaticRetries()) {
            throw new IllegalArgumentException(
                    "retryAttempt must be between 1 and "
                            + maximumAutomaticRetries());
        }

        return retryDelays.get(retryAttempt - 1);
    }

    public long requireOutboundClearingAccountId() {
        if (outboundClearingAccountId == null
                || outboundClearingAccountId <= 0L) {
            throw new IllegalStateException(
                    "outboundClearingAccountId is not configured");
        }

        return outboundClearingAccountId;
    }
}
