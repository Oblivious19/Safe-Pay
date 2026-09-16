package com.ofss.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import com.ofss.beans.User;
import com.ofss.repository.UserDao;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

class LoginAttemptTransactionTest {
    @ParameterizedTest
    @CsvSource({"0,false", "4,false", "0,true", "4,true"})
    void rejectedPasswordCommitsCounterAndLock(int initialCount, boolean phoneLogin) {
        UserDao users = mock(UserDao.class);
        User user = new User();
        user.setFailedLoginAttempts(initialCount);
        user.setPasswordHash(new BCryptPasswordEncoder().encode("Test@12345"));
        when(users.findForLoginByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(users.findForLoginByPhone("9876543210")).thenReturn(Optional.of(user));

        PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        TransactionStatus transaction = mock(TransactionStatus.class);
        when(transactions.getTransaction(any(TransactionDefinition.class))).thenReturn(transaction);
        TransactionInterceptor interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(transactions);
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        ProxyFactory factory = new ProxyFactory(new LoginService(users));
        factory.addAdvice(interceptor);
        LoginService service = (LoginService) factory.getProxy();
        org.junit.jupiter.api.function.Executable attempt = () -> {
            if (phoneLogin) service.loginByPhone("9876543210", "wrong");
            else service.login("test@example.com", "wrong");
        };

        if (initialCount == 4) {
            assertThrows(LockedException.class, attempt);
            assertEquals(com.ofss.beans.UserStatus.LOCKED, user.getStatus());
            assertNotNull(user.getLockedUntil());
        } else {
            assertThrows(BadCredentialsException.class, attempt);
        }
        assertEquals(initialCount + 1, user.getFailedLoginAttempts());
        verify(users).save(user);
        verify(transactions).commit(transaction);
        verify(transactions, never()).rollback(any());
    }
}
