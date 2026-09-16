package com.ofss.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import com.ofss.beans.*;
import com.ofss.excp.ResourceNotFoundExcp;
import com.ofss.excp.TransactionValidationException;
import com.ofss.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

class VerificationServiceTest {
    private VerificationRepository verifications;
    private AccountDao accounts;
    private UserDao users;
    private TransactionDao transactions;
    private AuditLogDao audits;
    private LoginService login;
    private VerificationService service;
    private User user;
    private Account account;
    private TransactionDb payment;

    @BeforeEach
    void setup() {
        verifications = mock(VerificationRepository.class);
        accounts = mock(AccountDao.class);
        users = mock(UserDao.class);
        transactions = mock(TransactionDao.class);
        audits = mock(AuditLogDao.class);
        login = mock(LoginService.class);
        service = new VerificationService(verifications, accounts, transactions, audits, login);
        user = new User(); user.setUserId(7L); user.setEmail("current@example.com");
        Role role = new Role(); role.setRoleName("CUSTOMER"); user.setRole(role);
        account = new Account(); account.setAccountId(11L); account.setUser(user);
        account.setBalance(new BigDecimal("40000.00"));
        Beneficiary beneficiary = new Beneficiary(); beneficiary.setBeneficiaryId(2L);
        beneficiary.setBeneficiaryName("Recipient"); beneficiary.setBankAccountNumber("1234"); beneficiary.setIfsc("HDFC0001234");
        payment = new TransactionDb(); payment.setTransactionId(21L); payment.setFromAccount(account);
        payment.setBeneficiary(beneficiary); payment.setAmount(new BigDecimal("10000.00"));
        payment.setState(TransactionState.HARD_HOLD); payment.setRiskTier(RiskTier.VERY_HIGH);
        payment.setAuthenticationRequired(true); payment.setVersion(3L); payment.setRiskReason("Unknown context");
        when(verifications.ownedAccountId(21L, 7L)).thenReturn(Optional.of(11L));
        when(login.verifyPassword(7L, "correct")).thenReturn(
                new LoginPrincipal(7L, "Owner", "current@example.com", "CUSTOMER", UserStatus.ACTIVE));
        when(accounts.findForTransaction(11L, 7L)).thenReturn(Optional.of(account));
        when(verifications.findByTransactionIdAndFromAccountUserUserId(21L, 7L)).thenReturn(Optional.of(payment));
        when(transactions.pendingAmount(eq(11L), anyList())).thenReturn(new BigDecimal("15000.00"));
        when(verifications.settleVerified(21L, 3L, "verify-1")).thenAnswer(call -> {
            payment.setState(TransactionState.SETTLED);
            payment.setVerificationIdempotencyKey("verify-1");
            payment.setVerifiedAt(LocalDateTime.of(2026, 9, 15, 10, 0));
            payment.setSettledAt(payment.getVerifiedAt());
            return 1;
        });
    }

    @Test
    void ownerPasswordSettlesOnceAndKeepsOtherHoldsCovered() {
        var result = service.verify(21L, 7L, "correct", "verify-1");
        assertEquals("SETTLED", result.state());
        assertNotNull(result.verifiedAt());
        assertEquals(new BigDecimal("30000.00"), account.getBalance());
        var order = inOrder(login, accounts, verifications, audits);
        order.verify(login).verifyPassword(7L, "correct");
        order.verify(accounts).findForTransaction(11L, 7L);
        order.verify(verifications).settleVerified(21L, 3L, "verify-1");
        order.verify(accounts).findForTransaction(11L, 7L);
        order.verify(accounts).save(account);
        order.verify(audits).save(argThat(audit -> "TRANSACTION_VERIFIED_SETTLED".equals(audit.getAction())
                && "HARD_HOLD".equals(audit.getOldState()) && "SETTLED".equals(audit.getNewState())
                && audit.getUserId().equals(7L)));
    }

    @Test
    void sameKeyReplaysWithoutAnotherDebitOrAuditEvenIfBalanceChanged() {
        service.verify(21L, 7L, "correct", "verify-1");
        when(verifications.findByVerificationIdempotencyKey("verify-1")).thenReturn(Optional.of(payment));
        account.setBalance(new BigDecimal("5000.00"));
        var result = service.verify(21L, 7L, "correct", "verify-1");
        assertEquals("SETTLED", result.state());
        assertEquals(new BigDecimal("5000.00"), account.getBalance());
        verify(verifications, times(1)).settleVerified(anyLong(), anyLong(), anyString());
        verify(accounts, times(1)).save(any());
        verify(audits, times(1)).save(any());
    }

