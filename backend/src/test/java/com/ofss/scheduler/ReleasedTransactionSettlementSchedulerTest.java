package com.ofss.scheduler;

import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ofss.services.ReleasedTransactionSettlementProcessor;

@ExtendWith(MockitoExtension.class)
class ReleasedTransactionSettlementSchedulerTest {

    @Mock private ReleasedTransactionSettlementProcessor processor;

    @Test
    void delegatesExactlyOneApprovedBatchPerPoll() {
        SettlementProcessorProperties properties = properties(25);
        ReleasedTransactionSettlementScheduler scheduler =
                new ReleasedTransactionSettlementScheduler(processor, properties);

        scheduler.settleReleasedTransactions();

        verify(processor).processDueTransactions(25);
    }

    @Test
    void honorsConfiguredSmallerBatchWithoutStartingAnotherBatch() {
        SettlementProcessorProperties properties = properties(7);
        ReleasedTransactionSettlementScheduler scheduler =
                new ReleasedTransactionSettlementScheduler(processor, properties);

        scheduler.settleReleasedTransactions();

        verify(processor).processDueTransactions(7);
    }

    private static SettlementProcessorProperties properties(int batchSize) {
        return new SettlementProcessorProperties(
                true,
                Duration.ofSeconds(1),
                batchSize,
                99L,
                List.of(Duration.ofSeconds(5), Duration.ofSeconds(30), Duration.ofMinutes(1)));
    }
}
