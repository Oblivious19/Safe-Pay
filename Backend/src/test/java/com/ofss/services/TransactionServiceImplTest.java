package com.ofss.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import com.ofss.beans.Account;
import com.ofss.beans.AuditLog;
import com.ofss.beans.Beneficiary;
import com.ofss.beans.RiskTier;
import com.ofss.beans.TransactionDb;
import com.ofss.beans.TransactionState;
import com.ofss.beans.User;
import com.ofss.excp.IdempotencyConflictException;
import com.ofss.excp.InsufficientBalanceException;
import com.ofss.excp.InvalidStateTransitionException;
import com.ofss.repository.AccountDao;
import com.ofss.repository.AuditLogDao;
import com.ofss.repository.BeneficiaryDao;
import com.ofss.repository.TransactionDao;
import com.ofss.repository.UserDao;

@ExtendWith(MockitoExtension.class)
class TransactionServiceImplTest {

    private static final String EMAIL = "customer@example.com";
    private static final String PAYMENT_KEY = "payment-001";
    private static final String CANCEL_KEY = "cancel-001";
    private static final long ACCOUNT_ID = 1000001L;
    private static final long BENEFICIARY_ID = 1L;
    private static final long TRANSACTION_ID = 42L;
    private static final LocalDateTime DATABASE_TIME = LocalDateTime.of(2020, 1, 1, 12, 0);

    @Mock private TransactionDao transactionDao;
    @Mock private AccountDao accountDao;
    @Mock private BeneficiaryDao beneficiaryDao;
    @Mock private AuditLogDao auditLogDao;
    @Mock private UserDao userDao;
    @Mock private PlatformTransactionManager transactionManager;
    @Mock private TransactionStatus failedStatus;
    @Mock private TransactionStatus successfulStatus;

    private TransactionServiceImpl service;
    private User user;
    private Account account;
    private Beneficiary beneficiary;

    @BeforeEach
    void setUp() {
        service = new TransactionServiceImpl(transactionDao, accountDao, beneficiaryDao,
                auditLogDao, userDao, transactionManager);
        user = new User();
        user.setUserId(101L);
        user.setEmail(EMAIL);
        account = account(ACCOUNT_ID, "60000.00");
        beneficiary = new Beneficiary();
        beneficiary.setBeneficiaryId(BENEFICIARY_ID);
        beneficiary.setAccount(account);
        beneficiary.setStatus("ACTIVE");
    }

    @Test
    void pendingHoldsPreventSpendingTheSameFundsAgain() {
        prepareInitiation("60000.00", "40000.00");

        assertThrows(InsufficientBalanceException.class, () -> initiate("40000.00"));

        assertEquals(new BigDecimal("60000.00"), account.getBalance());
        verify(transactionDao, never()).saveAndFlush(any(TransactionDb.class));
        verify(accountDao, never()).save(any(Account.class));
        verifyNoInteractions(auditLogDao);
    }

    @Test
    void lowPaymentLocksAccountBeforeCheckingReservationsAndDebitsOnlyItsAmount() {
        prepareInitiation("60000.00", "40000.00");
        prepareSuccessfulInitiation();

        TransactionDb saved = initiate("10000");

        assertEquals(TransactionState.SETTLED, saved.getState());
        assertEquals(new BigDecimal("10000.00"), saved.getAmount());
        assertEquals(new BigDecimal("50000.00"), account.getBalance());
        assertEquals(DATABASE_TIME, saved.getSettledAt());
        InOrder locksAndDebit = inOrder(userDao, accountDao, transactionDao);
        locksAndDebit.verify(userDao).findByEmailForUpdate(EMAIL);
        locksAndDebit.verify(accountDao).findByAccountIdForUpdate(ACCOUNT_ID);
        locksAndDebit.verify(transactionDao).sumHeldAmount(ACCOUNT_ID);
        locksAndDebit.verify(accountDao).save(account);
    }

