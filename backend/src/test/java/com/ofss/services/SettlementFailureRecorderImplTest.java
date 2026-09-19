package com.ofss.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.ofss.beans.Account;
import com.ofss.beans.TransactionDb;
import com.ofss.beans.TransactionExceptionLog;
import com.ofss.beans.TransactionExceptionStatus;
import com.ofss.beans.TransactionProcessingStage;
import com.ofss.beans.TransactionState;
import com.ofss.repository.AccountDao;
import com.ofss.repository.TransactionDao;
import com.ofss.repository.TransactionExceptionDao;
import com.ofss.scheduler.SettlementProcessorProperties;

import jakarta.persistence.EntityManager;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SettlementFailureRecorderImplTest {

    private static final Long TRANSACTION_ID = 501L;
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-09-16T11:00:00Z");

    @Mock private TransactionDao transactionDao;
    @Mock private TransactionExceptionDao exceptionDao;
    @Mock private AccountDao accountDao;
    @Mock private TransactionStateService stateService;
    @Mock private TransactionLifecycleEvidenceService evidenceService;
    @Mock private EntityManager entityManager;
    @Mock private TransactionDb transaction;
    @Mock private Account source;
    @Mock private TransactionExceptionLog existing;

    private SettlementFailureRecorder recorder;

    @BeforeEach
    void setUp() {
        SettlementProcessorProperties properties = new SettlementProcessorProperties(
                true,
                Duration.ofSeconds(1),
                25,
                900L,
                List.of(Duration.ofSeconds(5), Duration.ofSeconds(30), Duration.ofMinutes(1)));
        recorder = new SettlementFailureRecorderImpl(
                transactionDao,
                exceptionDao,
                accountDao,
                stateService,
                properties,
                evidenceService,
                entityManager);
        when(transactionDao.findByIdForUpdate(TRANSACTION_ID)).thenReturn(Optional.of(transaction));
        when(transaction.getTransactionId()).thenReturn(TRANSACTION_ID);
        when(transaction.getState()).thenReturn(TransactionState.RELEASED);
        when(transactionDao.currentDatabaseTime()).thenReturn(NOW);
        when(exceptionDao.findFirstByTransaction_TransactionIdAndProcessingStageOrderByCreatedAtDesc(
                TRANSACTION_ID,
                TransactionProcessingStage.SETTLEMENT)).thenReturn(Optional.empty());
    }

    @Test
    void firstTemporaryFailureSchedulesRetryAfterFiveSeconds() {
        SettlementFailureDisposition disposition = recorder.recordFailure(
                TRANSACTION_ID,
                "SETTLEMENT-501",
                new RuntimeException("database unavailable"));

        assertThat(disposition).isEqualTo(SettlementFailureDisposition.RETRY_SCHEDULED);
        ArgumentCaptor<TransactionExceptionLog> captor = ArgumentCaptor.forClass(TransactionExceptionLog.class);
        verify(exceptionDao).saveAndFlush(captor.capture());
        TransactionExceptionLog exception = captor.getValue();
        assertThat(exception.getStatus()).isEqualTo(TransactionExceptionStatus.RETRY_PENDING);
        assertThat(exception.getRetryCount()).isEqualTo(1);
        assertThat(exception.getNextRetryAt()).isEqualTo(NOW.plusSeconds(5));
    }

    @Test
    void secondFailureSchedulesThirtySecondDelay() {
        existingRetry(1);

        assertThat(recorder.recordFailure(TRANSACTION_ID, "SETTLEMENT-501", new RuntimeException("again")))
                .isEqualTo(SettlementFailureDisposition.RETRY_SCHEDULED);
        verify(existing).scheduleRetry(2, NOW, NOW.plusSeconds(30));
        verify(exceptionDao).saveAndFlush(existing);
    }

    @Test
    void thirdScheduleUsesOneMinuteDelay() {
        existingRetry(2);

        assertThat(recorder.recordFailure(TRANSACTION_ID, "SETTLEMENT-501", new RuntimeException("again")))
                .isEqualTo(SettlementFailureDisposition.RETRY_SCHEDULED);
        verify(existing).scheduleRetry(3, NOW, NOW.plusMinutes(1));
    }

    @Test
    void failureOfThirdRetryRoutesToManualReviewWithoutFourthRetry() {
        existingRetry(3);

        assertThat(recorder.recordFailure(TRANSACTION_ID, "SETTLEMENT-501", new RuntimeException("exhausted")))
                .isEqualTo(SettlementFailureDisposition.MANUAL_REVIEW);
        verify(existing).moveToManualReview(3, NOW);
        verify(existing, never()).scheduleRetry(
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void manualReviewCandidateCannotBeRescheduled() {
        when(existing.getStatus()).thenReturn(TransactionExceptionStatus.MANUAL_REVIEW);
        when(exceptionDao.findFirstByTransaction_TransactionIdAndProcessingStageOrderByCreatedAtDesc(
                TRANSACTION_ID,
                TransactionProcessingStage.SETTLEMENT)).thenReturn(Optional.of(existing));

        assertThat(recorder.recordFailure(TRANSACTION_ID, "SETTLEMENT-501", new RuntimeException("ignored")))
                .isEqualTo(SettlementFailureDisposition.MANUAL_REVIEW);
        verify(exceptionDao, never()).saveAndFlush(existing);
    }

    @Test
    void definitiveInactiveSourceReleasesReservationAndFailsPayment() {
        when(transaction.getSourceAccount()).thenReturn(source);
        when(transaction.getAmount()).thenReturn(new BigDecimal("100.00"));
        when(transaction.getReservedAmount()).thenReturn(new BigDecimal("100.00"));
        when(source.getAccountId()).thenReturn(70L);
        when(source.getReservedAmount()).thenReturn(new BigDecimal("100.00"));
        when(accountDao.findByIdForUpdate(70L)).thenReturn(Optional.of(source));

        SettlementInvariantException failure = new SettlementInvariantException(
                "SOURCE_ACCOUNT_INACTIVE",
                "Source account is inactive");

        assertThat(recorder.recordFailure(TRANSACTION_ID, "SETTLEMENT-501", failure))
                .isEqualTo(SettlementFailureDisposition.DEFINITIVE_FAILURE);
        verify(source).releaseReservedFunds(new BigDecimal("100.00"), NOW);
        verify(transaction).endReservation(NOW);
        verify(stateService).transition(
                transaction,
                TransactionState.FAILED,
                "SOURCE_ACCOUNT_INACTIVE",
                NOW);
        verify(evidenceService).appendSystemEvent(
                org.mockito.ArgumentMatchers.eq(
                        TransactionLifecycleEvent.PAYMENT_FAILED),
                org.mockito.ArgumentMatchers.eq(transaction),
                org.mockito.ArgumentMatchers.eq(TransactionState.RELEASED),
                org.mockito.ArgumentMatchers.eq(TransactionState.FAILED),
                org.mockito.ArgumentMatchers.eq(
                        com.ofss.beans.AuditOutcome.FAILED),
                org.mockito.ArgumentMatchers.eq(
                        "SOURCE_ACCOUNT_INACTIVE"),
                org.mockito.ArgumentMatchers.eq(
                        new OperationContext("SETTLEMENT-501", null)),
                org.mockito.ArgumentMatchers.eq(
                        "SETTLEMENT-FINAL-FAILURE"),
                org.mockito.ArgumentMatchers.eq(NOW));
        verify(entityManager).flush();
    }

    @Test
    void candidateThatAlreadyLeftReleasedStateIsIgnored() {
        when(transaction.getState()).thenReturn(TransactionState.SETTLED);

        assertThat(recorder.recordFailure(TRANSACTION_ID, "SETTLEMENT-501", new RuntimeException("late")))
                .isEqualTo(SettlementFailureDisposition.IGNORED);
        verify(transactionDao, never()).currentDatabaseTime();
    }

    @Test
    void rejectsInvalidIdentityBeforeDatabaseAccess() {
        assertThatThrownBy(() -> recorder.recordFailure(0L, "SETTLEMENT-0", new RuntimeException()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("transactionId must be positive");
        verify(transactionDao, never()).findByIdForUpdate(0L);
    }

    private void existingRetry(int retryCount) {
        when(existing.getStatus()).thenReturn(TransactionExceptionStatus.RETRY_PENDING);
        when(existing.getRetryCount()).thenReturn(retryCount);
        when(exceptionDao.findFirstByTransaction_TransactionIdAndProcessingStageOrderByCreatedAtDesc(
                TRANSACTION_ID,
                TransactionProcessingStage.SETTLEMENT)).thenReturn(Optional.of(existing));
    }
}
