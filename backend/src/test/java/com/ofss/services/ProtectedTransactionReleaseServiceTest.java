package com.ofss.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ofss.repository.TransactionDao;

@ExtendWith(MockitoExtension.class)
class ProtectedTransactionReleaseServiceTest {

    @Mock
    private TransactionDao transactionDao;

    @Mock
    private ProtectedTransactionReleaseWorker releaseWorker;

    private ProtectedTransactionReleaseService service;

    @BeforeEach
    void setUp() {
        service = new ProtectedTransactionReleaseServiceImpl(
                transactionDao,
                releaseWorker);
    }

    @Test
    void processesTheBoundedCandidateListInDatabaseOrder() {
        when(transactionDao.findExpiredProtectedTransactionIds(50))
                .thenReturn(List.of(11L, 22L, 33L));
        when(releaseWorker.releaseIfExpired(11L))
                .thenReturn(ProtectedTransactionReleaseOutcome.RELEASED);
        when(releaseWorker.releaseIfExpired(22L))
                .thenReturn(
                        ProtectedTransactionReleaseOutcome.NOT_ELIGIBLE);
        when(releaseWorker.releaseIfExpired(33L))
                .thenReturn(ProtectedTransactionReleaseOutcome.RELEASED);

        int released = service.releaseDueTransactions(50);

        assertThat(released).isEqualTo(2);
        InOrder order = inOrder(releaseWorker);
        order.verify(releaseWorker).releaseIfExpired(11L);
        order.verify(releaseWorker).releaseIfExpired(22L);
        order.verify(releaseWorker).releaseIfExpired(33L);
    }

    @Test
    void returnsZeroWithoutStartingWorkersWhenNoRowsAreDue() {
        when(transactionDao.findExpiredProtectedTransactionIds(50))
                .thenReturn(List.of());

        assertThat(service.releaseDueTransactions(50)).isZero();

        verifyNoInteractions(releaseWorker);
    }

    @Test
    void continuesWithLaterCandidatesAfterOneWorkerFails() {
        when(transactionDao.findExpiredProtectedTransactionIds(50))
                .thenReturn(List.of(11L, 22L, 33L));
        when(releaseWorker.releaseIfExpired(11L))
                .thenReturn(ProtectedTransactionReleaseOutcome.RELEASED);
        when(releaseWorker.releaseIfExpired(22L))
                .thenThrow(new IllegalStateException("fixture failure"));
        when(releaseWorker.releaseIfExpired(33L))
                .thenReturn(ProtectedTransactionReleaseOutcome.RELEASED);

        assertThat(service.releaseDueTransactions(50)).isEqualTo(2);

        InOrder order = inOrder(releaseWorker);
        order.verify(releaseWorker).releaseIfExpired(11L);
        order.verify(releaseWorker).releaseIfExpired(22L);
        order.verify(releaseWorker).releaseIfExpired(33L);
    }

    @Test
    void rejectsZeroBatchBeforeQueryingOracle() {
        assertThatThrownBy(() -> service.releaseDueTransactions(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("batchSize must be between 1 and 50");

        verifyNoInteractions(transactionDao, releaseWorker);
    }

    @Test
    void rejectsBatchAboveApprovedV1MaximumBeforeQueryingOracle() {
        assertThatThrownBy(() -> service.releaseDueTransactions(51))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("batchSize must be between 1 and 50");

        verifyNoInteractions(transactionDao, releaseWorker);
    }
}
