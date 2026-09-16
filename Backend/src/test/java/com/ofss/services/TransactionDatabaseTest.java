package com.ofss.services;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.*;
import com.ofss.beans.*;
import com.ofss.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:safepay-safeguards;MODE=Oracle;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.jpa.hibernate.ddl-auto=create-drop"})
@ActiveProfiles("local")
class TransactionDatabaseTest {
    @Autowired UserService users;
    @Autowired AccountDao accounts;
    @Autowired BeneficiaryDao beneficiaries;
    @Autowired TransactionDao transactions;
    @Autowired TransactionService service;
    @Autowired VerificationService verification;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    Account account;
    Beneficiary beneficiary;
    String email;

    @BeforeEach void setup() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        email = "db-" + suffix + "@example.com";
        User user = new User(); user.setName("Database test"); user.setEmail(email);
        user.setPhone(String.format("%010d", Math.abs(UUID.randomUUID().getLeastSignificantBits() % 9000000000L)));
        user.setPasswordHash("Payment#2026");
        User registered = users.register(user);
        account = accounts.findFirstByUserUserIdOrderByAccountId(registered.getUserId()).orElseThrow();
        account.setBalance(new BigDecimal("500000.00")); accounts.save(account);
        beneficiary = new Beneficiary(); beneficiary.setAccount(account); beneficiary.setBeneficiaryName("Recipient");
        beneficiary.setBankAccountNumber("1234567890"); beneficiary.setIfsc("SBIN0001234");
        beneficiary.setStatus("ACTIVE"); beneficiary.setCreatedAt(LocalDateTime.now());
        beneficiary = beneficiaries.save(beneficiary);
    }

    TransactionDb protectedPayment(LocalDateTime expires) {
        TransactionDb t = new TransactionDb(); t.setFromAccount(account); t.setBeneficiary(beneficiary);
        t.setTransactionRef("DB-" + UUID.randomUUID()); t.setIdempotencyKey(UUID.randomUUID().toString());
        t.setAmount(new BigDecimal("20000.00")); t.setState(TransactionState.PROTECTED);
        t.setRiskTier(RiskTier.MEDIUM); t.setRiskReason("Test protection"); t.setProtectionSeconds(10);
        t.setProtectionExpiresAt(expires); t.setCreatedAt(LocalDateTime.now());
        return transactions.save(t);
    }

    @Test void cancellationIsFreshAndConcurrentReplayWritesOneAudit() throws Exception {
        TransactionDb t = protectedPayment(LocalDateTime.now().plusMinutes(2));
        String key = UUID.randomUUID().toString();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<TransactionDb> one = pool.submit(() -> service.cancel(t.getTransactionId(), key, email));
            Future<TransactionDb> two = pool.submit(() -> service.cancel(t.getTransactionId(), key, email));
            assertEquals(TransactionState.CANCELLED, one.get(20, TimeUnit.SECONDS).getState());
            assertEquals(TransactionState.CANCELLED, two.get(20, TimeUnit.SECONDS).getState());
        } finally { pool.shutdownNow(); }
        assertEquals(1, jdbc.queryForObject("select count(*) from audit_log where transaction_id=? and action='TRANSACTION_CANCELLED'",
                Integer.class, t.getTransactionId()));
        assertEquals(0, accounts.findById(account.getAccountId()).orElseThrow().getBalance().compareTo(new BigDecimal("500000.00")));
    }

    @Test void databasePredicateRejectsExpiredCancellationEvenWithMatchingVersion() {
        TransactionDb t = protectedPayment(LocalDateTime.now().minusMinutes(1));
        int changed = new TransactionTemplate(manager).execute(status ->
                transactions.cancelProtected(t.getTransactionId(), t.getVersion(), UUID.randomUUID().toString()));
        assertEquals(0, changed);
    }

    @Test void realRiskEngineHardHoldRequiresPasswordAndReplayDoesNotDebitAgain() {
        TransactionDb t = service.initiate(account.getAccountId(), beneficiary.getBeneficiaryId(),
                new BigDecimal("110000.00"), "Verification test", UUID.randomUUID().toString(), account.getUserId());
        assertEquals(TransactionState.HARD_HOLD, t.getState());
        assertEquals(RiskTier.VERY_HIGH, t.getRiskTier());
        assertTrue(t.isAuthenticationRequired());
        assertNull(t.getProtectionExpiresAt());
        String key = UUID.randomUUID().toString();
        assertThrows(com.ofss.excp.ResourceNotFoundExcp.class,
                () -> verification.verify(t.getTransactionId(), account.getUserId() + 1000000L, "Payment#2026", key));
        assertThrows(org.springframework.security.authentication.BadCredentialsException.class,
                () -> verification.verify(t.getTransactionId(), account.getUserId(), "wrong", key));
        assertEquals(1, jdbc.queryForObject("select failed_login_attempts from users where user_id=?", Integer.class, account.getUserId()));
        assertEquals(TransactionState.HARD_HOLD, transactions.findById(t.getTransactionId()).orElseThrow().getState());
        assertEquals(0, accounts.findById(account.getAccountId()).orElseThrow().getBalance().compareTo(new BigDecimal("500000.00")));
        verification.verify(t.getTransactionId(), account.getUserId(), "Payment#2026", key);
        verification.verify(t.getTransactionId(), account.getUserId(), "Payment#2026", key);
        TransactionDb settled = transactions.findById(t.getTransactionId()).orElseThrow();
        assertEquals(TransactionState.SETTLED, settled.getState()); assertNotNull(settled.getVerifiedAt());
        assertEquals(0, accounts.findById(account.getAccountId()).orElseThrow().getBalance().compareTo(new BigDecimal("390000.00")));
        assertEquals(0, jdbc.queryForObject("select failed_login_attempts from users where user_id=?", Integer.class, account.getUserId()));
        assertEquals(1, jdbc.queryForObject("select count(*) from audit_log where transaction_id=? and action='TRANSACTION_VERIFIED_SETTLED'",
                Integer.class, t.getTransactionId()));
    }

    @Test void newBeneficiaryLowPaymentSettlesImmediatelyAndInitiationReplayDebitsOnce() {
        String key = UUID.randomUUID().toString();
        TransactionDb first = service.initiate(account.getAccountId(), beneficiary.getBeneficiaryId(),
                new BigDecimal("5000.00"), "New beneficiary low payment", key, account.getUserId());
        assertEquals(RiskTier.LOW, first.getRiskTier());
        assertEquals(TransactionState.SETTLED, first.getState());
        assertEquals(0, first.getProtectionSeconds());
        assertFalse(first.isAuthenticationRequired());
        assertNull(first.getProtectionExpiresAt());
        assertNotNull(first.getSettledAt());
        TransactionDb replay = service.initiate(account.getAccountId(), beneficiary.getBeneficiaryId(),
                new BigDecimal("5000.00"), "New beneficiary low payment", key, account.getUserId());
        assertEquals(first.getTransactionId(), replay.getTransactionId());
        assertEquals(0, accounts.findById(account.getAccountId()).orElseThrow().getBalance()
                .compareTo(new BigDecimal("495000.00")));
        assertEquals(1, jdbc.queryForObject("select count(*) from audit_log where transaction_id=? and action='TRANSACTION_INITIATED'",
                Integer.class, first.getTransactionId()));
    }

    @ParameterizedTest
    @CsvSource({
        "0.01,LOW,SETTLED,0,false",
        "10000.00,LOW,SETTLED,0,false",
        "10000.01,MEDIUM,PROTECTED,10,false",
        "50000.00,MEDIUM,PROTECTED,10,false",
        "50000.01,HIGH,PROTECTED,60,false",
        "100000.00,HIGH,PROTECTED,60,false",
        "100000.01,VERY_HIGH,HARD_HOLD,0,true"
    })
    void realDatabaseUsesExactAmountBoundariesWithoutHistorySignals(String value, RiskTier tier,
            TransactionState state, int seconds, boolean authenticationRequired) {
        // Assertions run while initiation's account lock is still held. The scheduler
        // cannot debit a protected payment mid-assertion, even on a slow test machine.
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            BigDecimal amount = new BigDecimal(value);
            TransactionDb payment = service.initiate(account.getAccountId(), beneficiary.getBeneficiaryId(),
                    amount, "Amount boundary", UUID.randomUUID().toString(), account.getUserId());
            transactions.flush();
            assertEquals(tier, payment.getRiskTier());
            assertEquals(state, payment.getState());
            assertEquals(seconds, payment.getProtectionSeconds());
            assertEquals(authenticationRequired, payment.isAuthenticationRequired());
            assertFalse(payment.getRiskReason().isBlank());
            assertEquals(tier.name(), jdbc.queryForObject("select risk_tier from transaction_db where transaction_id=?",
                    String.class, payment.getTransactionId()));
            BigDecimal expectedBalance = new BigDecimal("500000.00");
            if (state == TransactionState.SETTLED) expectedBalance = expectedBalance.subtract(amount);
            assertEquals(0, jdbc.queryForObject("select balance from account where account_id=?",
                    BigDecimal.class, account.getAccountId()).compareTo(expectedBalance));
            if (state == TransactionState.PROTECTED) {
                assertNotNull(payment.getProtectionExpiresAt());
                assertEquals(seconds, Duration.between(payment.getCreatedAt(), payment.getProtectionExpiresAt()).getSeconds());
            } else {
                assertNull(payment.getProtectionExpiresAt());
            }
        });
    }
}
