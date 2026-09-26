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

class AdminApprovalServiceTest {
    private VerificationRepository verifications;
    private AccountDao accounts;
    private UserDao users;
    private TransactionDao transactions;
    private AuditLogDao audits;
    private LoginService login;
    private AdminApprovalService service;
    private CurrentSessionService sessions;
    private LoginPrincipal admin = new LoginPrincipal(70L, "Admin", "admin@example.test", "ADMIN", UserStatus.ACTIVE);
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
        sessions = mock(CurrentSessionService.class);
        when(sessions.isCurrent(admin)).thenReturn(true);
        service = new AdminApprovalService(verifications, accounts, transactions, audits, sessions);
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
        when(verifications.accountId(21L)).thenReturn(Optional.of(11L));
        when(login.verifyPassword(7L, "correct")).thenReturn(
                new LoginPrincipal(7L, "Owner", "current@example.com", "CUSTOMER", UserStatus.ACTIVE));
        when(accounts.findForSettlement(11L)).thenReturn(Optional.of(account));
        when(verifications.findByTransactionId(21L)).thenReturn(Optional.of(payment));
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
    void adminApprovalSettlesOnceAndKeepsOtherHoldsCovered() {
        var result = service.approve(21L, admin, "verify-1");
        assertEquals("SETTLED", result.state());
        assertNotNull(result.verifiedAt());
        assertEquals(new BigDecimal("30000.00"), account.getBalance());
        var order = inOrder(sessions, accounts, verifications, audits);
        order.verify(sessions).isCurrent(admin);
        order.verify(accounts).findForSettlement(11L);
        order.verify(verifications).settleVerified(21L, 3L, "verify-1");
        order.verify(accounts).findForSettlement(11L);
        order.verify(accounts).save(account);
        order.verify(audits).saveAndFlush(argThat(audit -> "ADMIN_APPROVED_SETTLED".equals(audit.getAction())
                && "HARD_HOLD".equals(audit.getOldState()) && "SETTLED".equals(audit.getNewState())
                && audit.getUserId().equals(70L) && "verify-1".equals(audit.getRequestKey())));
    }

    @Test
    void sameKeyReplaysWithoutAnotherDebitOrAuditEvenIfBalanceChanged() {
        service.approve(21L, admin, "verify-1");
        AuditLog receipt = new AuditLog(); receipt.setAction("ADMIN_APPROVED_SETTLED");
        receipt.setUserId(70L); receipt.setTransactionId(21L);
        when(audits.findByRequestKey("verify-1")).thenReturn(Optional.of(receipt));
        account.setBalance(new BigDecimal("5000.00"));
        var result = service.approve(21L, admin, "verify-1");
        assertEquals("SETTLED", result.state());
        assertEquals(new BigDecimal("5000.00"), account.getBalance());
        verify(verifications, times(1)).settleVerified(anyLong(), anyLong(), anyString());
        verify(accounts, times(1)).save(any());
        verify(audits, times(1)).saveAndFlush(any());
    }

    @Test
    void keyForAnotherPaymentIsConflictAndNeverDebits() {
        TransactionDb other = new TransactionDb(); other.setTransactionId(22L); other.setFromAccount(account);
        when(verifications.findByVerificationIdempotencyKey("verify-1")).thenReturn(Optional.of(other));
        assertEquals(409, assertThrows(TransactionValidationException.class,
                () -> service.approve(21L, admin, "verify-1")).getStatus());
        verify(accounts, never()).save(any());
        verify(audits, never()).saveAndFlush(any());
    }

    @Test
    void customerOrRevokedAdminCannotApprove() {
        var customer = new LoginPrincipal(7L, "Owner", "owner@test", "CUSTOMER", UserStatus.ACTIVE);
        assertEquals(403, assertThrows(TransactionValidationException.class,
                () -> service.approve(21L, customer, "verify-1")).getStatus());
        when(sessions.isCurrent(admin)).thenReturn(false);
        assertEquals(403, assertThrows(TransactionValidationException.class,
                () -> service.approve(21L, admin, "verify-1")).getStatus());
        verifyNoInteractions(accounts, transactions, audits);
    }

