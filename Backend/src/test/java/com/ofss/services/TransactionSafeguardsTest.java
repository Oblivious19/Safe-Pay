package com.ofss.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import com.ofss.beans.*;
import com.ofss.excp.*;
import com.ofss.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TransactionSafeguardsTest {
    TransactionDao transactions = mock(TransactionDao.class);
    AccountDao accounts = mock(AccountDao.class);
    AuditLogDao audits = mock(AuditLogDao.class);
    ExpiredTransactionSettlementService worker = mock(ExpiredTransactionSettlementService.class);
    TransactionServiceImpl service = new TransactionServiceImpl(transactions, accounts,
            mock(TransactionPreRiskValidator.class), audits, new RiskAssessmentEngine(), worker);
    LocalDateTime now = LocalDateTime.of(2026, 9, 15, 12, 0);
    Account account;
    TransactionDb payment;

    @BeforeEach
    void setup() {
        User user = new User(); user.setUserId(1L); user.setEmail("owner@example.com");
        account = new Account(); account.setAccountId(2L); account.setUser(user);
        account.setBalance(new BigDecimal("30000.00"));
        payment = new TransactionDb(); payment.setTransactionId(3L); payment.setFromAccount(account);
        payment.setAmount(new BigDecimal("10000.00")); payment.setVersion(0L);
        payment.setState(TransactionState.PROTECTED); payment.setProtectionExpiresAt(now.plusSeconds(10));
        when(transactions.findOwnedAccountId(3L, user.getEmail())).thenReturn(Optional.of(2L));
        when(transactions.findAccountId(3L)).thenReturn(Optional.of(2L));
        when(accounts.findForSettlement(2L)).thenReturn(Optional.of(account));
        when(transactions.findById(3L)).thenReturn(Optional.of(payment));
        when(transactions.findByTransactionIdAndFromAccountUserEmail(3L, user.getEmail())).thenReturn(Optional.of(payment));
        when(transactions.currentDatabaseTime(2L)).thenReturn(now);
    }

    @Test void cancelReturnsReloadedStateAndDoesNotDebit() {
        TransactionDb saved = new TransactionDb(); saved.setTransactionId(3L); saved.setFromAccount(account);
        saved.setState(TransactionState.CANCELLED); saved.setCancelIdempotencyKey("cancel-1"); saved.setCancelledAt(now);
        when(transactions.findByTransactionIdAndFromAccountUserEmail(3L, "owner@example.com"))
                .thenReturn(Optional.of(payment), Optional.of(saved));
        when(transactions.cancelProtected(3L, 0L, "cancel-1")).thenReturn(1);
        assertSame(saved, service.cancel(3L, "cancel-1", "owner@example.com"));
        verify(accounts, never()).save(any());
        verify(audits).save(argThat(a -> a.getAction().equals("TRANSACTION_CANCELLED")));
    }

    @Test void cancellationReplayDoesNotWriteAgain() {
        payment.setState(TransactionState.CANCELLED);
        when(transactions.findByCancelIdempotencyKey("cancel-1")).thenReturn(Optional.of(payment));
        assertSame(payment, service.cancel(3L, "cancel-1", "owner@example.com"));
        verify(transactions, never()).cancelProtected(anyLong(), anyLong(), anyString());
        verifyNoInteractions(audits);
    }

    @Test void hardHoldCancellationAuditsTheActualStateWithoutRequiringATimer() {
        payment.setState(TransactionState.HARD_HOLD); payment.setProtectionExpiresAt(null);
        TransactionDb saved = new TransactionDb(); saved.setTransactionId(3L); saved.setFromAccount(account);
        saved.setState(TransactionState.CANCELLED); saved.setCancelIdempotencyKey("cancel-hold"); saved.setCancelledAt(now);
        when(transactions.findByTransactionIdAndFromAccountUserEmail(3L, "owner@example.com"))
                .thenReturn(Optional.of(payment), Optional.of(saved));
        when(transactions.cancelHeld(3L, 0L, "cancel-hold")).thenReturn(1);
        assertSame(saved, service.cancel(3L, "cancel-hold", "owner@example.com"));
        verify(transactions, never()).currentDatabaseTime(anyLong());
        verify(transactions, never()).cancelProtected(anyLong(), anyLong(), anyString());
        verify(accounts, never()).save(any());
        verify(audits).save(argThat(a -> "HARD_HOLD".equals(a.getOldState())
                && "CANCELLED".equals(a.getNewState()) && Long.valueOf(1).equals(a.getUserId())));
    }

    @Test void racingHardHoldUpdateCannotWriteACancellationAudit() {
        payment.setState(TransactionState.HARD_HOLD);
        when(transactions.cancelHeld(3L, 0L, "cancel-hold")).thenReturn(0);
        assertThrows(InvalidStateTransitionException.class, () -> service.cancel(3L, "cancel-hold", "owner@example.com"));
        verifyNoInteractions(audits);
    }

    @Test void keyReusedForDifferentCancellationIsConflict() {
        TransactionDb other = new TransactionDb(); other.setTransactionId(99L);
        when(transactions.findByCancelIdempotencyKey("cancel-1")).thenReturn(Optional.of(other));
        assertEquals(409, assertThrows(TransactionValidationException.class,
                () -> service.cancel(3L, "cancel-1", "owner@example.com")).getStatus());
        verifyNoInteractions(audits);
    }

    @Test void cancellationAtExactDatabaseDeadlineIsRejected() {
        payment.setProtectionExpiresAt(now);
        assertThrows(InvalidStateTransitionException.class, () -> service.cancel(3L, "cancel-1", "owner@example.com"));
        verify(transactions, never()).cancelProtected(anyLong(), anyLong(), anyString());
    }

    @Test void racingUpdateFailureDoesNotWriteAudit() {
        when(transactions.cancelProtected(3L, 0L, "cancel-1")).thenReturn(0);
        assertThrows(InvalidStateTransitionException.class, () -> service.cancel(3L, "cancel-1", "owner@example.com"));
        verifyNoInteractions(audits);
    }

    @Test void foreignTransactionDoesNotObtainAnAccountLock() {
        assertThrows(ResourceNotFoundExcp.class, () -> service.cancel(3L, "cancel-1", "other@example.com"));
        verify(accounts, never()).findForSettlement(anyLong());
    }

    @Test void oneFailedSettlementDoesNotPreventNextPayment() {
        when(transactions.findExpiredTransactionIds()).thenReturn(List.of(3L, 4L));
        when(worker.settle(3L)).thenThrow(new IllegalStateException("simulated database failure"));
        when(worker.settle(4L)).thenReturn(true);
        assertDoesNotThrow(service::releaseExpiredTransactions);
        verify(worker).settle(4L);
    }

    @Test void settlementPreservesOtherHoldsAndDebitsOnce() {
        payment.setProtectionExpiresAt(now);
        when(transactions.pendingAmount(eq(2L), anyList())).thenReturn(new BigDecimal("25000.00"));
        when(transactions.settleProtected(3L, 0L)).thenReturn(1);
        assertTrue(new ExpiredTransactionSettlementService(transactions, accounts, audits).settle(3L));
        assertEquals(new BigDecimal("20000.00"), account.getBalance());
        verify(accounts, times(2)).findForSettlement(2L);
        verify(audits).save(argThat(a -> a.getNewState().equals("SETTLED")));
    }

    @Test void settlementCannotConsumeMinimumOrOtherReservations() {
        payment.setProtectionExpiresAt(now);
        when(transactions.pendingAmount(eq(2L), anyList())).thenReturn(new BigDecimal("25000.01"));
        assertThrows(InsufficientBalanceException.class,
                () -> new ExpiredTransactionSettlementService(transactions, accounts, audits).settle(3L));
        verify(transactions, never()).settleProtected(anyLong(), anyLong());
        verify(accounts, never()).save(any());
    }

    @Test void settlementSkipsHardHoldEvenWithExpiredTimestamp() {
        payment.setState(TransactionState.HARD_HOLD); payment.setProtectionExpiresAt(now.minusDays(1));
        assertFalse(new ExpiredTransactionSettlementService(transactions, accounts, audits).settle(3L));
        verify(transactions, never()).settleProtected(anyLong(), anyLong());
    }
}