    @ParameterizedTest
    @CsvSource({"25000.00, MEDIUM, 10", "75000.00, HIGH, 60"})
    void protectedPaymentUsesDatabaseClockForItsWindow(String amount, RiskTier tier, int seconds) {
        prepareInitiation("200000.00", "0.00");
        prepareSuccessfulInitiation();

        TransactionDb saved = initiate(amount);

        assertEquals(TransactionState.PROTECTED, saved.getState());
        assertEquals(tier, saved.getRiskTier());
        assertEquals(DATABASE_TIME, saved.getCreatedAt());
        assertEquals(DATABASE_TIME, saved.getAuthorizedAt());
        assertEquals(DATABASE_TIME.plusSeconds(seconds), saved.getProtectionExpiresAt());
        assertFalse(saved.getRiskReason().isBlank());
        assertEquals(new BigDecimal("200000.00"), account.getBalance());
        verify(accountDao, never()).save(any(Account.class));
    }

    @Test
    void hardHoldRequiresAuthenticationAndDoesNotDebitAccount() {
        prepareInitiation("200000.00", "0.00");
        prepareSuccessfulInitiation();

        TransactionDb saved = initiate("150000.00");

        assertEquals(TransactionState.HARD_HOLD, saved.getState());
        assertEquals(RiskTier.HARD_HOLD, saved.getRiskTier());
        assertEquals("Y", saved.getAuthenticationRequired());
        assertNull(saved.getProtectionExpiresAt());
        assertNull(saved.getSettledAt());
        assertEquals(new BigDecimal("200000.00"), account.getBalance());
        verify(accountDao, never()).save(any(Account.class));
    }

    @Test
    void paymentReplayTreatsEquivalentAmountAndEmptyPurposeAsTheSameRequest() {
        TransactionDb existing = transaction(TRANSACTION_ID, account, "10000.00", TransactionState.SETTLED);
        when(userDao.findByEmailForUpdate(EMAIL)).thenReturn(Optional.of(user));
        when(transactionDao.findByIdempotencyKey(PAYMENT_KEY)).thenReturn(Optional.of(existing));

        TransactionDb replay = service.initiate(ACCOUNT_ID, BENEFICIARY_ID, new BigDecimal("10000.0"),
                "", PAYMENT_KEY, EMAIL);

        assertSame(existing, replay);
        verify(transactionDao, never()).saveAndFlush(any(TransactionDb.class));
        verifyNoInteractions(accountDao, beneficiaryDao, auditLogDao);
    }

    @Test
    void paymentKeyCannotBeReusedWithADifferentAmount() {
        TransactionDb existing = transaction(TRANSACTION_ID, account, "10000.00", TransactionState.SETTLED);
        when(userDao.findByEmailForUpdate(EMAIL)).thenReturn(Optional.of(user));
        when(transactionDao.findByIdempotencyKey(PAYMENT_KEY)).thenReturn(Optional.of(existing));

        assertThrows(IdempotencyConflictException.class, () -> initiate("10001.00"));

        verify(transactionDao, never()).saveAndFlush(any(TransactionDb.class));
        verifyNoInteractions(accountDao, beneficiaryDao, auditLogDao);
    }

    @Test
    void fractionalPaiseAreRejectedBeforeDatabaseAccess() {
        assertThrows(IllegalArgumentException.class, () -> initiate("10000.001"));

        verifyNoInteractions(userDao, accountDao, beneficiaryDao, transactionDao, auditLogDao);
    }

