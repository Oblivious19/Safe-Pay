package com.ofss.services;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import com.ofss.beans.Account;
import com.ofss.beans.AccountType;
import com.ofss.beans.User;
import com.ofss.excp.BusinessRuleException;
import com.ofss.excp.ResourceNotFoundExcp;
import com.ofss.repository.AccountDao;

@ExtendWith(MockitoExtension.class)
class AccountFundsServiceImplTest {

    private static final Long OWNER_ID = 7L;
    private static final Long SOURCE_ID = 10L;
    private static final Long CLEARING_ID = 20L;

    private static final BigDecimal AMOUNT =
            new BigDecimal("250.00");

    private static final OffsetDateTime OCCURRED_AT =
            OffsetDateTime.of(
                    2026,
                    9,
                    14,
                    10,
                    30,
                    0,
                    0,
                    ZoneOffset.UTC);

    @Mock
    private AccountDao accountDao;

    @Mock
    private Account sourceAccount;

    @Mock
    private Account clearingAccount;

    @Mock
    private User owner;

    private AccountFundsServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AccountFundsServiceImpl(accountDao);
    }

    @Test
    void reservesFundsOnlyAfterLockingAndCheckingOwnership() {
        when(accountDao.findByIdForUpdate(SOURCE_ID))
                .thenReturn(Optional.of(sourceAccount));

        when(sourceAccount.getOwner())
                .thenReturn(owner);

        when(owner.getUserId())
                .thenReturn(OWNER_ID);

        service.reserveOwnedFunds(
                OWNER_ID,
                SOURCE_ID,
                AMOUNT,
                OCCURRED_AT);

        verify(accountDao)
                .findByIdForUpdate(SOURCE_ID);

        verify(sourceAccount)
                .reserveFunds(AMOUNT, OCCURRED_AT);
    }

    @Test
    void hidesAccountWhenOwnershipDoesNotMatch() {
        when(accountDao.findByIdForUpdate(SOURCE_ID))
                .thenReturn(Optional.of(sourceAccount));

        when(sourceAccount.getOwner())
                .thenReturn(owner);

        when(owner.getUserId())
                .thenReturn(99L);

        assertThrows(
                ResourceNotFoundExcp.class,
                () -> service.reserveOwnedFunds(
                        OWNER_ID,
                        SOURCE_ID,
                        AMOUNT,
                        OCCURRED_AT));

        verify(sourceAccount, never())
                .reserveFunds(AMOUNT, OCCURRED_AT);
    }

    @Test
    void rejectsReservationWhenAccountDoesNotExist() {
        when(accountDao.findByIdForUpdate(SOURCE_ID))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundExcp.class,
                () -> service.reserveOwnedFunds(
                        OWNER_ID,
                        SOURCE_ID,
                        AMOUNT,
                        OCCURRED_AT));
    }

    @Test
    void releasesExistingCustomerReservation() {
        when(accountDao.findByIdForUpdate(SOURCE_ID))
                .thenReturn(Optional.of(sourceAccount));

        when(sourceAccount.isCustomerOwnedAccount())
                .thenReturn(true);

        service.releaseReservedFunds(
                SOURCE_ID,
                AMOUNT,
                OCCURRED_AT);

        verify(sourceAccount)
                .releaseReservedFunds(
                        AMOUNT,
                        OCCURRED_AT);
    }

    @Test
    void settlementLocksAccountsInAscendingIdOrder() {
        when(accountDao.findByIdForUpdate(SOURCE_ID))
                .thenReturn(Optional.of(sourceAccount));

        when(accountDao.findByIdForUpdate(CLEARING_ID))
                .thenReturn(Optional.of(clearingAccount));

        configureValidSettlementAccounts();

        service.settleReservedFunds(
                CLEARING_ID,
                SOURCE_ID,
                AMOUNT,
                OCCURRED_AT);

        InOrder lockOrder = inOrder(accountDao);

        lockOrder.verify(accountDao)
                .findByIdForUpdate(SOURCE_ID);

        lockOrder.verify(accountDao)
                .findByIdForUpdate(CLEARING_ID);

        verify(clearingAccount)
                .consumeReservedFunds(
                        AMOUNT,
                        OCCURRED_AT);

        verify(sourceAccount)
                .creditSettlementFunds(
                        AMOUNT,
                        OCCURRED_AT);
    }

    @Test
    void doesNotMutateWhenSecondAccountIsMissing() {
        when(accountDao.findByIdForUpdate(SOURCE_ID))
                .thenReturn(Optional.of(sourceAccount));

        when(accountDao.findByIdForUpdate(CLEARING_ID))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundExcp.class,
                () -> service.settleReservedFunds(
                        SOURCE_ID,
                        CLEARING_ID,
                        AMOUNT,
                        OCCURRED_AT));

        verify(sourceAccount, never())
                .consumeReservedFunds(
                        AMOUNT,
                        OCCURRED_AT);

        verify(sourceAccount, never())
                .creditSettlementFunds(
                        AMOUNT,
                        OCCURRED_AT);
    }

    @Test
    void rejectsNonClearingSettlementDestination() {
        when(accountDao.findByIdForUpdate(SOURCE_ID))
                .thenReturn(Optional.of(sourceAccount));

        when(accountDao.findByIdForUpdate(CLEARING_ID))
                .thenReturn(Optional.of(clearingAccount));

        when(sourceAccount.isCustomerOwnedAccount())
                .thenReturn(true);

        when(sourceAccount.isActive())
                .thenReturn(true);

        when(clearingAccount.getAccountType())
                .thenReturn(AccountType.OPENING_BALANCE_CONTROL);

        assertThrows(
                BusinessRuleException.class,
                () -> service.settleReservedFunds(
                        SOURCE_ID,
                        CLEARING_ID,
                        AMOUNT,
                        OCCURRED_AT));

        verify(sourceAccount, never())
                .consumeReservedFunds(
                        AMOUNT,
                        OCCURRED_AT);

        verify(clearingAccount, never())
                .creditSettlementFunds(
                        AMOUNT,
                        OCCURRED_AT);
    }

    @Test
    void rejectsSameSourceAndClearingAccount() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.settleReservedFunds(
                        SOURCE_ID,
                        SOURCE_ID,
                        AMOUNT,
                        OCCURRED_AT));

        verifyNoInteractions(accountDao);
    }

    @Test
    void implementationHasWritableTransactionBoundary() {
        Transactional transactional =
                AccountFundsServiceImpl.class
                        .getAnnotation(Transactional.class);

        assertNotNull(transactional);
        assertFalse(transactional.readOnly());
    }

    private void configureValidSettlementAccounts() {
        /*
         * In the ascending-lock test, clearingAccount is deliberately
         * passed as the source and sourceAccount as the destination.
         */
        when(clearingAccount.isCustomerOwnedAccount())
                .thenReturn(true);

        when(clearingAccount.isActive())
                .thenReturn(true);

        when(sourceAccount.getAccountType())
                .thenReturn(AccountType.OUTBOUND_CLEARING);

        when(sourceAccount.isActive())
                .thenReturn(true);
    }
}