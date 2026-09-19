package com.ofss.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class NotificationDispatcherPropertiesTest {

    @Test
    void acceptsExactlyApprovedV1Policy() {
        NotificationDispatcherProperties properties = properties(25);

        assertThat(properties.pollInterval()).isEqualTo(Duration.ofSeconds(1));
        assertThat(properties.batchSize()).isEqualTo(25);
        assertThat(properties.maxAttempts()).isEqualTo(5);
        assertThat(properties.retryDelays()).containsExactly(
                Duration.ofSeconds(5),
                Duration.ofSeconds(30),
                Duration.ofMinutes(1),
                Duration.ofMinutes(5));
    }

    @Test
    void allowsOperationalBatchBelowApprovedMaximum() {
        assertThat(properties(1).batchSize()).isEqualTo(1);
    }

    @Test
    void rejectsBatchOutsideOneToTwentyFive() {
        assertThatThrownBy(() -> properties(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("batchSize must be between 1 and 25");
        assertThatThrownBy(() -> properties(26))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("batchSize must be between 1 and 25");
    }

    @Test
    void rejectsPollingDriftFromOneSecond() {
        assertThatThrownBy(() -> new NotificationDispatcherProperties(
                true, Duration.ofSeconds(2), 25, 5, delays()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("pollInterval must be PT1S for SafePay V1");
    }

    @Test
    void rejectsAttemptCountDriftFromFive() {
        assertThatThrownBy(() -> new NotificationDispatcherProperties(
                true, Duration.ofSeconds(1), 25, 4, delays()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("maxAttempts must be 5 for SafePay V1");
    }

    @Test
    void exposesFourOneBasedRetryDelays() {
        NotificationDispatcherProperties properties = properties(25);

        assertThat(properties.retryDelayAfterFailure(1))
                .isEqualTo(Duration.ofSeconds(5));
        assertThat(properties.retryDelayAfterFailure(4))
                .isEqualTo(Duration.ofMinutes(5));
        assertThatThrownBy(() ->
                properties.retryDelayAfterFailure(5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void defensivelyCopiesAndValidatesRetrySchedule() {
        ArrayList<Duration> mutable = new ArrayList<>(delays());
        NotificationDispatcherProperties properties =
                new NotificationDispatcherProperties(
                        true, Duration.ofSeconds(1), 25, 5, mutable);
        mutable.set(0, Duration.ofHours(1));

        assertThat(properties.retryDelays().getFirst())
                .isEqualTo(Duration.ofSeconds(5));
        assertThatThrownBy(() -> new NotificationDispatcherProperties(
                true,
                Duration.ofSeconds(1),
                25,
                5,
                List.of(
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(30),
                        Duration.ofMinutes(1),
                        Duration.ofMinutes(4))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("retryDelays must be PT5S, PT30S, PT1M, PT5M");
    }

    private static NotificationDispatcherProperties properties(int batchSize) {
        return new NotificationDispatcherProperties(
                true,
                Duration.ofSeconds(1),
                batchSize,
                5,
                delays());
    }

    private static List<Duration> delays() {
        return List.of(
                Duration.ofSeconds(5),
                Duration.ofSeconds(30),
                Duration.ofMinutes(1),
                Duration.ofMinutes(5));
    }
}
