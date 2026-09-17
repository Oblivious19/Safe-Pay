package com.safepay.assistant;
import static org.junit.jupiter.api.Assertions.*;
import static com.safepay.assistant.Models.*;
import java.nio.file.*;
import java.sql.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

class EvidenceRepositoryTest {
    @TempDir Path temp;String url;EvidenceRepository repository;LocalDateTime now=LocalDateTime.of(2026,9,16,12,0);
    @BeforeEach void setup()throws Exception{
        url="jdbc:h2:mem:assistant-"+UUID.randomUUID()+";MODE=Oracle;DB_CLOSE_DELAY=-1";
        Path properties=temp.resolve("db.properties");Files.writeString(properties,"spring.datasource.url="+url+"\nspring.datasource.username=sa\nspring.datasource.password=\n");
        repository=new EvidenceRepository(new DatabaseConnections(properties.toString(),new MockEnvironment()));
        try(Connection c=connection();Statement s=c.createStatement()){
            s.execute("create table account(account_id number primary key,user_id number,balance number(18,2))");
            s.execute("create table beneficiaries(beneficiary_id number primary key,beneficiary_name varchar2(200),bank_account_number varchar2(30),ifsc varchar2(20),created_at timestamp)");
            s.execute("create table transaction_db(transaction_id number primary key,transaction_ref varchar2(50),version number,from_account_id number,beneficiary_id number,amount number(18,2),state varchar2(30),risk_tier varchar2(30),risk_reason varchar2(2000),purpose varchar2(200),created_at timestamp,settled_at timestamp)");
            s.execute("insert into account values(1,7,500000)");s.execute("insert into account values(2,7,500000)");s.execute("insert into account values(3,8,500000)");
            s.execute("insert into beneficiaries values(11,'Recipient','123456789012','SBIN0001234',timestamp '2026-09-16 11:00:00')");
            s.execute("insert into beneficiaries values(12,'Other recipient','999999999999','SBIN0001234',timestamp '2026-01-01 11:00:00')");
            s.execute("insert into beneficiaries values(21,'Same recipient on other account','123456789012','SBIN0001234',timestamp '2026-01-01 11:00:00')");
        }
        payment(100,1,11,"120000.00","HARD_HOLD",now,null);
        payment(10,1,12,"5000.00","SETTLED",now.minusDays(2),now.minusDays(2));
        payment(20,2,12,"15000.00","SETTLED",now.minusDays(1),now.minusDays(1));
        payment(30,3,11,"990000.00","SETTLED",now.minusDays(1),now.minusDays(1));
        payment(40,1,11,"500.00","PROTECTED",now.minusMinutes(2),null);
        payment(50,1,11,"900000.00","SETTLED",now.minusMinutes(1),now.plusHours(1));
    }
    Connection connection()throws SQLException{return DriverManager.getConnection(url,"sa","");}
    void payment(long id,long account,long beneficiary,String amount,String state,LocalDateTime created,LocalDateTime settled)throws SQLException{
        try(Connection c=connection();PreparedStatement q=c.prepareStatement("insert into transaction_db values(?,?,0,?,?,?,?,?,?,?, ?,?)")){
            q.setLong(1,id);q.setString(2,"TX-"+id);q.setLong(3,account);q.setLong(4,beneficiary);q.setBigDecimal(5,new BigDecimal(amount));q.setString(6,state);q.setString(7,"VERY_HIGH");q.setString(8,"Saved reason");q.setString(9,"rent");q.setObject(10,created);q.setObject(11,settled);q.executeUpdate();
        }
    }
    Pending pending(){return new Pending(100L,"TX-100","120000.00",7L,"Customer",1L,"500000000001","Recipient","123456789012","SBIN0001234","rent","Saved reason",now.toString());}
    String fact(Evidence e,String id){return e.facts().stream().filter(f->f.id().equals(id)).findFirst().orElseThrow().text();}
    @Test void comparesAllOwnedAccountsWithoutForeignDataOrFutureSettlements(){
        Evidence e=repository.read(pending());assertEquals(2,e.recentSettledPayments().size());
        assertTrue(fact(e,"HISTORY").contains("2 settled payments"));assertTrue(fact(e,"HISTORY").contains("20000.00"));
        assertTrue(fact(e,"AMOUNT_COMPARISON").contains("8.00 times"));assertTrue(fact(e,"BENEFICIARY").contains("No earlier"));
        assertTrue(fact(e,"VELOCITY").startsWith("3 payment requests"));assertTrue(fact(e,"DAILY_TOTAL").contains("15000.00"));
        assertEquals("••••9012",e.maskedBeneficiary());assertEquals("Saved reason",e.riskReason());
    }
    @Test void recipientHistoryRecognizesSameRecipientAcrossAccounts()throws Exception{
        payment(60,2,21,"10000.00","SETTLED",now.minusDays(3),now.minusDays(3));
        assertTrue(fact(repository.read(pending()),"BENEFICIARY").startsWith("1 earlier"));
    }
    @Test void missingHistoryIsExplicitAndHasNoInventedComparison()throws Exception{
        try(Connection c=connection();Statement s=c.createStatement()){s.execute("delete from transaction_db where state='SETTLED'");}
        var e=repository.read(pending());assertTrue(fact(e,"HISTORY").contains("Insufficient history"));
        assertTrue(e.facts().stream().noneMatch(f->f.id().equals("AMOUNT_COMPARISON")));
    }
    @Test void mismatchedBackendAndDatabaseIdentityFailsClosed(){
        var original=pending();var other=new Pending(100L,"FOREIGN-REFERENCE",original.amount(),7L,"Customer",1L,"a","b","c","d","e","f","g");
        assertEquals(503,assertThrows(AppError.class,()->repository.read(other)).status());
    }
    @Test void changedStateAndVersionAreRejected()throws Exception{
        repository.requireUnchanged(100,0);
        try(Connection c=connection();Statement s=c.createStatement()){s.execute("update transaction_db set state='SETTLED',version=1 where transaction_id=100");}
        assertEquals(409,assertThrows(AppError.class,()->repository.requireUnchanged(100,0)).status());
        assertEquals(409,assertThrows(AppError.class,()->repository.read(pending())).status());
    }
    @Test void evidenceReadsNeverMutateBalancesOrTransactions()throws Exception{
        repository.read(pending());repository.requireUnchanged(100,0);
        try(Connection c=connection();Statement s=c.createStatement();ResultSet r=s.executeQuery("select a.balance,t.state,t.version from account a join transaction_db t on a.account_id=t.from_account_id where t.transaction_id=100")){
            r.next();assertEquals(0,new BigDecimal("500000.00").compareTo(r.getBigDecimal(1)));assertEquals("HARD_HOLD",r.getString(2));assertEquals(0,r.getLong(3));
        }
    }
    @Test void exactHighPrecisionMoneyIsPreserved()throws Exception{
        payment(70,1,12,"9999999999999999.99","SETTLED",now.minusDays(4),now.minusDays(4));
        var e=repository.read(pending());assertTrue(fact(e,"HISTORY").contains("9999999999999999.99"));
        assertTrue(e.recentSettledPayments().stream().anyMatch(row->row.amount().equals("9999999999999999.99")));
    }
}