    @Test
    void keyForAnotherPaymentIsConflictAndNeverDebits() {
        TransactionDb other = new TransactionDb(); other.setTransactionId(22L); other.setFromAccount(account);
        when(verifications.findByVerificationIdempotencyKey("verify-1")).thenReturn(Optional.of(other));
        assertEquals(409, assertThrows(TransactionValidationException.class,
                () -> service.verify(21L, 7L, "correct", "verify-1")).getStatus());
        verify(accounts, never()).save(any());
        verifyNoInteractions(audits);
    }

    @Test
    void differentOwnerCannotTriggerPasswordAttemptsOrMutations() {
        assertThrows(ResourceNotFoundExcp.class, () -> service.verify(21L, 8L, "correct", "verify-1"));
        verifyNoInteractions(users, login, accounts, transactions, audits);
    }

    @ParameterizedTest
    @EnumSource(value = TransactionState.class, names = {"CREATED", "AUTHORIZED", "RISK_ASSESSED", "PROTECTED", "CANCELLED", "SETTLED"})
    void nonHardHoldStateCannotSettle(TransactionState state) {
        payment.setState(state);
        assertEquals(409, assertThrows(TransactionValidationException.class,
                () -> service.verify(21L, 7L, "correct", "verify-1")).getStatus());
        verify(verifications, never()).settleVerified(anyLong(), anyLong(), anyString());
        verifyNoInteractions(transactions, audits);
    }

    @Test
    void blockedAccountCannotReleasePayment() {
        account.setStatus(AccountStatus.BLOCKED);
        assertThrows(TransactionValidationException.class, () -> service.verify(21L, 7L, "correct", "verify-1"));
        assertEquals(new BigDecimal("40000.00"), account.getBalance());
        verifyNoInteractions(transactions, audits);
    }

    @Test
    void settlementKeepsMinimumAndAllOtherPendingPaymentsCovered() {
        when(transactions.pendingAmount(eq(11L), anyList())).thenReturn(new BigDecimal("35000.01"));
        assertThrows(TransactionValidationException.class, () -> service.verify(21L, 7L, "correct", "verify-1"));
        verify(verifications, never()).settleVerified(anyLong(), anyLong(), anyString());
        verify(accounts, never()).save(any());
    }

    @Test
    void lostStateRaceCannotDebitOrAudit() {
        when(verifications.settleVerified(21L, 3L, "verify-1")).thenReturn(0);
        assertThrows(TransactionValidationException.class, () -> service.verify(21L, 7L, "correct", "verify-1"));
        assertEquals(new BigDecimal("40000.00"), account.getBalance());
        verify(accounts, never()).save(any());
        verifyNoInteractions(audits);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 4})
    void rejectedPasswordPersistsAttemptAndLockWithoutTouchingMoney(int attempts) {
        user.setFailedLoginAttempts(attempts);
        user.setPasswordHash(new BCryptPasswordEncoder().encode("correct"));
        when(users.findForVerificationById(7L)).thenReturn(Optional.of(user));
        var actual = new VerificationService(verifications, accounts, transactions, audits, new LoginService(users));
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        TransactionStatus status = mock(TransactionStatus.class);
        when(manager.getTransaction(any(TransactionDefinition.class))).thenReturn(status);
        TransactionInterceptor interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(manager);
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        ProxyFactory factory = new ProxyFactory(actual); factory.addAdvice(interceptor);
        VerificationService proxied = (VerificationService) factory.getProxy();
        if (attempts == 4) {
            assertThrows(LockedException.class, () -> proxied.verify(21L, 7L, "wrong", "verify-1"));
        } else {
            assertThrows(BadCredentialsException.class, () -> proxied.verify(21L, 7L, "wrong", "verify-1"));
        }
        assertEquals(attempts + 1, user.getFailedLoginAttempts());
        if (attempts == 4) { assertEquals(UserStatus.LOCKED, user.getStatus()); assertNotNull(user.getLockedUntil()); }
        verify(users).save(user);
        verify(manager).commit(status);
        verify(manager, never()).rollback(any());
        verifyNoInteractions(accounts, transactions, audits);
    }
}
