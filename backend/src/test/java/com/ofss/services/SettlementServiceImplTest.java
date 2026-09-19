package com.ofss.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.ofss.beans.Account;
import com.ofss.beans.AccountType;
import com.ofss.beans.CurrencyCode;
import com.ofss.beans.LedgerEntry;
import com.ofss.beans.LedgerPosting;
import com.ofss.beans.LedgerPostingStatus;
import com.ofss.beans.TransactionDb;
import com.ofss.beans.TransactionState;
import com.ofss.beans.User;
import com.ofss.repository.AccountDao;
import com.ofss.repository.LedgerEntryDao;
import com.ofss.repository.LedgerPostingDao;
import com.ofss.repository.TransactionDao;
import com.ofss.scheduler.SettlementProcessorProperties;

import jakarta.persistence.EntityManager;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SettlementServiceImplTest {

    private static final Long TRANSACTION_ID = 101L;
    private static final Long SOURCE_ID = 20L;
    private static final Long CLEARING_ID = 90L;
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-09-16T10:00:00Z");

    @Mock private TransactionDao transactionDao;
    @Mock private AccountDao accountDao;
    @Mock private LedgerPostingDao postingDao;
    @Mock private LedgerEntryDao entryDao;
    @Mock private SettlementPostingFactory postingFactory;
    @Mock private SettlementEvidenceService evidenceService;
    @Mock private TransactionStateService stateService;
    @Mock private EntityManager entityManager;
    @Mock private TransactionDb transaction;
    @Mock private Account source;
    @Mock private Account clearing;
    @Mock private User customer;
    @Mock private LedgerPosting posting;
    @Mock private LedgerEntry debit;
    @Mock private LedgerEntry credit;

    private SettlementService service;

    @BeforeEach
    void setUp() {
        SettlementProcessorProperties properties = new SettlementProcessorProperties(
                true,
                java.time.Duration.ofSeconds(1),
                25,
                CLEARING_ID,
                List.of(
                        java.time.Duration.ofSeconds(5),
                        java.time.Duration.ofSeconds(30),
                        java.time.Duration.ofMinutes(1)));
        service = new SettlementServiceImpl(
                transactionDao,
                accountDao,
                postingDao,
                entryDao,
                postingFactory,
                evidenceService,
                stateService,
                properties,
                entityManager);
        validSettlement();
    }

    @Test
    void settlesBalancesLedgerStateAndEvidenceInOneCall() {
        SettlementAttemptOutcome outcome = service.settleIfReleased(TRANSACTION_ID, "CORR-101");

        assertThat(outcome).isEqualTo(SettlementAttemptOutcome.SETTLED);
        verify(source).consumeReservedFunds(new BigDecimal("125.00"), NOW);
        verify(clearing).creditSettlementFunds(new BigDecimal("125.00"), NOW);
        verify(transaction).endReservation(NOW);
        verify(stateService).transition(transaction, TransactionState.SETTLED, NOW);
        verify(posting).markPosted(NOW);
        verify(entryDao).saveAll(List.of(debit, credit));
        verify(evidenceService).appendSuccessfulSettlement(transaction, posting, "CORR-101", NOW);
        verify(entityManager, org.mockito.Mockito.times(2)).flush();
    }

    @Test
    void locksAccountsInAscendingIdentifierOrder() {
        service.settleIfReleased(TRANSACTION_ID, "CORR-101");

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(accountDao);
        order.verify(accountDao).findByIdForUpdate(SOURCE_ID);
        order.verify(accountDao).findByIdForUpdate(CLEARING_ID);
    }

    @Test
    void missingTransactionIsARepeatSafeNoOp() {
        when(transactionDao.findByIdForUpdate(TRANSACTION_ID)).thenReturn(Optional.empty());

        assertThat(service.settleIfReleased(TRANSACTION_ID, "CORR-101"))
                .isEqualTo(SettlementAttemptOutcome.NOT_FOUND);
        verify(accountDao, never()).findByIdForUpdate(SOURCE_ID);
    }

    @Test
    void nonReleasedTransactionIsNotEligible() {
        when(transaction.getState()).thenReturn(TransactionState.PROTECTED);

        assertThat(service.settleIfReleased(TRANSACTION_ID, "CORR-101"))
                .isEqualTo(SettlementAttemptOutcome.NOT_ELIGIBLE);
        verify(accountDao, never()).findByIdForUpdate(SOURCE_ID);
    }

    @Test
    void alreadySettledRequiresOnePostedPosting() {
        when(transaction.getState()).thenReturn(TransactionState.SETTLED);
        when(posting.getStatus()).thenReturn(LedgerPostingStatus.POSTED);
        when(postingDao.findByTransaction_TransactionId(TRANSACTION_ID))
                .thenReturn(Optional.of(posting));

        assertThat(service.settleIfReleased(TRANSACTION_ID, "CORR-101"))
                .isEqualTo(SettlementAttemptOutcome.ALREADY_SETTLED);
        verify(accountDao, never()).findByIdForUpdate(SOURCE_ID);
    }

    @Test
    void settledWithoutPostedLedgerIsRejectedAsInvariantViolation() {
        when(transaction.getState()).thenReturn(TransactionState.SETTLED);
        when(postingDao.findByTransaction_TransactionId(TRANSACTION_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.settleIfReleased(TRANSACTION_ID, "CORR-101"))
                .isInstanceOf(SettlementInvariantException.class)
                .hasMessageContaining("must have one posting");
    }

    @Test
    void releasedTransactionCannotHaveAPriorPosting() {
        when(postingDao.findByTransaction_TransactionId(TRANSACTION_ID))
                .thenReturn(Optional.of(posting));

        assertThatThrownBy(() -> service.settleIfReleased(TRANSACTION_ID, "CORR-101"))
                .isInstanceOf(SettlementInvariantException.class)
                .hasMessageContaining("already has a settlement posting");
        verify(accountDao, never()).findByIdForUpdate(SOURCE_ID);
    }

    @Test
    void rejectsIncompleteReservationBeforeCreatingLedger() {
        when(source.getReservedAmount()).thenReturn(new BigDecimal("100.00"));

        assertThatThrownBy(() -> service.settleIfReleased(TRANSACTION_ID, "CORR-101"))
                .isInstanceOf(SettlementInvariantException.class)
                .hasMessageContaining("complete source reservation");
        verify(postingFactory, never()).create(transaction, clearing, NOW);
    }

    private void validSettlement() {
        when(transactionDao.findByIdForUpdate(TRANSACTION_ID)).thenReturn(Optional.of(transaction));
        when(transaction.getState()).thenReturn(TransactionState.RELEASED);
        when(transaction.getTransactionId()).thenReturn(TRANSACTION_ID);
        when(transaction.getSourceAccount()).thenReturn(source);
        when(transaction.getCustomer()).thenReturn(customer);
        when(transaction.getAmount()).thenReturn(new BigDecimal("125.00"));
        when(transaction.getReservedAmount()).thenReturn(new BigDecimal("125.00"));
        when(transaction.getReservedAt()).thenReturn(NOW.minusMinutes(1));
        when(transaction.getCurrencyCode()).thenReturn(CurrencyCode.INR);
        when(customer.getUserId()).thenReturn(7L);

        when(source.getAccountId()).thenReturn(SOURCE_ID);
        when(source.getOwner()).thenReturn(customer);
        when(source.isCustomerOwnedAccount()).thenReturn(true);
        when(source.isActive()).thenReturn(true);
        when(source.getCurrencyCode()).thenReturn(CurrencyCode.INR);
        when(source.getReservedAmount()).thenReturn(new BigDecimal("125.00"));
        when(source.getCurrentBalance()).thenReturn(new BigDecimal("500.00"));

        when(clearing.getAccountId()).thenReturn(CLEARING_ID);
        when(clearing.getAccountType()).thenReturn(AccountType.OUTBOUND_CLEARING);
        when(clearing.isActive()).thenReturn(true);
        when(clearing.getCurrencyCode()).thenReturn(CurrencyCode.INR);

        when(postingDao.findByTransaction_TransactionId(TRANSACTION_ID)).thenReturn(Optional.empty());
        when(transactionDao.currentDatabaseTime()).thenReturn(NOW);
        when(accountDao.findByIdForUpdate(SOURCE_ID)).thenReturn(Optional.of(source));
        when(accountDao.findByIdForUpdate(CLEARING_ID)).thenReturn(Optional.of(clearing));
        SettlementPostingPair pair = org.mockito.Mockito.mock(SettlementPostingPair.class);
        when(pair.posting()).thenReturn(posting);
        when(pair.entriesInPostingOrder()).thenReturn(List.of(debit, credit));
        when(postingFactory.create(transaction, clearing, NOW)).thenReturn(pair);
    }
}
