package com.ofss.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ofss.services.ProtectedTransactionReleaseService;

@ExtendWith(MockitoExtension.class)
class ProtectedTransactionSchedulerTest {

    @Mock
    private ProtectedTransactionReleaseService releaseService;

    @Test
    void delegatesOneApprovedV1BatchPerTrigger() {
        ProtectionSchedulerProperties properties =
                new ProtectionSchedulerProperties(
                        true,
                        Duration.ofSeconds(1),
                        50);
        ProtectedTransactionScheduler scheduler =
                new ProtectedTransactionScheduler(
                        releaseService,
                        properties);

        scheduler.releaseExpiredTransactions();

        verify(releaseService).releaseDueTransactions(50);
    }

    @Test
    void doesNotAlterTheWorkerResultOrStartAnotherBatch() {
        ProtectionSchedulerProperties properties =
                new ProtectionSchedulerProperties(
                        true,
                        Duration.ofSeconds(1),
                        10);
        ProtectedTransactionScheduler scheduler =
                new ProtectedTransactionScheduler(
                        releaseService,
                        properties);
        when(releaseService.releaseDueTransactions(10))
                .thenReturn(7);

        scheduler.releaseExpiredTransactions();

        verify(releaseService).releaseDueTransactions(10);
        assertThat(properties.batchSize()).isEqualTo(10);
    }
}
