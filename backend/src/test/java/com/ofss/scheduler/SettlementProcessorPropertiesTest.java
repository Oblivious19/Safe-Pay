package com.ofss.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class SettlementProcessorPropertiesTest {

    @Test
    void acceptsApprovedEnabledV1Configuration() {
        SettlementProcessorProperties properties =
                properties(true, 25, 9001L, approvedDelays());

        assertThat(properties.enabled()).isTrue();
        assertThat(properties.pollInterval())
                .isEqualTo(Duration.ofSeconds(1));
        assertThat(properties.batchSize()).isEqualTo(25);
        assertThat(properties.requireOutboundClearingAccountId())
                .isEqualTo(9001L);
        assertThat(properties.maximumAutomaticRetries())
                .isEqualTo(3);
    }

    @Test
    void allowsDisabledProcessorWithoutPrematureClearingId() {
        SettlementProcessorProperties properties =
                properties(false, 25, null, approvedDelays());

        assertThat(properties.enabled()).isFalse();
        assertThatThrownBy(
                properties::requireOutboundClearingAccountId)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "outboundClearingAccountId is not configured");
    }

    @Test
    void allowsOperationalBatchBelowApprovedMaximum() {
        SettlementProcessorProperties properties =
                properties(false, 1, null, approvedDelays());

        assertThat(properties.batchSize()).isEqualTo(1);
    }

    @Test
    void rejectsPollingIntervalOtherThanOneSecond() {
        assertThatThrownBy(() ->
                new SettlementProcessorProperties(
                        false,
                        Duration.ofSeconds(2),
                        25,
                        null,
                        approvedDelays()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "pollInterval must be PT1S for SafePay V1");
    }

    @Test
    void rejectsBatchOutsideOneToTwentyFive() {
        assertThatThrownBy(() ->
                properties(false, 0, null, approvedDelays()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "batchSize must be between 1 and 25");

        assertThatThrownBy(() ->
                properties(false, 26, null, approvedDelays()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "batchSize must be between 1 and 25");
    }

    @Test
    void allowsEnabledDirectSettlementWithoutClearingId() {
        SettlementProcessorProperties properties =
                properties(true, 25, null, approvedDelays());
        assertThat(properties.enabled()).isTrue();
        assertThatThrownBy(() ->
                properties(true, 25, 0L, approvedDelays()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("outboundClearingAccountId must be positive");
    }

    @Test
    void enforcesExactApprovedRetrySchedule() {
        assertThatThrownBy(() ->
                properties(
                        false,
                        25,
                        null,
                        List.of(
                                Duration.ofSeconds(5),
                                Duration.ofSeconds(30),
                                Duration.ofMinutes(2))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "retryDelays must be PT5S, PT30S, PT1M");
    }

    @Test
    void exposesApprovedRetryDelayByOneBasedAttempt() {
        SettlementProcessorProperties properties =
                properties(false, 25, null, approvedDelays());

        assertThat(properties.retryDelayForAttempt(1))
                .isEqualTo(Duration.ofSeconds(5));
        assertThat(properties.retryDelayForAttempt(2))
                .isEqualTo(Duration.ofSeconds(30));
        assertThat(properties.retryDelayForAttempt(3))
                .isEqualTo(Duration.ofMinutes(1));

        assertThatThrownBy(() ->
                properties.retryDelayForAttempt(4))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("retryAttempt must be between 1 and 3");
    }

    @Test
    void defensivelyCopiesRetrySchedule() {
        ArrayList<Duration> mutableDelays =
                new ArrayList<>(approvedDelays());

        SettlementProcessorProperties properties =
                properties(false, 25, null, mutableDelays);

        mutableDelays.set(0, Duration.ofMinutes(9));

        assertThat(properties.retryDelays())
                .containsExactlyElementsOf(approvedDelays());
        assertThatThrownBy(() -> properties.retryDelays()
                .add(Duration.ofMinutes(2)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static SettlementProcessorProperties properties(
            boolean enabled,
            int batchSize,
            Long clearingAccountId,
            List<Duration> retryDelays) {

        return new SettlementProcessorProperties(
                enabled,
                Duration.ofSeconds(1),
                batchSize,
                clearingAccountId,
                retryDelays);
    }

    private static List<Duration> approvedDelays() {
        return List.of(
                Duration.ofSeconds(5),
                Duration.ofSeconds(30),
                Duration.ofMinutes(1));
    }
}
