package com.ofss.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import com.ofss.beans.*;
import com.ofss.repository.UserDao;
import com.ofss.services.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class TransactionTimeContractTest {
    private final LocalDateTime databaseNow = LocalDateTime.of(2001, 2, 3, 4, 5, 6);
    private final LoginPrincipal caller = new LoginPrincipal(103L, "Owner", "owner@example.test", "CUSTOMER", UserStatus.ACTIVE);
    private TransactionService service;
    private UserDao users;
    private TransactionController controller;

    @BeforeEach
    void setup() {
        service = mock(TransactionService.class);
        users = mock(UserDao.class);
        controller = new TransactionController(service, users);
        User user = new User(); user.setUserId(caller.userId()); user.setEmail(caller.email());
        when(users.findById(caller.userId())).thenReturn(Optional.of(user));
    }

    private TransactionDb payment(long id, long accountId, TransactionState state, LocalDateTime expiry) {
        Account account = new Account(); account.setAccountId(accountId);
        Beneficiary beneficiary = new Beneficiary(); beneficiary.setBeneficiaryId(500L + id);
        beneficiary.setBeneficiaryName("Recipient"); beneficiary.setBankAccountNumber("1234567890"); beneficiary.setIfsc("TEST0000001");
        TransactionDb transaction = new TransactionDb(); transaction.setTransactionId(id);
        transaction.setFromAccount(account); transaction.setBeneficiary(beneficiary);
        transaction.setAmount(new BigDecimal("20000.00")); transaction.setRiskTier(RiskTier.MEDIUM);
        transaction.setState(state); transaction.setProtectionExpiresAt(expiry);
        return transaction;
    }

    @Test
    void detailUsesDatabaseRelativeDurationWithoutChangingStoredExpiryOrGuessingTimezone() {
        var expiry = databaseNow.plusSeconds(10).plusNanos(123_000_000);
        var payment = payment(1, 20, TransactionState.PROTECTED, expiry);
        when(service.getTransaction(1L, caller.email())).thenReturn(payment);
        when(service.currentDatabaseTime(20L)).thenReturn(databaseNow);
        var response = controller.getTransaction(1L, caller);
        assertEquals(10123L, response.get("protectionRemainingMillis"));
        assertEquals(true, response.get("canCancel"));
        assertEquals(expiry.toString(), response.get("protectionExpiresAt"));
        assertEquals(expiry, payment.getProtectionExpiresAt());
        verify(service).currentDatabaseTime(20L);
    }

    @Test
    void listReadsTheDatabaseClockOnceForAllProtectedPaymentsAcrossAccounts() {
        var first = payment(1, 20, TransactionState.PROTECTED, databaseNow.plusSeconds(5));
        var second = payment(2, 30, TransactionState.PROTECTED, databaseNow.plusSeconds(7));
        var settled = payment(3, 40, TransactionState.SETTLED, databaseNow.plusSeconds(9));
        when(service.getTransactions(null, caller.email())).thenReturn(List.of(first, second, settled));
        when(service.currentDatabaseTime(20L)).thenReturn(databaseNow);
        var response = controller.getTransactions(null, caller);
        assertEquals(5000L, response.get(0).get("protectionRemainingMillis"));
        assertEquals(7000L, response.get(1).get("protectionRemainingMillis"));
        assertNull(response.get(2).get("protectionRemainingMillis"));
        assertEquals(false, response.get(2).get("canCancel"));
        verify(service, times(1)).currentDatabaseTime(anyLong());
    }

    @Test
    void expiredProtectionNeverReturnsNegativeTimeOrCancellationAuthority() {
        when(service.currentDatabaseTime(20L)).thenReturn(databaseNow);
        for (LocalDateTime expiry : List.of(databaseNow, databaseNow.minusSeconds(3))) {
            when(service.getTransaction(1L, caller.email())).thenReturn(payment(1, 20, TransactionState.PROTECTED, expiry));
            var response = controller.getTransaction(1L, caller);
            assertEquals(0L, response.get("protectionRemainingMillis"));
            assertEquals(false, response.get("canCancel"));
        }
    }

    @ParameterizedTest
    @EnumSource(value = TransactionState.class, names = {"CREATED", "AUTHORIZED", "RISK_ASSESSED", "SETTLED", "CANCELLED", "HARD_HOLD"})
    void otherStatesNeverGetATimerOrCancellationEvenWithHistoricalExpiry(TransactionState state) {
        when(service.getTransaction(1L, caller.email())).thenReturn(payment(1, 20, state, databaseNow.plusDays(1)));
        var response = controller.getTransaction(1L, caller);
        assertNull(response.get("protectionRemainingMillis"));
        assertEquals(false, response.get("canCancel"));
        verify(service, never()).currentDatabaseTime(anyLong());
    }

    @Test
    void missingExpiryFailsClosedWithoutAClockRead() {
        when(service.getTransaction(1L, caller.email())).thenReturn(payment(1, 20, TransactionState.PROTECTED, null));
        var response = controller.getTransaction(1L, caller);
        assertNull(response.get("protectionRemainingMillis"));
        assertEquals(false, response.get("canCancel"));
        verify(service, never()).currentDatabaseTime(anyLong());
    }

    @Test
    void unavailableDatabaseClockDoesNotInventACancellationDeadline() {
        when(service.getTransaction(1L, caller.email())).thenReturn(payment(1, 20, TransactionState.PROTECTED, databaseNow.plusSeconds(5)));
        assertThrows(NullPointerException.class, () -> controller.getTransaction(1L, caller));
    }

    @Test
    void emptyOrTerminalListsDoNotReadDatabaseClock() {
        when(service.getTransactions(null, caller.email())).thenReturn(List.of());
        assertTrue(controller.getTransactions(null, caller).isEmpty());
        when(service.getTransactions(null, caller.email())).thenReturn(List.of(payment(1, 20, TransactionState.SETTLED, null)));
        assertEquals(false, controller.getTransactions(null, caller).get(0).get("canCancel"));
        verify(service, never()).currentDatabaseTime(anyLong());
    }

    @Test
    void createCancelAndVerificationResponsesUseTheSameAdditionalFields() {
        var pending = payment(1, 20, TransactionState.PROTECTED, databaseNow.plusSeconds(10));
        when(service.initiate(20L, 501L, pending.getAmount(), "rent", "create-key", 103L)).thenReturn(pending);
        when(service.currentDatabaseTime(20L)).thenReturn(databaseNow);
        var created = controller.initiate("create-key", new TransactionRequest(20L, 501L, pending.getAmount(), "rent"), caller);
        assertEquals(10000L, created.get("protectionRemainingMillis"));
        assertEquals(true, created.get("canCancel"));
        var cancelled = payment(1, 20, TransactionState.CANCELLED, pending.getProtectionExpiresAt());
        when(service.cancel(1L, "cancel-key", caller.email())).thenReturn(cancelled);
        var cancelResponse = controller.cancel(1L, "cancel-key", caller);
        assertNull(cancelResponse.get("protectionRemainingMillis"));
        assertEquals(false, cancelResponse.get("canCancel"));
        var verified = VerifiedTransactionResponse.from(payment(2, 20, TransactionState.SETTLED, null));
        assertNull(verified.protectionRemainingMillis());
        assertFalse(verified.canCancel());
        verify(service, times(1)).currentDatabaseTime(anyLong());
    }
}
