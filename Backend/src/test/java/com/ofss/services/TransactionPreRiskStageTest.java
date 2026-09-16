package com.ofss.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import com.ofss.beans.*;
import com.ofss.repository.*;
import com.ofss.excp.TransactionValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;

class TransactionPreRiskStageTest {
    private UserDao users;
    private AccountDao accounts;
    private BeneficiaryDao beneficiaries;
    private TransactionDao transactions;
    private AuditLogDao audits;
    private RiskAssessmentEngine risk;
    private TransactionServiceImpl service;
    private User user;
    private Account account;
    private Beneficiary beneficiary;

    @BeforeEach
    void setup() {
        users = mock(UserDao.class); accounts = mock(AccountDao.class);
        beneficiaries = mock(BeneficiaryDao.class); transactions = mock(TransactionDao.class);
        audits = mock(AuditLogDao.class); risk = spy(new RiskAssessmentEngine());
        user = new User(); user.setUserId(103L); user.setEmail("owner@example.com");
        Role role = new Role(); role.setRoleName("CUSTOMER"); user.setRole(role);
        account = new Account(); account.setAccountId(1000001L); account.setUser(user);
        account.setBalance(new BigDecimal("200000.00"));
        beneficiary = new Beneficiary(); beneficiary.setBeneficiaryId(2001L); beneficiary.setAccount(account);
        beneficiary.setStatus("ACTIVE");
        when(users.findById(103L)).thenReturn(Optional.of(user));
        when(accounts.findForTransaction(1000001L, 103L)).thenReturn(Optional.of(account));
        when(beneficiaries.findByBeneficiaryIdAndAccountUserUserId(2001L, 103L)).thenReturn(Optional.of(beneficiary));
        when(transactions.pendingAmount(eq(1000001L), anyList())).thenReturn(BigDecimal.ZERO);
        when(transactions.currentDatabaseTime(1000001L)).thenReturn(java.time.LocalDateTime.of(2026, 9, 15, 12, 0));
        when(transactions.save(any())).thenAnswer(call -> { TransactionDb tx = call.getArgument(0); tx.setTransactionId(17L); return tx; });
        service = new TransactionServiceImpl(transactions, accounts,
                new TransactionPreRiskValidator(users, accounts, beneficiaries, transactions), audits, risk, null);
    }

    @ParameterizedTest
    @ValueSource(strings = {"missingUser", "nonCustomer", "suspended", "inactive", "locked", "foreignAccount",
            "blockedAccount", "closedAccount", "foreignBeneficiary", "inactiveBeneficiary", "zero", "negative",
            "fraction", "overflow", "nullAmount", "purpose", "unicodePurpose", "key", "blankKey", "balance", "minimum", "pending"})
    void rejectionAlwaysHappensBeforeRiskOrAnyWrite(String scenario) {
        BigDecimal amount = new BigDecimal("5000");
        String purpose = "Demo";
        String key = "test-key";
        switch (scenario) {
            case "missingUser" -> when(users.findById(103L)).thenReturn(Optional.empty());
            case "nonCustomer" -> user.getRole().setRoleName("ADMIN");
            case "suspended" -> user.setStatus(UserStatus.SUSPENDED);
            case "inactive" -> user.setStatus(UserStatus.INACTIVE);
            case "locked" -> user.setStatus(UserStatus.LOCKED);
            case "foreignAccount" -> when(accounts.findForTransaction(1000001L, 103L)).thenReturn(Optional.empty());
            case "blockedAccount" -> account.setStatus(AccountStatus.BLOCKED);
            case "closedAccount" -> account.setStatus(AccountStatus.CLOSED);
            case "foreignBeneficiary" -> when(beneficiaries.findByBeneficiaryIdAndAccountUserUserId(2001L, 103L)).thenReturn(Optional.empty());
            case "inactiveBeneficiary" -> beneficiary.setStatus("INACTIVE");
            case "zero" -> amount = BigDecimal.ZERO;
            case "negative" -> amount = new BigDecimal("-1");
            case "fraction" -> amount = new BigDecimal("1.001");
            case "overflow" -> amount = new BigDecimal("10000000000000000");
            case "nullAmount" -> amount = null;
            case "purpose" -> purpose = "X".repeat(256);
            case "unicodePurpose" -> purpose = "₹".repeat(86);
            case "key" -> key = "X".repeat(101);
            case "blankKey" -> key = " ";
            case "balance" -> account.setBalance(new BigDecimal("4000"));
            case "minimum" -> account.setBalance(new BigDecimal("9999.99"));
            case "pending" -> {
                account.setBalance(new BigDecimal("20000"));
                when(transactions.pendingAmount(eq(1000001L), anyList())).thenReturn(new BigDecimal("15000"));
            }
        }
        BigDecimal requested = amount;
        String requestedPurpose = purpose, requestedKey = key;
        BigDecimal original = account.getBalance();
        assertThrows(RuntimeException.class, () -> service.initiate(1000001L, 2001L, requested, requestedPurpose, requestedKey, 103L));
        verifyNoInteractions(risk, audits);
        verify(transactions, never()).save(any());
        verify(accounts, never()).save(any());
        assertEquals(original, account.getBalance());
    }

