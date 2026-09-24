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
    @Autowired AdminApprovalService approvals;
    @Autowired RoleDao roles;
    @Autowired UserDao userDao;
    LoginPrincipal admin;
    @BeforeEach void administrator() {
        Role role = roles.findByRoleName("ADMIN").orElseGet(() -> {
            Role created = new Role(); created.setRoleName("ADMIN"); return roles.save(created);
        });
        User actor = new User(); actor.setName("Admin"); actor.setEmail(UUID.randomUUID()+"@admin.test");
        actor.setPhone(UUID.randomUUID().toString().substring(0,10)); actor.setPasswordHash("unused-test-hash");
        actor.setRole(role); actor.setStatus(UserStatus.ACTIVE);
        actor.setCreatedAt(LocalDateTime.now()); actor.setUpdatedAt(LocalDateTime.now()); actor = userDao.save(actor);
        admin = new LoginPrincipal(actor.getUserId(),actor.getName(),actor.getEmail(),"ADMIN",UserStatus.ACTIVE);
    }
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

    @Test void realRiskEngineHardHoldRequiresAdminAndReplayDoesNotDebitAgain() {
        TransactionDb t = service.initiate(account.getAccountId(), beneficiary.getBeneficiaryId(),
                new BigDecimal("110000.00"), "Verification test", UUID.randomUUID().toString(), account.getUserId(), PaymentCategory.MEDICAL);
        assertEquals(TransactionState.HARD_HOLD, t.getState());
        assertEquals(RiskTier.VERY_HIGH, t.getRiskTier());
        assertTrue(t.isAuthenticationRequired());
        assertNull(t.getProtectionExpiresAt());
        String key = UUID.randomUUID().toString();
        assertThrows(com.ofss.excp.TransactionValidationException.class,
                () -> verification.verify(t.getTransactionId(), account.getUserId() + 1000000L, "Payment#2026", key));
        assertThrows(com.ofss.excp.TransactionValidationException.class,
                () -> verification.verify(t.getTransactionId(), account.getUserId(), "wrong", key));
        assertEquals(0, jdbc.queryForObject("select failed_login_attempts from users where user_id=?", Integer.class, account.getUserId()));
        assertEquals(TransactionState.HARD_HOLD, transactions.findById(t.getTransactionId()).orElseThrow().getState());
        assertEquals(0, accounts.findById(account.getAccountId()).orElseThrow().getBalance().compareTo(new BigDecimal("500000.00")));
        approvals.approve(t.getTransactionId(), admin, key);
        approvals.approve(t.getTransactionId(), admin, key);
        TransactionDb settled = transactions.findById(t.getTransactionId()).orElseThrow();
        assertEquals(TransactionState.SETTLED, settled.getState()); assertNotNull(settled.getVerifiedAt());
        assertEquals(0, accounts.findById(account.getAccountId()).orElseThrow().getBalance().compareTo(new BigDecimal("390000.00")));
        assertEquals(0, jdbc.queryForObject("select failed_login_attempts from users where user_id=?", Integer.class, account.getUserId()));
        assertEquals(1, jdbc.queryForObject("select count(*) from audit_log where transaction_id=? and action='ADMIN_APPROVED_SETTLED'",
                Integer.class, t.getTransactionId()));
    }

    @Test void concurrentAdminApprovalsDebitOnceAndNotifyFromDatabase() throws Exception {
        TransactionDb t = service.initiate(account.getAccountId(), beneficiary.getBeneficiaryId(),
                new BigDecimal("120000.00"), "Concurrent approval", UUID.randomUUID().toString(), account.getUserId(), PaymentCategory.MEDICAL);
        assertTrue(approvals.pending(admin).stream().anyMatch(row -> row.transactionId().equals(t.getTransactionId())));
        String key = UUID.randomUUID().toString();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<VerifiedTransactionResponse> one = pool.submit(() -> approvals.approve(t.getTransactionId(), admin, key));
            Future<VerifiedTransactionResponse> two = pool.submit(() -> approvals.approve(t.getTransactionId(), admin, key));
            assertEquals("SETTLED", one.get(20, TimeUnit.SECONDS).state());
            assertEquals("SETTLED", two.get(20, TimeUnit.SECONDS).state());
        } finally { pool.shutdownNow(); }
        assertEquals(new BigDecimal("380000.00"), accounts.findById(account.getAccountId()).orElseThrow().getBalance());
        assertEquals(1, jdbc.queryForObject("select count(*) from audit_log where transaction_id=? and action='ADMIN_APPROVED_SETTLED' and user_id=?",
                Integer.class, t.getTransactionId(), admin.userId()));
        assertFalse(approvals.pending(admin).stream().anyMatch(row -> row.transactionId().equals(t.getTransactionId())));
        assertThrows(com.ofss.excp.TransactionValidationException.class,
                () -> approvals.approve(t.getTransactionId(), admin, UUID.randomUUID().toString()));
    }

    @Test void competingAdminsWithDifferentKeysCannotDebitTwice() throws Exception {
        TransactionDb t = service.initiate(account.getAccountId(), beneficiary.getBeneficiaryId(),
                new BigDecimal("120000.00"), "Competing admins", UUID.randomUUID().toString(), account.getUserId(), PaymentCategory.MEDICAL);
        LoginPrincipal firstAdmin = admin; administrator(); LoginPrincipal secondAdmin = admin;
        ExecutorService pool = Executors.newFixedThreadPool(2);
        java.util.concurrent.Callable<Boolean> first = () -> {
            try { approvals.approve(t.getTransactionId(), firstAdmin, UUID.randomUUID().toString()); return true; }
            catch (com.ofss.excp.TransactionValidationException e) { assertEquals(409,e.getStatus()); return false; }
        };
        java.util.concurrent.Callable<Boolean> second = () -> {
            try { approvals.approve(t.getTransactionId(), secondAdmin, UUID.randomUUID().toString()); return true; }
            catch (com.ofss.excp.TransactionValidationException e) { assertEquals(409,e.getStatus()); return false; }
        };
        try {
            var one = pool.submit(first); var two = pool.submit(second);
            assertNotEquals(one.get(20,TimeUnit.SECONDS), two.get(20,TimeUnit.SECONDS));
        } finally { pool.shutdownNow(); }
        assertEquals(new BigDecimal("380000.00"),accounts.findById(account.getAccountId()).orElseThrow().getBalance());
        assertEquals(1,jdbc.queryForObject("select count(*) from audit_log where transaction_id=? and action='ADMIN_APPROVED_SETTLED'",Integer.class,t.getTransactionId()));
    }

    @Test void failedAuditRollsBackApprovalAndDebit() {
        TransactionDb t = service.initiate(account.getAccountId(), beneficiary.getBeneficiaryId(),
                new BigDecimal("120000.00"), "Rollback approval", UUID.randomUUID().toString(), account.getUserId(), PaymentCategory.MEDICAL);
        jdbc.execute("alter table audit_log add constraint approval_audit_failure check (transaction_id <> "
                + t.getTransactionId() + " or action <> 'ADMIN_APPROVED_SETTLED')");
        try {
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                    () -> approvals.approve(t.getTransactionId(), admin, UUID.randomUUID().toString()));
        } finally { jdbc.execute("alter table audit_log drop constraint approval_audit_failure"); }
        assertEquals(TransactionState.HARD_HOLD, transactions.findById(t.getTransactionId()).orElseThrow().getState());
        assertEquals(new BigDecimal("500000.00"), accounts.findById(account.getAccountId()).orElseThrow().getBalance());
        assertEquals(0, jdbc.queryForObject("select count(*) from audit_log where transaction_id=? and action='ADMIN_APPROVED_SETTLED'", Integer.class,t.getTransactionId()));
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
        "50000.01,HIGH,PROTECTED,30,false",
        "100000.00,HIGH,PROTECTED,30,false",
        "100000.01,VERY_HIGH,HARD_HOLD,0,true"
    })
    void realDatabaseUsesExactAmountBoundariesWithoutHistorySignals(String value, RiskTier tier,
            TransactionState state, int seconds, boolean authenticationRequired) {
        // Assertions run while initiation's account lock is still held. The scheduler
        // cannot debit a protected payment mid-assertion, even on a slow test machine.
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            BigDecimal amount = new BigDecimal(value);
            TransactionDb payment = service.initiate(account.getAccountId(), beneficiary.getBeneficiaryId(),
                    amount, "Amount boundary", UUID.randomUUID().toString(), account.getUserId(),
                    PaymentCategory.appliesTo(amount) ? PaymentCategory.MEDICAL : null);
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

    @Test void categoryPersistsAndPendingQueueUsesPriorityThenFifoIncludingLegacyRows() {
        account.setBalance(new BigDecimal("2000000.00")); accounts.saveAndFlush(account);
        var ids = new java.util.ArrayList<Long>();
        var categories = new PaymentCategory[]{PaymentCategory.OTHERS, PaymentCategory.INVESTMENTS,
                PaymentCategory.FRIENDS_FAMILY, PaymentCategory.LOAN, PaymentCategory.MEDICAL, PaymentCategory.MEDICAL, PaymentCategory.LOAN};
        for (var category : categories) {
            var row = service.initiate(account.getAccountId(), beneficiary.getBeneficiaryId(), new BigDecimal("100001.00"),
                    "Test category", UUID.randomUUID().toString(), account.getUserId(), category);
            ids.add(row.getTransactionId());
            assertEquals(category.name(), jdbc.queryForObject("select payment_category from transaction_db where transaction_id=?", String.class, row.getTransactionId()));
            jdbc.update("update transaction_db set created_at=? where transaction_id=?", LocalDateTime.of(2026, 1, 1, 12, 0), row.getTransactionId());
        }
        // A legacy row has no category; it remains reviewable and follows named categories.
        jdbc.update("update transaction_db set payment_category=null where transaction_id=?", ids.get(6));
        var queue = approvals.pending(admin).stream().filter(row -> ids.contains(row.transactionId())).toList();
        assertEquals(java.util.List.of(ids.get(4), ids.get(5), ids.get(3), ids.get(2), ids.get(1), ids.get(0), ids.get(6)),
                queue.stream().map(AdminApprovalRequestView::transactionId).toList());
        assertEquals(PaymentCategory.MEDICAL, queue.get(0).category());
        assertNull(queue.get(6).category());
        // Earlier creation wins even when its transaction ID is larger.
        jdbc.update("update transaction_db set created_at=? where transaction_id=?", LocalDateTime.of(2025, 12, 31, 12, 0), ids.get(5));
        assertEquals(ids.get(5), approvals.pending(admin).stream().filter(row -> ids.contains(row.transactionId())).findFirst().orElseThrow().transactionId());
        assertNull(approvals.approve(ids.get(6), admin, UUID.randomUUID().toString()).category());
    }

    TransactionDb hardHold(String amount) {
        return service.initiate(account.getAccountId(), beneficiary.getBeneficiaryId(), new BigDecimal(amount),
                "medical", UUID.randomUUID().toString(), account.getUserId(), PaymentCategory.MEDICAL);
    }
    @Autowired AccountFundsRepository funds;
    @Test void multipleHardHoldsAreAllowedAndDeclineFreesOnlyItsReservation() {
        TransactionDb first = hardHold("200000.00"), second = hardHold("150000.00");
        assertEquals(TransactionState.HARD_HOLD, first.getState());
        assertEquals(TransactionState.HARD_HOLD, second.getState());
        assertEquals("medical", approvals.pending(admin).stream().filter(p -> p.transactionId().equals(first.getTransactionId())).findFirst().orElseThrow().purpose());
        var before = funds.funds(account.getAccountId(), account.getUserId(), java.util.List.of(TransactionState.PROTECTED, TransactionState.HARD_HOLD)).orElseThrow();
        assertEquals(0, before.getReservedBalance().compareTo(new BigDecimal("350000.00")));
        String key = UUID.randomUUID().toString();
        assertEquals("CANCELLED", approvals.decline(first.getTransactionId(), admin, key).state());
        assertEquals("CANCELLED", approvals.decline(first.getTransactionId(), admin, key).state());
        assertEquals(new BigDecimal("500000.00"), accounts.findById(account.getAccountId()).orElseThrow().getBalance());
        assertEquals(TransactionState.HARD_HOLD, transactions.findById(second.getTransactionId()).orElseThrow().getState());
        var after = funds.funds(account.getAccountId(), account.getUserId(), java.util.List.of(TransactionState.PROTECTED, TransactionState.HARD_HOLD)).orElseThrow();
        assertEquals(0, after.getReservedBalance().compareTo(new BigDecimal("150000.00")));
        assertTrue(funds.funds(account.getAccountId(), -1L, java.util.List.of(TransactionState.HARD_HOLD)).isEmpty());
        assertEquals(1, jdbc.queryForObject("select count(*) from audit_log where transaction_id=? and action='ADMIN_DECLINED_CANCELLED' and user_id=?",Integer.class,first.getTransactionId(),admin.userId()));
        assertNotNull(transactions.findById(first.getTransactionId()).orElseThrow().getCancelledAt());
        assertThrows(com.ofss.excp.TransactionValidationException.class, () -> approvals.approve(first.getTransactionId(), admin, UUID.randomUUID().toString()));
        hardHold("200000.00");
        approvals.approve(second.getTransactionId(), admin, UUID.randomUUID().toString());
        assertEquals(new BigDecimal("350000.00"), accounts.findById(account.getAccountId()).orElseThrow().getBalance());
    }
    @Test void concurrentHardHoldsBothSucceedWhenFunded() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<TransactionDb> one = pool.submit(() -> hardHold("200000.00"));
            Future<TransactionDb> two = pool.submit(() -> hardHold("150000.00"));
            assertEquals(TransactionState.HARD_HOLD, one.get(20,TimeUnit.SECONDS).getState());
            assertEquals(TransactionState.HARD_HOLD, two.get(20,TimeUnit.SECONDS).getState());
        } finally { pool.shutdownNow(); }
        assertEquals(0, transactions.pendingAmount(account.getAccountId(),java.util.List.of(TransactionState.HARD_HOLD)).compareTo(new BigDecimal("350000.00")));
    }
    @Test void existingReservationCannotBeSpentTwiceAndExactMinimumIsAllowed() {
        account.setBalance(new BigDecimal("289600.00")); accounts.saveAndFlush(account);
        hardHold("200000.00");
        var failure = assertThrows(com.ofss.excp.InsufficientBalanceException.class, () -> hardHold("150000.00"));
        assertTrue(failure.getMessage().contains("84600"));
        account = accounts.findById(account.getAccountId()).orElseThrow();
        account.setBalance(new BigDecimal("355000.00")); accounts.saveAndFlush(account);
        assertEquals(TransactionState.HARD_HOLD, hardHold("150000.00").getState());
        assertThrows(com.ofss.excp.InsufficientBalanceException.class, () -> hardHold("150000.00"));
    }
    @Test void approvalAndDeclineRaceHasExactlyOneWinner() throws Exception {
        var payment = hardHold("200000.00"); ExecutorService pool = Executors.newFixedThreadPool(2);
        int completed = 0;
        try {
            var one = pool.submit(() -> approvals.approve(payment.getTransactionId(),admin,UUID.randomUUID().toString()));
            var two = pool.submit(() -> approvals.decline(payment.getTransactionId(),admin,UUID.randomUUID().toString()));
            for (var future : java.util.List.of(one,two)) {
                try { future.get(20,TimeUnit.SECONDS); completed++; }
                catch (ExecutionException e) { assertInstanceOf(com.ofss.excp.TransactionValidationException.class,e.getCause()); }
            }
        } finally { pool.shutdownNow(); }
        assertEquals(1,completed);
        var finalPayment=transactions.findById(payment.getTransactionId()).orElseThrow();
        var expected=finalPayment.getState()==TransactionState.SETTLED ? "300000.00" : "500000.00";
        assertEquals(new BigDecimal(expected),accounts.findById(account.getAccountId()).orElseThrow().getBalance());
        assertEquals(1,jdbc.queryForObject("select count(*) from audit_log where transaction_id=? and action in ('ADMIN_APPROVED_SETTLED','ADMIN_DECLINED_CANCELLED')",Integer.class,payment.getTransactionId()));
    }

}
