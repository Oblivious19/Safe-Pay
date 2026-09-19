package com.ofss.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ofss.repository.TransactionDao;

@ExtendWith(MockitoExtension.class)
class ReleasedTransactionSettlementProcessorTest {

    @Mock private TransactionDao transactionDao;
    @Mock private SettlementService settlementService;
    @Mock private SettlementFailureRecorder failureRecorder;
    private ReleasedTransactionSettlementProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new ReleasedTransactionSettlementProcessorImpl(
                transactionDao,
                settlementService,
                failureRecorder);
    }

    @Test
    void processesOneBoundedCandidateListAndCountsOnlySettledRows() {
        when(transactionDao.findDueSettlementTransactionIds(25)).thenReturn(List.of(1L, 2L, 3L));
        when(settlementService.settleIfReleased(1L, "SETTLEMENT-1")).thenReturn(SettlementAttemptOutcome.SETTLED);
        when(settlementService.settleIfReleased(2L, "SETTLEMENT-2")).thenReturn(SettlementAttemptOutcome.ALREADY_SETTLED);
        when(settlementService.settleIfReleased(3L, "SETTLEMENT-3")).thenReturn(SettlementAttemptOutcome.NOT_ELIGIBLE);

        assertThat(processor.processDueTransactions(25)).isEqualTo(1);
        verify(transactionDao).findDueSettlementTransactionIds(25);
    }

    @Test
    void recordsFailureSeparatelyAndContinuesTheBatch() {
        RuntimeException failure = new RuntimeException("temporary");
        when(transactionDao.findDueSettlementTransactionIds(2)).thenReturn(List.of(10L, 11L));
        when(settlementService.settleIfReleased(10L, "SETTLEMENT-10")).thenThrow(failure);
        when(settlementService.settleIfReleased(11L, "SETTLEMENT-11")).thenReturn(SettlementAttemptOutcome.SETTLED);

        assertThat(processor.processDueTransactions(2)).isEqualTo(1);
        verify(failureRecorder).recordFailure(10L, "SETTLEMENT-10", failure);
    }

    @Test
    void failureRecorderFailureDoesNotBlockLaterCandidates() {
        RuntimeException failure = new RuntimeException("temporary");
        when(transactionDao.findDueSettlementTransactionIds(2)).thenReturn(List.of(10L, 11L));
        when(settlementService.settleIfReleased(10L, "SETTLEMENT-10")).thenThrow(failure);
        when(failureRecorder.recordFailure(10L, "SETTLEMENT-10", failure)).thenThrow(new RuntimeException("recording"));
        when(settlementService.settleIfReleased(11L, "SETTLEMENT-11")).thenReturn(SettlementAttemptOutcome.SETTLED);

        assertThat(processor.processDueTransactions(2)).isEqualTo(1);
        verify(settlementService).settleIfReleased(11L, "SETTLEMENT-11");
    }

    @Test
    void emptyBacklogPerformsNoSettlementAttempt() {
        when(transactionDao.findDueSettlementTransactionIds(25)).thenReturn(List.of());

        assertThat(processor.processDueTransactions(25)).isZero();
        verify(settlementService, never()).settleIfReleased(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void rejectsBatchAboveApprovedMaximum() {
        assertThatThrownBy(() -> processor.processDueTransactions(26))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("batchSize must be between 1 and 25");
        verify(transactionDao, never()).findDueSettlementTransactionIds(26);
    }

    @Test
    void rejectsEmptyBatch() {
        assertThatThrownBy(() -> processor.processDueTransactions(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("batchSize must be between 1 and 25");
    }
}
