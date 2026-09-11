package com.ofss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;

import com.ofss.beans.Account;
import com.ofss.beans.User;
import com.ofss.repository.AccountDao;
import com.ofss.repository.BeneficiaryDao;
import com.ofss.repository.TransactionDao;
import com.ofss.repository.UserDao;
import com.ofss.services.AccountServiceImpl;

class SafePayApplicationTests {

    private final AccountDao accountDao = mock(AccountDao.class);
    private final UserDao userDao = mock(UserDao.class);
    private final BeneficiaryDao beneficiaryDao = mock(BeneficiaryDao.class);
    private final TransactionDao transactionDao = mock(TransactionDao.class);
    private final AccountServiceImpl service = new AccountServiceImpl(
            accountDao, userDao, beneficiaryDao, transactionDao);

    @Test
    void balanceUpdateCannotSpendMoneyReservedForPendingPayments() {
        Account saved = accountWithBalance("60000.00");
        when(accountDao.findByAccountIdForUpdate(1L)).thenReturn(Optional.of(saved));
        when(transactionDao.sumHeldAmount(1L)).thenReturn(new BigDecimal("40000.00"));

        assertThrows(IllegalArgumentException.class,
                () -> service.updateAccount(1L, accountWithBalance("44999.99")));

        assertEquals(new BigDecimal("60000.00"), saved.getBalance());
        verify(accountDao, never()).save(any(Account.class));
    }

    @Test
    void balanceUpdateAcceptsExactlyTheMinimumPlusPendingPayments() {
        Account saved = accountWithBalance("60000.00");
        when(accountDao.findByAccountIdForUpdate(1L)).thenReturn(Optional.of(saved));
        when(transactionDao.sumHeldAmount(1L)).thenReturn(new BigDecimal("40000.00"));
        when(accountDao.save(saved)).thenReturn(saved);

        Account result = service.updateAccount(1L, accountWithBalance("45000.000"));

        assertSame(saved, result);
        assertEquals(new BigDecimal("45000.00"), result.getBalance());
        InOrder order = inOrder(accountDao, transactionDao);
        order.verify(accountDao).findByAccountIdForUpdate(1L);
        order.verify(transactionDao).sumHeldAmount(1L);
        order.verify(accountDao).save(saved);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"4999.99", "5000.001", "10000000000000000.00"})
    void balanceUpdateRejectsMissingBelowMinimumOrInexactMoney(String amount) {
        assertThrows(IllegalArgumentException.class,
                () -> service.updateAccount(1L, accountWithBalance(amount)));

        verifyNoInteractions(accountDao, transactionDao);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"4999.99", "5000.001", "10000000000000000.00"})
    void accountCreationRejectsMissingBelowMinimumOrInexactMoney(String amount) {
        when(userDao.findById(7L)).thenReturn(Optional.of(new User()));

        assertThrows(IllegalArgumentException.class,
                () -> service.createAccount(7L, accountWithBalance(amount)));

        verify(accountDao, never()).save(any(Account.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"5000.00", "9999999999999999.99"})
    void accountCreationAcceptsBothDatabaseMoneyBoundaries(String amount) {
        Account requested = accountWithBalance(amount);
        when(userDao.findById(7L)).thenReturn(Optional.of(new User()));
        when(accountDao.save(requested)).thenReturn(requested);

        Account created = service.createAccount(7L, requested);

        assertEquals(new BigDecimal(amount), created.getBalance());
        verify(accountDao).save(requested);
    }

    private Account accountWithBalance(String amount) {
        Account account = new Account();
        account.setBalance(amount == null ? null : new BigDecimal(amount));
        return account;
    }

}
