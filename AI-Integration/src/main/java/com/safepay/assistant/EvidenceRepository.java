package com.safepay.assistant;
import static com.safepay.assistant.Models.*;
import java.math.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Repository;

@Repository
public class EvidenceRepository {
    private final DatabaseConnections connections;
    public EvidenceRepository(DatabaseConnections connections){this.connections=connections;}
    public Evidence read(Pending expected) {
        try(Connection c=connections.open()) {
            String sql="select t.transaction_id,t.transaction_ref,t.version,t.amount,t.state,t.risk_tier,t.risk_reason,t.purpose,t.created_at,"
                +"a.account_id,a.user_id,b.beneficiary_id,b.beneficiary_name,b.bank_account_number,b.ifsc,b.created_at beneficiary_created "
                +"from transaction_db t join account a on a.account_id=t.from_account_id join beneficiaries b on b.beneficiary_id=t.beneficiary_id where t.transaction_id=?";
            try(PreparedStatement q=prepare(c,sql,expected.transactionId());ResultSet r=q.executeQuery()) {
                if(!r.next())throw new AppError(409,"Payment is not available in the configured database");
                if(!Objects.equals(r.getString("transaction_ref"),expected.transactionRef()) || r.getLong("user_id")!=expected.userId()
                    || r.getLong("account_id")!=expected.fromAccountId() || r.getBigDecimal("amount").compareTo(new BigDecimal(expected.amount()))!=0) {
                    throw new AppError(503,"The assistant database does not match this SafePay payment");
                }
                if(!"HARD_HOLD".equals(r.getString("state")))throw new AppError(409,"Payment is no longer on HARD_HOLD");
                LocalDateTime at=time(r,"created_at");
                if(at==null)throw new AppError(409,"Payment creation time is unavailable; history cannot be compared reliably");
                long owner=r.getLong("user_id"), id=r.getLong("transaction_id");
                BigDecimal amount=r.getBigDecimal("amount"); LocalDateTime start=at.minusDays(30);
                var facts=new ArrayList<Fact>();
                facts.add(new Fact("CURRENT","Current payment", "Payment amount INR "+money(amount)+". Saved tier: "+r.getString("risk_tier")+". Current state: HARD_HOLD. Administrator approval is required.",List.of(id)));
                List<HistoryRow> recent=recent(c,owner,start,at,id);
                Object[] stats=aggregate(c,"select count(*),min(t.amount),max(t.amount),sum(t.amount) from transaction_db t join account a on a.account_id=t.from_account_id "
                    +"where a.user_id=? and t.state='SETTLED' and t.settled_at>=? and t.settled_at<=? and t.transaction_id<>?",owner,start,at,id);
                long count=((Number)stats[0]).longValue(); BigDecimal max=(BigDecimal)stats[2];
                String history=count==0?"No settled payments in the 30 days preceding this payment. Insufficient history for an amount comparison."
                    :count+" settled payments in the preceding 30 days, across this customer's accounts. Range INR "+money((BigDecimal)stats[1])+" to INR "+money(max)+"; total INR "+money((BigDecimal)stats[3])+".";
                if(count>0 && count<5)history+=" Limited history: fewer than five settled payments.";
                facts.add(new Fact("HISTORY","30-day settled history",history,recent.stream().map(HistoryRow::transactionId).toList()));
                if(max!=null && max.signum()>0)facts.add(new Fact("AMOUNT_COMPARISON","Amount comparison", "Current amount is "+amount.divide(max,2,RoundingMode.HALF_UP).toPlainString()+" times the largest settled payment in the preceding 30 days. This comparison alone does not establish fraud.",List.of(id)));
                String beneficiarySql="select count(*) from transaction_db t join account a on a.account_id=t.from_account_id join beneficiaries b on b.beneficiary_id=t.beneficiary_id "
                    +"where a.user_id=? and b.bank_account_number=? and b.ifsc=? and t.state='SETTLED' and t.settled_at<=? and t.transaction_id<>?";
                long previous=count(c,beneficiarySql,owner,r.getString("bank_account_number"),r.getString("ifsc"),at,id);
                facts.add(new Fact("BENEFICIARY","Recipient history",previous==0?"No earlier settled payment to this recipient was found across the customer's accounts. This does not by itself indicate fraud.":previous+" earlier settled payments to this recipient were found across the customer's accounts.",List.of()));
                LocalDateTime added=time(r,"beneficiary_created");
                facts.add(new Fact("BENEFICIARY_AGE","Saved beneficiary age",added==null || added.isAfter(at)?"Beneficiary age is unavailable or inconsistent with the payment time.":"This source account's beneficiary record was added "+Duration.between(added,at).toHours()+" completed hours before the payment.",List.of()));
                long five=count(c,"select count(*) from transaction_db t join account a on a.account_id=t.from_account_id where a.user_id=? and t.created_at>=? "
                    +"and (t.created_at<? or (t.created_at=? and t.transaction_id<=?))",owner,at.minusMinutes(5),at,at,id);
                facts.add(new Fact("VELOCITY","Recent payment requests",five+" payment requests in the five minutes up to this payment, including this request, across the customer's accounts. Requests may have different outcomes.",List.of(id)));
                Object[] day=aggregate(c,"select count(*),min(t.amount),max(t.amount),sum(t.amount) from transaction_db t join account a on a.account_id=t.from_account_id "
                    +"where a.user_id=? and t.state='SETTLED' and t.settled_at>=? and t.settled_at<=? and t.transaction_id<>?",owner,at.minusHours(24),at,id);
                facts.add(new Fact("DAILY_TOTAL","Previous 24 hours",day[0]+" settled payments totalling INR "+money((BigDecimal)day[3])+" in the 24 hours preceding this payment. The current held payment is excluded.",List.of()));
                return new Evidence(id,r.getString("transaction_ref"),r.getLong("version"),owner,r.getLong("account_id"),money(amount),r.getString("state"),
                    r.getString("risk_tier"),r.getString("risk_reason"),r.getString("beneficiary_name"),mask(r.getString("bank_account_number")),r.getString("purpose"),at,Instant.now(),
                    "Customer's accounts; only history available by payment creation time. Latest 20 settled rows are shown; aggregates cover all matching rows.",List.copyOf(facts),recent);
            }
        }catch(AppError e){throw e;}catch(Exception e){throw new AppError(503,"Read-only payment evidence is unavailable. Check database connectivity and configuration.");}
    }
    public void requireUnchanged(long id,long version) {
        try(Connection c=connections.open();PreparedStatement q=prepare(c,"select state,version from transaction_db where transaction_id=?",id);ResultSet r=q.executeQuery()) {
            if(!r.next() || !"HARD_HOLD".equals(r.getString(1)) || r.getLong(2)!=version)throw new AppError(409,"Payment changed during review. Refresh before continuing.");
        }catch(AppError e){throw e;}catch(Exception e){throw new AppError(503,"Unable to confirm the payment's current state");}
    }
    private List<HistoryRow> recent(Connection c,long owner,LocalDateTime start,LocalDateTime at,long id)throws SQLException {
        var rows=new ArrayList<HistoryRow>();
        try(PreparedStatement q=prepare(c,"select t.transaction_id,t.transaction_ref,t.amount,t.created_at,t.settled_at from transaction_db t join account a on a.account_id=t.from_account_id "
            +"where a.user_id=? and t.state='SETTLED' and t.settled_at>=? and t.settled_at<=? and t.transaction_id<>? order by t.settled_at desc,t.transaction_id desc fetch first 20 rows only",owner,start,at,id);ResultSet r=q.executeQuery()) {
            while(r.next())rows.add(new HistoryRow(r.getLong(1),r.getString(2),money(r.getBigDecimal(3)),time(r,"created_at"),time(r,"settled_at")));
        }return List.copyOf(rows);
    }
    private Object[] aggregate(Connection c,String sql,Object...params)throws SQLException {
        try(PreparedStatement q=prepare(c,sql,params);ResultSet r=q.executeQuery()){r.next();return new Object[]{r.getLong(1),r.getBigDecimal(2),r.getBigDecimal(3),r.getBigDecimal(4)};}
    }
    private long count(Connection c,String sql,Object...params)throws SQLException {
        try(PreparedStatement q=prepare(c,sql,params);ResultSet r=q.executeQuery()){r.next();return r.getLong(1);}
    }
    private PreparedStatement prepare(Connection c,String sql,Object...params)throws SQLException {
        PreparedStatement q=c.prepareStatement(sql);q.setQueryTimeout(8);
        for(int i=0;i<params.length;i++)q.setObject(i+1,params[i] instanceof LocalDateTime dt?Timestamp.valueOf(dt):params[i]);return q;
    }
    private static LocalDateTime time(ResultSet r,String name)throws SQLException{Timestamp time=r.getTimestamp(name);return time==null?null:time.toLocalDateTime();}
    private static String money(BigDecimal value){return value==null?"0.00":value.setScale(2,RoundingMode.UNNECESSARY).toPlainString();}
    private static String mask(String value){return value==null?"Unavailable":"••••"+value.substring(Math.max(0,value.length()-4));}
}