    @ParameterizedTest
    @EnumSource(value = TransactionState.class, names = {"CREATED", "AUTHORIZED", "RISK_ASSESSED", "PROTECTED", "CANCELLED", "SETTLED"})
    void nonHardHoldStateCannotSettle(TransactionState state) {
        payment.setState(state);
        assertEquals(409, assertThrows(TransactionValidationException.class,
                () -> service.approve(21L, admin, "verify-1")).getStatus());
        verify(verifications, never()).settleVerified(anyLong(), anyLong(), anyString());
        verifyNoInteractions(transactions); verify(audits, never()).saveAndFlush(any());
    }

    @Test
    void blockedAccountCannotReleasePayment() {
        account.setStatus(AccountStatus.BLOCKED);
        assertThrows(TransactionValidationException.class, () -> service.approve(21L, admin, "verify-1"));
        assertEquals(new BigDecimal("40000.00"), account.getBalance());
        verifyNoInteractions(transactions); verify(audits, never()).saveAndFlush(any());
    }

    @Test
    void settlementKeepsAllPendingPaymentsCovered() {
        when(transactions.pendingAmount(eq(11L), anyList())).thenReturn(new BigDecimal("40000.01"));
        assertThrows(TransactionValidationException.class, () -> service.approve(21L, admin, "verify-1"));
        verify(verifications, never()).settleVerified(anyLong(), anyLong(), anyString());
        verify(accounts, never()).save(any());
    }

    @Test
    void lostStateRaceCannotDebitOrAudit() {
        when(verifications.settleVerified(21L, 3L, "verify-1")).thenReturn(0);
        assertThrows(TransactionValidationException.class, () -> service.approve(21L, admin, "verify-1"));
        assertEquals(new BigDecimal("40000.00"), account.getBalance());
        verify(accounts, never()).save(any());
        verify(audits, never()).saveAndFlush(any());
    }

    @Test
    void anotherAdministratorCannotReplaySomebodyElsesKey() {
        AuditLog receipt = new AuditLog(); receipt.setAction("ADMIN_APPROVED_SETTLED");
        receipt.setUserId(99L); receipt.setTransactionId(21L);
        when(audits.findByRequestKey("verify-1")).thenReturn(Optional.of(receipt));
        assertEquals(409, assertThrows(TransactionValidationException.class,
                () -> service.approve(21L, admin, "verify-1")).getStatus());
        verify(accounts, never()).save(any());
    }

    @Test void declineCannotBePerformedByCustomerOrRevokedAdmin() {
        var customer = new LoginPrincipal(7L,"Owner","owner@test","CUSTOMER",UserStatus.ACTIVE);
        assertThrows(TransactionValidationException.class, () -> service.decline(21L, customer,"decline-1"));
        when(sessions.isCurrent(admin)).thenReturn(false);
        assertThrows(TransactionValidationException.class, () -> service.decline(21L, admin,"decline-1"));
        verifyNoInteractions(accounts,audits);
    }
    @ParameterizedTest
    @EnumSource(value=TransactionState.class,names={"CREATED","AUTHORIZED","RISK_ASSESSED","PROTECTED","CANCELLED","SETTLED"})
    void declineOnlyAcceptsHardHold(TransactionState state) {
        payment.setState(state);
        assertThrows(TransactionValidationException.class, () -> service.decline(21L,admin,"decline-1"));
        verify(verifications,never()).declineHeld(anyLong(),anyLong(),anyString());
        verify(accounts,never()).save(any());
    }
    @Test void declineRejectsAnotherActionOrActorsKey() {
        var receipt=new AuditLog();receipt.setAction("ADMIN_APPROVED_SETTLED");receipt.setTransactionId(21L);receipt.setUserId(70L);
        when(audits.findByRequestKey("used")).thenReturn(Optional.of(receipt));
        assertThrows(TransactionValidationException.class, () -> service.decline(21L,admin,"used"));
        receipt.setAction("ADMIN_DECLINED_CANCELLED");receipt.setUserId(99L);
        assertThrows(TransactionValidationException.class, () -> service.decline(21L,admin,"used"));
        verify(verifications,never()).declineHeld(anyLong(),anyLong(),anyString());
    }

}