    @Test
    void cancellationPassesKeyToAtomicUpdateAndReturnsReloadedState() {
        TransactionDb protectedPayment = prepareCancellation();
        TransactionDb cancelled = transaction(TRANSACTION_ID, account, "25000.00", TransactionState.CANCELLED);
        cancelled.setCancelIdempotencyKey(CANCEL_KEY);
        cancelled.setCancelledAt(DATABASE_TIME.plusSeconds(5));
        when(transactionDao.findByTransactionIdAndFromAccountUserEmail(TRANSACTION_ID, EMAIL))
                .thenReturn(Optional.of(protectedPayment), Optional.of(cancelled));
        when(transactionDao.cancelProtected(TRANSACTION_ID, 3L, CANCEL_KEY)).thenReturn(1);

        TransactionDb result = service.cancel(TRANSACTION_ID, CANCEL_KEY, EMAIL);

        assertSame(cancelled, result);
        assertEquals(TransactionState.CANCELLED, result.getState());
        assertEquals(CANCEL_KEY, result.getCancelIdempotencyKey());
        verify(transactionDao).cancelProtected(TRANSACTION_ID, 3L, CANCEL_KEY);
        ArgumentCaptor<AuditLog> audit = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogDao).save(audit.capture());
        assertEquals("TRANSACTION_CANCELLED", audit.getValue().getAction());
        assertEquals(cancelled.getCancelledAt(), audit.getValue().getCreatedAt());
        verify(accountDao, never()).save(any(Account.class));
    }

    @Test
    void cancellationRejectedByDatabaseDeadlineDoesNotWriteAudit() {
        TransactionDb protectedPayment = prepareCancellation();
        when(transactionDao.findByTransactionIdAndFromAccountUserEmail(TRANSACTION_ID, EMAIL))
                .thenReturn(Optional.of(protectedPayment));
        when(transactionDao.cancelProtected(TRANSACTION_ID, 3L, CANCEL_KEY)).thenReturn(0);

        assertThrows(InvalidStateTransitionException.class,
                () -> service.cancel(TRANSACTION_ID, CANCEL_KEY, EMAIL));

        verifyNoInteractions(auditLogDao);
        verify(accountDao, never()).save(any(Account.class));
    }

    @Test
    void cancellationReplayDoesNotRepeatTransitionOrAudit() {
        TransactionDb cancelled = transaction(TRANSACTION_ID, account, "25000.00", TransactionState.CANCELLED);
        cancelled.setCancelIdempotencyKey(CANCEL_KEY);
        when(userDao.findByEmailForUpdate(EMAIL)).thenReturn(Optional.of(user));
        when(transactionDao.findByCancelIdempotencyKey(CANCEL_KEY)).thenReturn(Optional.of(cancelled));

        assertSame(cancelled, service.cancel(TRANSACTION_ID, CANCEL_KEY, EMAIL));

        verify(transactionDao, never()).cancelProtected(any(), any(), any());
        verifyNoInteractions(accountDao, auditLogDao);
    }

    @Test
    void failedSettlementRollsBackItsOwnTransactionAndDoesNotStopNextPayment() {
        account.setBalance(new BigDecimal("10000.00"));
        Account nextAccount = account(1000002L, "60000.00");
        Account reloadedAccount = account(1000002L, "60000.00");
        TransactionDb failedPayment = transaction(42L, account, "25000.00", TransactionState.PROTECTED);
        TransactionDb nextPayment = transaction(43L, nextAccount, "25000.00", TransactionState.PROTECTED);
        TransactionDb settledPayment = transaction(43L, reloadedAccount, "25000.00", TransactionState.SETTLED);
        settledPayment.setSettledAt(DATABASE_TIME);
        when(transactionDao.findExpiredTransactionIds()).thenReturn(List.of(42L, 43L));
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(failedStatus, successfulStatus);
        when(transactionDao.findAccountId(42L)).thenReturn(Optional.of(ACCOUNT_ID));
        when(transactionDao.findAccountId(43L)).thenReturn(Optional.of(1000002L));
        when(accountDao.findByAccountIdForUpdate(ACCOUNT_ID)).thenReturn(Optional.of(account));
        when(accountDao.findByAccountIdForUpdate(1000002L))
                .thenReturn(Optional.of(nextAccount), Optional.of(reloadedAccount));
        when(transactionDao.findById(42L)).thenReturn(Optional.of(failedPayment));
        when(transactionDao.findById(43L)).thenReturn(Optional.of(nextPayment), Optional.of(settledPayment));
        when(transactionDao.settleProtected(43L, 3L)).thenReturn(1);

        service.releaseExpiredTransactions();

        verify(transactionManager).rollback(failedStatus);
        verify(transactionManager).commit(successfulStatus);
        verify(transactionDao, never()).settleProtected(42L, 3L);
        verify(accountDao).save(reloadedAccount);
        assertEquals(new BigDecimal("35000.00"), reloadedAccount.getBalance());
        assertEquals(new BigDecimal("10000.00"), account.getBalance());
        ArgumentCaptor<TransactionDefinition> definitions = ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(transactionManager, times(2)).getTransaction(definitions.capture());
        definitions.getAllValues().forEach(definition -> assertEquals(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW, definition.getPropagationBehavior()));
        ArgumentCaptor<AuditLog> audit = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogDao).save(audit.capture());
        assertEquals(Long.valueOf(43L), audit.getValue().getTransactionId());
        assertEquals("TRANSACTION_AUTO_SETTLED", audit.getValue().getAction());
    }

    private void prepareInitiation(String balance, String reserved) {
        account.setBalance(new BigDecimal(balance));
        when(userDao.findByEmailForUpdate(EMAIL)).thenReturn(Optional.of(user));
        when(transactionDao.findByIdempotencyKey(PAYMENT_KEY)).thenReturn(Optional.empty());
        when(accountDao.findByAccountIdForUpdate(ACCOUNT_ID)).thenReturn(Optional.of(account));
        when(beneficiaryDao.findByBeneficiaryIdAndAccountUserEmail(BENEFICIARY_ID, EMAIL))
                .thenReturn(Optional.of(beneficiary));
        when(transactionDao.sumHeldAmount(ACCOUNT_ID)).thenReturn(new BigDecimal(reserved));
    }

    private void prepareSuccessfulInitiation() {
        when(transactionDao.currentDatabaseTime(ACCOUNT_ID)).thenReturn(DATABASE_TIME);
        when(transactionDao.saveAndFlush(any(TransactionDb.class))).thenAnswer(invocation -> {
            TransactionDb saved = invocation.getArgument(0);
            saved.setTransactionId(TRANSACTION_ID);
            return saved;
        });
    }

    private TransactionDb prepareCancellation() {
        when(userDao.findByEmailForUpdate(EMAIL)).thenReturn(Optional.of(user));
        when(transactionDao.findByCancelIdempotencyKey(CANCEL_KEY)).thenReturn(Optional.empty());
        when(transactionDao.findAccountId(TRANSACTION_ID)).thenReturn(Optional.of(ACCOUNT_ID));
        when(accountDao.findByAccountIdForUpdate(ACCOUNT_ID)).thenReturn(Optional.of(account));
        return transaction(TRANSACTION_ID, account, "25000.00", TransactionState.PROTECTED);
    }

    private TransactionDb initiate(String amount) {
        return service.initiate(ACCOUNT_ID, BENEFICIARY_ID, new BigDecimal(amount), null, PAYMENT_KEY, EMAIL);
    }

    private Account account(long id, String balance) {
        Account result = new Account();
        result.setAccountId(id);
        result.setUser(user);
        result.setBalance(new BigDecimal(balance));
        return result;
    }

    private TransactionDb transaction(long id, Account source, String amount, TransactionState state) {
        TransactionDb result = new TransactionDb();
        result.setTransactionId(id);
        result.setFromAccount(source);
        result.setBeneficiary(beneficiary);
        result.setAmount(new BigDecimal(amount));
        result.setState(state);
        result.setVersion(3L);
        result.setProtectionExpiresAt(DATABASE_TIME.plusSeconds(10));
        return result;
    }
}