    @ParameterizedTest
    @CsvSource({"5000,LOW,SETTLED", "10000,LOW,SETTLED", "10001,MEDIUM,PROTECTED",
            "50001,HIGH,PROTECTED", "100001,VERY_HIGH,HARD_HOLD"})
    void successfulRequestUsesAmountRangesAndCorrectState(String value, RiskTier tier, TransactionState state) {
        BigDecimal amount = new BigDecimal(value);
        TransactionDb result = service.initiate(1000001L, 2001L, amount, "Demo", "test-key", 103L);
        assertEquals(tier, result.getRiskTier());
        assertEquals(state, result.getState());
        assertTrue(result.getTransactionRef().length() <= 50);
        assertNotNull(result.getRiskReason());
        assertEquals(tier == RiskTier.VERY_HIGH, result.isAuthenticationRequired());
        int seconds = tier == RiskTier.MEDIUM ? 10 : tier == RiskTier.HIGH ? 60 : 0;
        assertEquals(seconds, result.getProtectionSeconds());
        if (seconds == 0) assertNull(result.getProtectionExpiresAt());
        else assertEquals(result.getCreatedAt().plusSeconds(seconds), result.getProtectionExpiresAt());
        verify(risk).assessAmount(amount);
        verify(transactions, never()).settledPaymentsToBeneficiary(anyLong(), anyLong());
        verify(transactions, never()).recentSettledAmounts(anyLong(), any(), any());
        verify(transactions).save(result);
        verify(audits).save(any());
        verify(transactions).pendingAmount(1000001L, List.of(TransactionState.PROTECTED, TransactionState.HARD_HOLD));
        assertEquals(tier == RiskTier.LOW ? new BigDecimal("200000.00").subtract(amount) : new BigDecimal("200000.00"),
                account.getBalance());
    }

    @Test
    void exactMinimumAfterPendingAmountsIsAccepted() {
        account.setBalance(new BigDecimal("25000.00"));
        when(transactions.pendingAmount(eq(1000001L), anyList())).thenReturn(new BigDecimal("15000.00"));
        service.initiate(1000001L, 2001L, new BigDecimal("5000.00"), "X".repeat(255), "K".repeat(100), 103L);
        assertEquals(new BigDecimal("20000.00"), account.getBalance());
        verify(risk).assessAmount(new BigDecimal("5000.00"));
    }

    @Test
    void idempotentReplaySkipsNewBalanceChecksAndDoesNotRescoreOrDebit() {
        TransactionDb original = service.initiate(1000001L, 2001L, new BigDecimal("5000"), "Demo", "test-key", 103L);
        when(transactions.findByIdempotencyKey("test-key")).thenReturn(Optional.of(original));
        account.setBalance(new BigDecimal("5000.00"));
        account.setStatus(AccountStatus.BLOCKED);
        beneficiary.setStatus("INACTIVE");
        clearInvocations(risk, accounts, transactions, audits);
        assertSame(original, service.initiate(1000001L, 2001L, new BigDecimal("5000"), "Demo", "test-key", 103L));
        verifyNoInteractions(risk, audits);
        verify(accounts, never()).save(any());
        verify(transactions, never()).save(any());
        verify(transactions, never()).pendingAmount(anyLong(), anyList());
        assertEquals(new BigDecimal("5000.00"), account.getBalance());
    }

    @Test
    void changedPayloadForUsedKeyIsConflictBeforeRisk() {
        TransactionDb original = service.initiate(1000001L, 2001L, new BigDecimal("5000"), "Demo", "test-key", 103L);
        when(transactions.findByIdempotencyKey("test-key")).thenReturn(Optional.of(original));
        clearInvocations(risk, audits);
        var error = assertThrows(TransactionValidationException.class,
                () -> service.initiate(1000001L, 2001L, new BigDecimal("6000"), "Demo", "test-key", 103L));
        assertEquals(409, error.getStatus());
        verifyNoInteractions(risk, audits);
    }

    @Test
    void riskFailureNeverFallsBackToSettlementOrAnyWrite() {
        doThrow(new IllegalArgumentException("Invalid risk input")).when(risk).assessAmount(any());
        assertThrows(IllegalArgumentException.class, () -> service.initiate(
                1000001L, 2001L, new BigDecimal("5000"), "Demo", "test-key", 103L));
        verify(transactions, never()).save(any());
        verify(accounts, never()).save(any());
        verifyNoInteractions(audits);
    }

    @Test
    void replayPreservesAnExistingHardHoldRecordedUnderPreviousPolicy() {
        TransactionDb previous = new TransactionDb();
        previous.setFromAccount(account); previous.setBeneficiary(beneficiary);
        previous.setAmount(new BigDecimal("5000.00")); previous.setPurpose("Old payment");
        previous.setState(TransactionState.HARD_HOLD); previous.setRiskTier(RiskTier.VERY_HIGH);
        previous.setAuthenticationRequired(true); previous.setRiskReason("Previous recorded decision");
        when(transactions.findByIdempotencyKey("old-policy-key")).thenReturn(Optional.of(previous));
        var result = service.initiate(1000001L, 2001L, new BigDecimal("5000.00"), "Old payment", "old-policy-key", 103L);
        assertSame(previous, result);
        assertEquals(TransactionState.HARD_HOLD, result.getState());
        assertEquals("Previous recorded decision", result.getRiskReason());
        verifyNoInteractions(risk, audits);
        verify(accounts, never()).save(any());
        verify(transactions, never()).save(any());
    }
}
