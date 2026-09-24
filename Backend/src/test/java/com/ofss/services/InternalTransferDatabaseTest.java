package com.ofss.services;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import com.ofss.beans.*;
import com.ofss.controller.TransactionController;
import com.ofss.excp.ResourceNotFoundExcp;
import com.ofss.excp.InvalidStateTransitionException;
import com.ofss.excp.TransactionValidationException;
import com.ofss.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:internal-transfers;MODE=Oracle;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.jpa.hibernate.ddl-auto=create-drop"})
@ActiveProfiles("local")
class InternalTransferDatabaseTest {
    @Autowired UserService users;
    @Autowired UserDao userDao;
    @Autowired RoleDao roles;
    @Autowired AccountDao accounts;
    @Autowired BeneficiaryDao beneficiaries;
    @Autowired TransactionDao transactions;
    @Autowired TransactionService service;
    @Autowired ExpiredTransactionSettlementService settlements;
    @Autowired AdminApprovalService approvals;
    @Autowired TransactionController controller;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean TransactionScheduler scheduler;
    Account sender, receiver;
    User senderUser, receiverUser;
    Beneficiary payee;
    LoginPrincipal admin;

    User customer(String name) {
        User user=new User();user.setName(name);user.setEmail(UUID.randomUUID()+"@transfer.test");
        user.setPhone(String.format("%010d",Math.abs(UUID.randomUUID().getLeastSignificantBits()%9000000000L)));
        user.setPasswordHash("Transfer#2026");return users.register(user);
    }
    Beneficiary payee(Account from, Account to) {
        Beneficiary b=new Beneficiary();b.setAccount(from);b.setBeneficiaryName("Receiving customer");
        b.setBankAccountNumber(to.getAccountNumber());b.setIfsc("TEST0000001");
        b.setStatus("ACTIVE");b.setCreatedAt(LocalDateTime.now());return beneficiaries.saveAndFlush(b);
    }
    @BeforeEach void setup() {
        senderUser=customer("Sending customer");receiverUser=customer("Receiving customer");
        sender=accounts.findFirstByUserUserIdOrderByAccountId(senderUser.getUserId()).orElseThrow();
        receiver=accounts.findFirstByUserUserIdOrderByAccountId(receiverUser.getUserId()).orElseThrow();
        sender.setBalance(new BigDecimal("500000.00"));receiver.setBalance(new BigDecimal("10000.00"));
        accounts.saveAllAndFlush(List.of(sender,receiver));payee=payee(sender,receiver);
        User actor=customer("Review administrator");actor.setRole(roles.findByRoleName("ADMIN").orElseThrow());userDao.saveAndFlush(actor);
        admin=new LoginPrincipal(actor.getUserId(),actor.getName(),actor.getEmail(),"ADMIN",UserStatus.ACTIVE);
    }
    TransactionDb create(String amount,String key) {
        return service.initiate(sender.getAccountId(),payee.getBeneficiaryId(),new BigDecimal(amount),"Test payment",key,senderUser.getUserId());
    }
    BigDecimal balance(Account a) {
        return jdbc.queryForObject("select balance from account where account_id=?",BigDecimal.class,a.getAccountId());
    }
    void balances(String sent,String received) {
        assertEquals(new BigDecimal(sent),balance(sender));assertEquals(new BigDecimal(received),balance(receiver));
        assertEquals(new BigDecimal("510000.00"),balance(sender).add(balance(receiver)));
    }
    void expire(TransactionDb tx) {
        jdbc.update("update transaction_db set protection_expires_at=? where transaction_id=?",
                LocalDateTime.now().minusMinutes(1),tx.getTransactionId());
    }
    @ParameterizedTest @ValueSource(strings={"5000.25","25000.00","75000.00","150000.00"})
    void everyRiskTierMovesBothBalancesExactlyOnce(String amount) {
        String key=UUID.randomUUID().toString();TransactionDb tx=create(amount,key);
        if(tx.getState()!=TransactionState.SETTLED)balances("500000.00","10000.00");
        if(tx.getState()==TransactionState.PROTECTED) {
            assertFalse(settlements.settle(tx.getTransactionId()));expire(tx);
            assertTrue(settlements.settle(tx.getTransactionId()));assertFalse(settlements.settle(tx.getTransactionId()));
        } else if(tx.getState()==TransactionState.HARD_HOLD) {
            assertFalse(settlements.settle(tx.getTransactionId()));
            String approval=UUID.randomUUID().toString();approvals.approve(tx.getTransactionId(),admin,approval);
            approvals.approve(tx.getTransactionId(),admin,approval);
        }
        service.initiate(sender.getAccountId(),payee.getBeneficiaryId(),new BigDecimal(amount),"Test payment",key,senderUser.getUserId());
        balances(new BigDecimal("500000.00").subtract(new BigDecimal(amount)).toPlainString(),
                new BigDecimal("10000.00").add(new BigDecimal(amount)).toPlainString());
        assertEquals(receiver.getAccountId(),transactions.findById(tx.getTransactionId()).orElseThrow().getToAccount().getAccountId());
        var received=controller.getTransactions(null,new LoginPrincipal(receiverUser.getUserId(),receiverUser.getName(),receiverUser.getEmail(),"CUSTOMER",UserStatus.ACTIVE));
        assertEquals(1,received.size());assertEquals("CREDIT",received.get(0).get("direction"));
        assertEquals(senderUser.getName(),received.get(0).get("counterpartyName"));
        assertEquals(false,received.get(0).get("canCancel"));
        assertEquals("DEBIT",controller.getTransactions(null,new LoginPrincipal(senderUser.getUserId(),senderUser.getName(),senderUser.getEmail(),"CUSTOMER",UserStatus.ACTIVE)).get(0).get("direction"));
    }
    @Test void cancelledAndDeclinedPaymentsNeverCreditAndRecipientsCannotCancel() {
        TransactionDb timed=create("20000.00",UUID.randomUUID().toString());
        assertTrue(service.getTransactions(null,receiverUser.getEmail()).isEmpty());
        assertThrows(ResourceNotFoundExcp.class,()->service.getTransaction(timed.getTransactionId(),receiverUser.getEmail()));
        assertThrows(ResourceNotFoundExcp.class,()->service.cancel(timed.getTransactionId(),"receiver-cancel",receiverUser.getEmail()));
        service.cancel(timed.getTransactionId(),UUID.randomUUID().toString(),senderUser.getEmail());
        TransactionDb held=create("150000.00",UUID.randomUUID().toString());
        approvals.decline(held.getTransactionId(),admin,UUID.randomUUID().toString());
        balances("500000.00","10000.00");assertTrue(service.getTransactions(null,receiverUser.getEmail()).isEmpty());
    }
    @Test void concurrentDuplicateRequestCreditsOnce() throws Exception {
        String key=UUID.randomUUID().toString();ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            var one=pool.submit(()->create("1000.25",key));var two=pool.submit(()->create("1000.25",key));
            assertEquals(one.get(20,TimeUnit.SECONDS).getTransactionId(),two.get(20,TimeUnit.SECONDS).getTransactionId());
        } finally {pool.shutdownNow();}
        balances("498999.75","11000.25");
    }

    @Test void ownerCanCancelHardHoldAndReleaseOnlyItsReservation() {
        TransactionDb held=create("150000.00",UUID.randomUUID().toString());
        TransactionDb other=create("120000.00",UUID.randomUUID().toString());
        assertThrows(ResourceNotFoundExcp.class,()->service.cancel(held.getTransactionId(),UUID.randomUUID().toString(),receiverUser.getEmail()));
        assertThrows(ResourceNotFoundExcp.class,()->service.cancel(held.getTransactionId(),UUID.randomUUID().toString(),admin.email()));
        String key=UUID.randomUUID().toString();
        TransactionDb cancelled=service.cancel(held.getTransactionId(),key,senderUser.getEmail());
        assertEquals(TransactionState.CANCELLED,cancelled.getState());assertNotNull(cancelled.getCancelledAt());
        assertEquals(key,cancelled.getCancelIdempotencyKey());
        assertEquals(TransactionState.CANCELLED,service.cancel(held.getTransactionId(),key,senderUser.getEmail()).getState());
        assertThrows(TransactionValidationException.class,()->service.cancel(other.getTransactionId(),key,senderUser.getEmail()));
        assertThrows(TransactionValidationException.class,()->approvals.approve(held.getTransactionId(),admin,UUID.randomUUID().toString()));
        assertFalse(settlements.settle(held.getTransactionId()));
        balances("500000.00","10000.00");
        assertEquals(0,new BigDecimal("120000.00").compareTo(transactions.pendingAmount(sender.getAccountId(),List.of(TransactionState.PROTECTED,TransactionState.HARD_HOLD))));
        assertFalse(approvals.pending(admin).stream().anyMatch(t->t.transactionId().equals(held.getTransactionId())));
        assertEquals(TransactionState.HARD_HOLD,transactions.findById(other.getTransactionId()).orElseThrow().getState());
        assertEquals(1,jdbc.queryForObject("select count(*) from audit_log where transaction_id=? and action='TRANSACTION_CANCELLED' and old_state='HARD_HOLD' and new_state='CANCELLED' and user_id=?",Integer.class,held.getTransactionId(),senderUser.getUserId()));
    }

    @Test void concurrentHardHoldCancellationReplayWritesOnce() throws Exception {
        TransactionDb held=create("150000.00",UUID.randomUUID().toString());String key=UUID.randomUUID().toString();
        ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch go=new CountDownLatch(1);
        try {
            var one=pool.submit(()->{go.await();return service.cancel(held.getTransactionId(),key,senderUser.getEmail());});
            var two=pool.submit(()->{go.await();return service.cancel(held.getTransactionId(),key,senderUser.getEmail());});
            go.countDown();assertEquals(TransactionState.CANCELLED,one.get(20,TimeUnit.SECONDS).getState());
            assertEquals(TransactionState.CANCELLED,two.get(20,TimeUnit.SECONDS).getState());
        } finally {pool.shutdownNow();}
        balances("500000.00","10000.00");
        assertEquals(1,jdbc.queryForObject("select count(*) from audit_log where transaction_id=? and action='TRANSACTION_CANCELLED'",Integer.class,held.getTransactionId()));
    }

    @Test void cancellationAfterAdminApprovalCannotUndoSettlement() {
        TransactionDb held=create("150000.00",UUID.randomUUID().toString());
        approvals.approve(held.getTransactionId(),admin,UUID.randomUUID().toString());
        assertThrows(InvalidStateTransitionException.class,()->service.cancel(held.getTransactionId(),UUID.randomUUID().toString(),senderUser.getEmail()));
        balances("350000.00","160000.00");
    }

    @Test void concurrentCustomerCancelAndAdminApprovalHaveExactlyOneWinner() throws Exception {
        TransactionDb held=create("150000.00",UUID.randomUUID().toString());
        ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch go=new CountDownLatch(1);
        try {
            var cancel=pool.submit(()->{go.await();try {service.cancel(held.getTransactionId(),UUID.randomUUID().toString(),senderUser.getEmail());return true;}
                catch(InvalidStateTransitionException expected){return false;}});
            var approve=pool.submit(()->{go.await();try {approvals.approve(held.getTransactionId(),admin,UUID.randomUUID().toString());return true;}
                catch(TransactionValidationException expected){assertEquals(409,expected.getStatus());return false;}});
            go.countDown();assertNotEquals(cancel.get(20,TimeUnit.SECONDS),approve.get(20,TimeUnit.SECONDS));
        } finally {pool.shutdownNow();}
        var state=transactions.findById(held.getTransactionId()).orElseThrow().getState();
        if(state==TransactionState.SETTLED)balances("350000.00","160000.00");
        else {assertEquals(TransactionState.CANCELLED,state);balances("500000.00","10000.00");}
        assertEquals(1,jdbc.queryForObject("select count(*) from audit_log where transaction_id=? and action in ('TRANSACTION_CANCELLED','ADMIN_APPROVED_SETTLED')",Integer.class,held.getTransactionId()));
    }

    @Test void failedHardHoldCancellationAuditRollsBackStateAndReservation() {
        TransactionDb held=create("150000.00",UUID.randomUUID().toString());
        jdbc.execute("alter table audit_log add constraint test_hold_cancel_audit_fail check (transaction_id <> "+held.getTransactionId()+" or action <> 'TRANSACTION_CANCELLED')");
        try {assertThrows(RuntimeException.class,()->service.cancel(held.getTransactionId(),UUID.randomUUID().toString(),senderUser.getEmail()));}
        finally {jdbc.execute("alter table audit_log drop constraint test_hold_cancel_audit_fail");}
        assertEquals(TransactionState.HARD_HOLD,transactions.findById(held.getTransactionId()).orElseThrow().getState());
        assertEquals(0,new BigDecimal("150000.00").compareTo(transactions.pendingAmount(sender.getAccountId(),List.of(TransactionState.HARD_HOLD))));
        balances("500000.00","10000.00");
    }
    @Test void oppositeDirectionTransfersDoNotDeadlockOrLoseUpdates() throws Exception {
        Beneficiary reverse=payee(receiver,sender);ExecutorService pool=Executors.newFixedThreadPool(2);
        CountDownLatch go=new CountDownLatch(1);
        try {
            var one=pool.submit(()->{go.await();return create("1000.25",UUID.randomUUID().toString());});
            var two=pool.submit(()->{go.await();return service.initiate(receiver.getAccountId(),reverse.getBeneficiaryId(),
                    new BigDecimal("1000.25"),"Reverse",UUID.randomUUID().toString(),receiverUser.getUserId());});
            go.countDown();assertEquals(TransactionState.SETTLED,one.get(20,TimeUnit.SECONDS).getState());
            assertEquals(TransactionState.SETTLED,two.get(20,TimeUnit.SECONDS).getState());
        } finally {pool.shutdownNow();}
        balances("500000.00","10000.00");
    }
    @Test void receiverFailureRollsBackBothBalancesAndState() {
        TransactionDb tx=create("150000.00",UUID.randomUUID().toString());
        receiver.setStatus(AccountStatus.CLOSED);accounts.saveAndFlush(receiver);
        assertThrows(RuntimeException.class,()->approvals.approve(tx.getTransactionId(),admin,UUID.randomUUID().toString()));
        balances("500000.00","10000.00");
        assertEquals(TransactionState.HARD_HOLD,transactions.findById(tx.getTransactionId()).orElseThrow().getState());
    }
    @Test void auditFailureRollsBackCreditDebitAndSettlement() {
        TransactionDb tx=create("25000.00",UUID.randomUUID().toString());expire(tx);
        jdbc.execute("alter table audit_log add constraint test_credit_audit_fail check (transaction_id <> "+tx.getTransactionId()+" or action <> 'TRANSACTION_AUTO_SETTLED')");
        try {assertThrows(RuntimeException.class,()->settlements.settle(tx.getTransactionId()));}
        finally {jdbc.execute("alter table audit_log drop constraint test_credit_audit_fail");}
        balances("500000.00","10000.00");
        assertEquals(TransactionState.PROTECTED,transactions.findById(tx.getTransactionId()).orElseThrow().getState());
    }
    @Test void sameAccountIsRejectedBeforeMoneyMoves() {
        Beneficiary self=payee(sender,sender);
        assertThrows(RuntimeException.class,()->service.initiate(sender.getAccountId(),self.getBeneficiaryId(),
                new BigDecimal("500.00"),"Self",UUID.randomUUID().toString(),senderUser.getUserId()));
        balances("500000.00","10000.00");
    }
    @Test void historicalSettledPaymentIsNotRetroactivelyCredited() {
        TransactionDb tx=create("500.00",UUID.randomUUID().toString());
        jdbc.update("update transaction_db set to_account_id=null where transaction_id=?",tx.getTransactionId());
        assertFalse(settlements.settle(tx.getTransactionId()));balances("499500.00","10500.00");
        assertTrue(service.getTransactions(null,receiverUser.getEmail()).isEmpty());
    }

    @Test void legacyUnsettledPaymentResolvesItsReceiverWhenItActuallySettles() {
        TransactionDb tx=create("25000.00",UUID.randomUUID().toString());
        jdbc.update("update transaction_db set to_account_id=null where transaction_id=?",tx.getTransactionId());
        expire(tx);assertTrue(settlements.settle(tx.getTransactionId()));balances("475000.00","35000.00");
        assertEquals(1,service.getTransactions(null,receiverUser.getEmail()).size());
    }
    @Test void transferBetweenTwoAccountsOfOneOwnerUpdatesEachAndDoesNotDuplicateHistory() {
        Account second=new Account();second.setUser(senderUser);second.setAccountNumber("90"+sender.getAccountNumber());
        second.setBalance(new BigDecimal("10000.00"));second.setCreatedAt(LocalDateTime.now());second.setUpdatedAt(LocalDateTime.now());
        second=accounts.saveAndFlush(second);Beneficiary own=payee(sender,second);
        TransactionDb tx=service.initiate(sender.getAccountId(),own.getBeneficiaryId(),new BigDecimal("500.25"),
                "Between my accounts",UUID.randomUUID().toString(),senderUser.getUserId());
        assertEquals(new BigDecimal("499499.75"),balance(sender));assertEquals(new BigDecimal("10500.25"),balance(second));
        assertEquals(1,service.getTransactions(null,senderUser.getEmail()).size());
        assertEquals(second.getAccountId(),transactions.findById(tx.getTransactionId()).orElseThrow().getToAccount().getAccountId());
    }
    @Test void unrelatedUserCannotReadOrCancelAnIncomingReceipt() {
        TransactionDb tx=create("500.00",UUID.randomUUID().toString());
        assertEquals(tx.getTransactionId(),service.getTransaction(tx.getTransactionId(),receiverUser.getEmail()).getTransactionId());
        assertThrows(ResourceNotFoundExcp.class,()->service.getTransaction(tx.getTransactionId(),admin.email()));
        assertThrows(ResourceNotFoundExcp.class,()->service.cancel(tx.getTransactionId(),UUID.randomUUID().toString(),receiverUser.getEmail()));
        assertTrue(service.getTransactions(null,admin.email()).isEmpty());
        balances("499500.00","10500.00");
    }
}
