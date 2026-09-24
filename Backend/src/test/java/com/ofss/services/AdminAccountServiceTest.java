package com.ofss.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.util.Optional;
import com.ofss.beans.*;
import com.ofss.beans.AdminAccountDtos.*;
import com.ofss.excp.TransactionValidationException;
import com.ofss.repository.AdminAccountRepository;
import com.ofss.repository.TransactionDao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AdminAccountServiceTest {
    private AdminAccountRepository accounts;
    private TransactionDao transactions;
    private AdminAccountService service;
    private Account account;
    @BeforeEach
    void setup() {
        accounts = mock(AdminAccountRepository.class); transactions = mock(TransactionDao.class);
        service = new AdminAccountService(accounts, transactions);
        User user = new User(); user.setUserId(7L);
        account = new Account(); account.setUser(user); account.setAccountId(11L);
        account.setAccountNumber("500000000011"); account.setBalance(new BigDecimal("20000.00"));
        account.setStatus(AccountStatus.BLOCKED);
        when(accounts.lockAccount(11L)).thenReturn(Optional.of(account));
        when(transactions.pendingAmount(eq(11L), anyList())).thenReturn(new BigDecimal("10000.00"));
        when(accounts.updateDetails(eq(11L), any(), any(), any())).thenAnswer(call -> {
            account.setBalance(call.getArgument(1)); account.setAccountType(call.getArgument(2));
            account.setUpdatedAt(call.getArgument(3)); return 1;
        });
    }
    @Test
    void balanceEqualToHoldsIsAcceptedWithoutChangingStatusOrOwner() {
        var result = service.update(11L, new Update(new BigDecimal("10000.00"), AccountType.CURRENT));
        assertEquals(new BigDecimal("10000.00"), result.balance());
        assertEquals(AccountType.CURRENT, result.accountType()); assertEquals(AccountStatus.BLOCKED, result.status());
        assertEquals(7L, result.userId()); assertEquals("500000000011", result.accountNumber());
        var order = inOrder(accounts, transactions);
        order.verify(accounts).lockAccount(11L);
        order.verify(transactions).pendingAmount(eq(11L), anyList());
        order.verify(accounts).updateDetails(eq(11L), any(), eq(AccountType.CURRENT), any());
        verify(accounts, never()).save(any());
    }
    @Test
    void reserveShortfallRejectsBalanceEdit() {
        assertThrows(TransactionValidationException.class, () -> service.update(11L,
                new Update(new BigDecimal("9999.99"), AccountType.SAVINGS)));
        verify(accounts, never()).updateDetails(anyLong(), any(), any(), any());
        assertEquals(new BigDecimal("20000.00"), account.getBalance());
    }
    @ParameterizedTest
    @ValueSource(strings = {"10000.001", "10000000000000000.00", "-10"})
    void incompatibleMoneyNeverReachesRepository(String value) {
        assertThrows(IllegalArgumentException.class, () -> service.update(11L,
                new Update(new BigDecimal(value), AccountType.SAVINGS)));
        verifyNoInteractions(accounts, transactions);
    }
    @Test
    void unavailableReservationsFailClosed() {
        when(transactions.pendingAmount(eq(11L), anyList())).thenReturn(null);
        assertThrows(TransactionValidationException.class, () -> service.update(11L,
                new Update(new BigDecimal("15000.00"), AccountType.SAVINGS)));
        verify(accounts, never()).updateDetails(anyLong(), any(), any(), any());
    }

    @Test
    void zeroBalanceIsAllowedWhenThereAreNoHeldPayments() {
        when(transactions.pendingAmount(eq(11L), anyList())).thenReturn(BigDecimal.ZERO);
        var result = service.update(11L, new Update(BigDecimal.ZERO, AccountType.SAVINGS));
        assertEquals(new BigDecimal("0.00"), result.balance());
    }
}
