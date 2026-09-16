package com.ofss.services;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import com.ofss.beans.*;
import com.ofss.beans.AdminCreditDtos.Receipt;
import com.ofss.excp.ResourceNotFoundExcp;
import com.ofss.excp.TransactionValidationException;
import com.ofss.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

/** Real database transactions: no mocked transaction manager, no enclosing test transaction. */
@SpringBootTest(properties={
        "spring.datasource.url=jdbc:h2:mem:safepay-admin-operations;MODE=Oracle;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.jpa.hibernate.ddl-auto=create-drop"})
@ActiveProfiles("local")
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class AdminOperationsDatabaseTest {
    @Autowired AdminCreditService credits;
    @Autowired AdminProvisioningService provisioning;
    @Autowired AdminService admin;
    @Autowired TransactionService transactions;
    @Autowired TransactionDao transactionRows;
    @Autowired UserDao users;
    @Autowired RoleDao roles;
    @Autowired AccountDao accounts;
    @Autowired BeneficiaryDao beneficiaries;
    @Autowired AuditLogDao audits;
    @Autowired JdbcTemplate jdbc;
    static final AtomicLong phones=new AtomicLong(8000000000L);
    User actor;
    User customer;
    Account account;
    @BeforeEach void setup() {
        actor=user("ADMIN"); customer=user("CUSTOMER"); account=account(customer);
    }
    User user(String role) {
        User user=new User(); user.setName("Admin operation test"); user.setEmail(UUID.randomUUID()+"@example.test");
        user.setPhone(Long.toString(phones.incrementAndGet())); user.setRole(roles.findByRoleName(role).orElseThrow());
        user.setPasswordHash("$2a$10$abcdefghijklmnopqrstuuOX8dqeTKdkgoqWxyGFNJYZmydgZEE.");
        user.setCreatedAt(LocalDateTime.now()); user.setUpdatedAt(user.getCreatedAt()); return users.saveAndFlush(user);
    }
    Account account(User owner) {
        Account account=new Account(); account.setUser(owner); account.setAccountNumber(accounts.nextAccountNumber());
        account.setBalance(new BigDecimal("200000.00")); account.setCreatedAt(LocalDateTime.now()); account.setUpdatedAt(account.getCreatedAt());
        return accounts.saveAndFlush(account);
    }
    Receipt credit(String key,String amount) { return credits.credit(account.getAccountId(),actor.getUserId(),new BigDecimal(amount),key); }
    void balance(Account source,String amount) {
        assertEquals(0,new BigDecimal(amount).compareTo(accounts.findById(source.getAccountId()).orElseThrow().getBalance()));
    }
    @Test void persistentReplayReturnsOriginalReceiptEvenAfterLaterBalanceOrStatusChanges() {
        String key=UUID.randomUUID().toString();
        Receipt original=credit(key,"10.25");
        credit(UUID.randomUUID().toString(),"20.00");
        account=accounts.findById(account.getAccountId()).orElseThrow(); account.setStatus(AccountStatus.BLOCKED); accounts.saveAndFlush(account);
        assertEquals(original,credit(key,"10.25"));
        balance(account,"200030.25");
        AuditLog audit=audits.findByRequestKey(key).orElseThrow();
        assertEquals(actor.getUserId(),audit.getUserId()); assertEquals("SIMULATED_CREDIT:"+account.getAccountId(),audit.getAction());
        assertEquals("200000.00",audit.getOldState()); assertEquals("200010.25",audit.getNewState());
        assertThrows(TransactionValidationException.class,()->credit(UUID.randomUUID().toString(),"1.00"));
    }
    @Test void concurrentIdenticalRetryCommitsOneCreditAndOneAudit() throws Exception {
        String key=UUID.randomUUID().toString();
        List<Receipt> results=concurrently(()->credit(key,"15.50"),()->credit(key,"15.50"));
        assertEquals(results.get(0),results.get(1)); balance(account,"200015.50");
        assertEquals(1,jdbc.queryForObject("select count(*) from audit_log where request_key=?",Integer.class,key));
    }
    @Test void concurrentDifferentKeysNeverLoseAnUpdate() throws Exception {
        concurrently(()->credit(UUID.randomUUID().toString(),"1.01"),()->credit(UUID.randomUUID().toString(),"2.02"));
        balance(account,"200003.03");
        assertEquals(2,jdbc.queryForObject("select count(*) from audit_log where user_id=? and action=?",Integer.class,
                actor.getUserId(),"SIMULATED_CREDIT:"+account.getAccountId()));
    }
    @Test void keyCannotBeReusedForAnotherAmountAccountOrActor() {
        String key=UUID.randomUUID().toString(); credit(key,"10.00");
        Account second=account(customer); User otherAdmin=user("ADMIN");
        assertEquals(409,assertThrows(TransactionValidationException.class,()->credit(key,"11.00")).getStatus());
        assertEquals(409,assertThrows(TransactionValidationException.class,()->credits.credit(second.getAccountId(),actor.getUserId(),new BigDecimal("10.00"),key)).getStatus());
        assertThrows(ResourceNotFoundExcp.class,()->credits.credit(account.getAccountId(),otherAdmin.getUserId(),new BigDecimal("10.00"),key));
        balance(account,"200010.00"); balance(second,"200000.00");
    }
    @Test void failedAuditRollsBackBalanceAndDoesNotConsumeRetryKey() {
        String key="rollback-credit-"+account.getAccountId();
        jdbc.execute("alter table audit_log add constraint reject_credit_test check (request_key is null or request_key <> '"+key+"')");
        try {
            assertThrows(DataIntegrityViolationException.class,()->credit(key,"5.00"));
            balance(account,"200000.00"); assertTrue(audits.findByRequestKey(key).isEmpty());
        } finally { jdbc.execute("alter table audit_log drop constraint reject_credit_test"); }
        credit(key,"5.00"); balance(account,"200005.00");
    }
    @Test void creditValidatesPrecisionBoundsAndKeepsHeldReservations() {
        for(String amount:List.of("0","-1","0.001","10000000000000000"))
            assertThrows(IllegalArgumentException.class,()->credit(UUID.randomUUID().toString(),amount));
        assertThrows(IllegalArgumentException.class,()->credit(" ","1.00"));
        assertThrows(IllegalArgumentException.class,()->credit("é".repeat(51),"1.00"));
        Beneficiary b=new Beneficiary(); b.setAccount(account); b.setBeneficiaryName("Reserved payment"); b.setBankAccountNumber("123456789");
        b.setIfsc("SBIN0001234"); b.setStatus("ACTIVE"); b.setCreatedAt(LocalDateTime.now()); b=beneficiaries.saveAndFlush(b);
        TransactionDb held=transactions.initiate(account.getAccountId(),b.getBeneficiaryId(),new BigDecimal("150000.00"),
                "Reservation",UUID.randomUUID().toString(),customer.getUserId());
        assertEquals(TransactionState.HARD_HOLD,held.getState());
        credit(UUID.randomUUID().toString(),"10000.00"); balance(account,"210000.00");
        assertEquals(0,new BigDecimal("150000.00").compareTo(transactionRows.pendingAmount(account.getAccountId(),
                List.of(TransactionState.PROTECTED,TransactionState.HARD_HOLD))));
        account=accounts.findById(account.getAccountId()).orElseThrow(); account.setBalance(new BigDecimal("9999999999999999.99")); accounts.saveAndFlush(account);
        assertThrows(TransactionValidationException.class,()->credit(UUID.randomUUID().toString(),"0.01"));
        balance(account,"9999999999999999.99");
    }
    @Test void userDetailsIncludeEveryAccountAndStatusReactivationClearsLockoutAndAuditsActor() {
        Account second=account(customer);
        assertEquals(2,credits.accounts(customer.getUserId()).size());
        assertEquals(second.getAccountId(),credits.accounts(customer.getUserId()).get(1).accountId());
        assertEquals(customer.getUserId(),admin.getUser(customer.getUserId()).userId());
        assertTrue(credits.accounts(actor.getUserId()).isEmpty());
        customer.setStatus(UserStatus.LOCKED); customer.setFailedLoginAttempts(5); customer.setLockedUntil(LocalDateTime.now().plusMinutes(15)); users.saveAndFlush(customer);
        admin.changeUserStatus(customer.getUserId(),UserStatus.ACTIVE,actor.getUserId());
        User active=users.findById(customer.getUserId()).orElseThrow();
        assertEquals(UserStatus.ACTIVE,active.getStatus()); assertEquals(0,active.getFailedLoginAttempts()); assertNull(active.getLockedUntil());
        assertEquals(1,jdbc.queryForObject("select count(*) from audit_log where user_id=? and action=? and old_state='LOCKED' and new_state='ACTIVE'",
                Integer.class,actor.getUserId(),"USER_STATUS:"+customer.getUserId()));
        admin.changeUserStatus(customer.getUserId(),UserStatus.ACTIVE,actor.getUserId());
        assertEquals(1,jdbc.queryForObject("select count(*) from audit_log where action=?",Integer.class,"USER_STATUS:"+customer.getUserId()));
    }
    @Test void statusAndProvisioningRollBackIfTheirAuditCannotBeStored() {
        String action="USER_STATUS:"+customer.getUserId();
        jdbc.execute("alter table audit_log add constraint reject_status_test check (action <> '"+action+"')");
        try {
            assertThrows(RuntimeException.class,()->admin.changeUserStatus(customer.getUserId(),UserStatus.SUSPENDED,actor.getUserId()));
            assertEquals(UserStatus.ACTIVE,users.findById(customer.getUserId()).orElseThrow().getStatus());
        } finally { jdbc.execute("alter table audit_log drop constraint reject_status_test"); }
        String email=UUID.randomUUID()+"@example.test";
        var request=new AdminProvisionRequest("New administrator",email,Long.toString(phones.incrementAndGet()),"InitialAdmin#2026");
        jdbc.execute("alter table audit_log add constraint reject_provision_test check (user_id <> "+actor.getUserId()+" or action not like 'ADMIN_CREATED:%')");
        try {
            assertThrows(DataIntegrityViolationException.class,()->provisioning.create(request,actor.getUserId()));
            assertTrue(users.findByEmail(email).isEmpty());
        } finally { jdbc.execute("alter table audit_log drop constraint reject_provision_test"); }
        var created=provisioning.create(request,actor.getUserId());
        assertEquals("ADMIN",created.role()); assertTrue(accounts.findByUserUserIdOrderByAccountId(created.userId()).isEmpty());
        assertTrue(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().matches("InitialAdmin#2026",users.findById(created.userId()).orElseThrow().getPasswordHash()));
        assertEquals(1,jdbc.queryForObject("select count(*) from audit_log where user_id=? and action=?",Integer.class,actor.getUserId(),"ADMIN_CREATED:"+created.userId()));
    }
    List<Receipt> concurrently(Callable<Receipt> first,Callable<Receipt> second) throws Exception {
        ExecutorService executor=Executors.newFixedThreadPool(2);
        CountDownLatch ready=new CountDownLatch(2), start=new CountDownLatch(1);
        try {
            Future<Receipt> left=executor.submit(()->{ready.countDown();start.await();return first.call();});
            Future<Receipt> right=executor.submit(()->{ready.countDown();start.await();return second.call();});
            assertTrue(ready.await(5,TimeUnit.SECONDS)); start.countDown();
            return List.of(left.get(20,TimeUnit.SECONDS),right.get(20,TimeUnit.SECONDS));
        } finally { start.countDown();executor.shutdownNow(); }
    }
}