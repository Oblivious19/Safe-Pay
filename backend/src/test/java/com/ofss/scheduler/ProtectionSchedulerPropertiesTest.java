package com.ofss.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class ProtectionSchedulerPropertiesTest {

    @Test
    void acceptsApprovedV1Policy() {
        ProtectionSchedulerProperties properties =
                new ProtectionSchedulerProperties(
                        true,
                        Duration.ofSeconds(1),
                        50);

        assertThat(properties.enabled()).isTrue();
        assertThat(properties.pollInterval())
                .isEqualTo(Duration.ofSeconds(1));
        assertThat(properties.batchSize()).isEqualTo(50);
    }

    @Test
    void allowsSmallerPositiveBatchForOperationalTuning() {
        ProtectionSchedulerProperties properties =
                new ProtectionSchedulerProperties(
                        true,
                        Duration.ofMillis(500),
                        1);

        assertThat(properties.batchSize()).isEqualTo(1);
    }

    @Test
    void rejectsZeroOrNegativePollingInterval() {
        assertThatThrownBy(() -> new ProtectionSchedulerProperties(
                true,
                Duration.ZERO,
                50))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("pollInterval must be positive");
    }

    @Test
    void rejectsZeroBatchSize() {
        assertThatThrownBy(() -> new ProtectionSchedulerProperties(
                true,
                Duration.ofSeconds(1),
                0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("batchSize must be between 1 and 50");
    }

    @Test
    void rejectsBatchLargerThanApprovedV1Maximum() {
        assertThatThrownBy(() -> new ProtectionSchedulerProperties(
                true,
                Duration.ofSeconds(1),
                51))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("batchSize must be between 1 and 50");
    }
}
