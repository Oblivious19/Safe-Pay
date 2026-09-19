package com.ofss.scheduler;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "safepay.notification-dispatcher")
public record NotificationDispatcherProperties(
        boolean enabled,
        Duration pollInterval,
        int batchSize,
        int maxAttempts,
        List<Duration> retryDelays) {

    public static final Duration APPROVED_POLL_INTERVAL =
            Duration.ofSeconds(1);
    public static final int MAX_BATCH_SIZE = 25;
    public static final int APPROVED_MAX_ATTEMPTS = 5;
    public static final List<Duration> APPROVED_RETRY_DELAYS =
            List.of(
                    Duration.ofSeconds(5),
                    Duration.ofSeconds(30),
                    Duration.ofMinutes(1),
                    Duration.ofMinutes(5));

    public NotificationDispatcherProperties {
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
        if (maxAttempts != APPROVED_MAX_ATTEMPTS) {
            throw new IllegalArgumentException(
                    "maxAttempts must be 5 for SafePay V1");
        }
        Objects.requireNonNull(
                retryDelays,
                "retryDelays is required");
        retryDelays = List.copyOf(retryDelays);
        if (!APPROVED_RETRY_DELAYS.equals(retryDelays)) {
            throw new IllegalArgumentException(
                    "retryDelays must be PT5S, PT30S, PT1M, PT5M");
        }
        if (retryDelays.size() != maxAttempts - 1) {
            throw new IllegalArgumentException(
                    "retryDelays must cover every non-terminal failure");
        }
    }

    public Duration retryDelayAfterFailure(int failedAttemptCount) {
        if (failedAttemptCount < 1
                || failedAttemptCount >= maxAttempts) {
            throw new IllegalArgumentException(
                    "failedAttemptCount must be between 1 and 4");
        }
        return retryDelays.get(failedAttemptCount - 1);
    }
}
