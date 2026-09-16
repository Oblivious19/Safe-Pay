package com.ofss.services;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ofss.beans.Account;
import com.ofss.beans.User;
import com.ofss.excp.AccountAlreadyExistsException;
import com.ofss.repository.AccountDao;
import com.ofss.repository.BeneficiaryDao;
import com.ofss.repository.TransactionDao;
import com.ofss.repository.UserDao;

@ExtendWith(MockitoExtension.class)
class AccountServiceImplTest {

    @Mock private AccountDao accountDao;
    @Mock private UserDao userDao;
    @Mock private BeneficiaryDao beneficiaryDao;
    @Mock private TransactionDao transactionDao;

    @InjectMocks private AccountServiceImpl accountService;

    @Test
    void rejectsCreatingASecondAccountForTheSameUser() {
        Long userId = 103L;
        Account requestedAccount = new Account();
        requestedAccount.setBalance(new BigDecimal("5000.00"));

        when(userDao.findById(userId)).thenReturn(Optional.of(new User()));
        when(accountDao.existsByUserUserId(userId)).thenReturn(true);

        assertThrows(AccountAlreadyExistsException.class,
                () -> accountService.createAccount(userId, requestedAccount));

        verify(accountDao, never()).save(any(Account.class));
    }
}
