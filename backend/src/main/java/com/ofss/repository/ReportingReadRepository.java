package com.ofss.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.ofss.beans.TransactionState;
import com.ofss.common.api.PagedResponse;
import com.ofss.dto.audit.AuditReconciliationResponse;
import com.ofss.dto.audit.AuditExceptionResponse;
import com.ofss.dto.admin.OperationalDashboardResponse;
import com.ofss.dto.admin.OperationalFailureResponse;
import com.ofss.dto.riskreview.RiskDashboardResponse;
import com.ofss.dto.audit.AuditDashboardResponse;

/** Read projections only; no entity mutation or worker invocation. */
@Repository
public class ReportingReadRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public ReportingReadRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = java.util.Objects.requireNonNull(jdbc, "jdbc is required");
    }

    public static PageRequest pageRequest(int page, int size) {
        if (page < 0 || size < 1 || size > 100)
            throw new IllegalArgumentException("page must be non-negative and size between 1 and 100");
        return PageRequest.of(page, size);
    }

    public static void validateRange(Long id, OffsetDateTime from, OffsetDateTime to) {
        if (id != null && id <= 0) throw new IllegalArgumentException("filter ID must be positive");
        if (from != null && to != null && !from.isBefore(to))
            throw new IllegalArgumentException("from must be earlier than to");
    }

    public PagedResponse<AuditReconciliationResponse.Ledger> ledger(
            Long postingId, String status, int page, int size) {
        validateRange(postingId, null, null);
        validateStatus(status, Set.of("INCOMPLETE", "BALANCED", "MISMATCH"));
        var args = new MapSqlParameterSource();
        String where = filters(args, "posting_id", postingId, status, null, null, null);
        return page("""
                select posting_id, posting_reference, posting_type, transaction_id, posting_status,
                       posting_amount, currency_code, expected_entry_count, actual_entry_count,
                       debit_count, credit_count, debit_total, credit_total, distinct_account_count,
                       reconciliation_status, created_at, posted_at, updated_at
                """, " from SAFEPAY_OWNER.VW_LEDGER_RECONCILIATION", where, "posting_id asc", args,
                page, size, (r, n) -> new AuditReconciliationResponse.Ledger(
                        r.getString("posting_id"), r.getString("posting_reference"), r.getString("posting_type"),
                        r.getString("transaction_id"), r.getString("posting_status"), r.getBigDecimal("posting_amount"),
                        r.getString("currency_code"), r.getLong("expected_entry_count"), r.getLong("actual_entry_count"),
                        r.getLong("debit_count"), r.getLong("credit_count"), r.getBigDecimal("debit_total"),
                        r.getBigDecimal("credit_total"), r.getLong("distinct_account_count"),
                        r.getString("reconciliation_status"), time(r, "created_at"), time(r, "posted_at"),
                        time(r, "updated_at")));
    }

    public PagedResponse<AuditReconciliationResponse.Reservation> reservations(
            Long accountId, String status, int page, int size) {
        validateRange(accountId, null, null);
        validateStatus(status, Set.of("MATCH", "MISMATCH"));
        var args = new MapSqlParameterSource();
        String where = filters(args, "account_id", accountId, status, null, null, null);
        return page("""
                select account_id, masked_account_number, currency_code, stored_reserved_amount,
                       calculated_reserved_amount, reservation_difference, reconciliation_status, updated_at
                """, " from SAFEPAY_OWNER.VW_RESERVATION_RECONCILIATION", where, "account_id asc", args,
                page, size, (r, n) -> new AuditReconciliationResponse.Reservation(
                        r.getString("account_id"), r.getString("masked_account_number"), r.getString("currency_code"),
                        r.getBigDecimal("stored_reserved_amount"), r.getBigDecimal("calculated_reserved_amount"),
                        r.getBigDecimal("reservation_difference"), r.getString("reconciliation_status"),
                        time(r, "updated_at")));
    }

    // Both sources expose only controlled columns. All exception statuses retain their evidence.
    private static final String FAILURE_FROM = """
             from (
                select 'TRANSACTION' as source, transaction_exception_id as record_id, transaction_id,
                       processing_stage, status, error_code, retryable_flag,
                       retry_count as attempt_count, next_retry_at as next_attempt_at,
                       last_occurred_at as event_at, resolved_at, correlation_id
                  from SAFEPAY_OWNER.TRANSACTION_EXCEPTION
                union all
                select 'NOTIFICATION', notification_id, transaction_id,
                       cast(null as varchar2(30)), delivery_status, last_error_code,
                       case when delivery_status = 'RETRY_PENDING' then 'Y' else 'N' end,
                       attempt_count, next_attempt_at, nvl(failed_at, updated_at),
                       cast(null as timestamp with time zone), correlation_id
                  from SAFEPAY_OWNER.APP_NOTIFICATION
                 where delivery_status in ('RETRY_PENDING', 'FAILED')
             ) failures
            """;

    public PagedResponse<OperationalFailureResponse> failures(OperationalFailureResponse.Source source,
            Long transactionId, OffsetDateTime from, OffsetDateTime to, int page, int size) {
        validateRange(transactionId, from, to);
        var args = new MapSqlParameterSource();
        String where = filters(args, "transaction_id", transactionId, null, "event_at", from, to);
        if (source != null) {
            where += " and source = :source";
            args.addValue("source", source.name());
        }
        return page("""
                select source, record_id, transaction_id, processing_stage, status, error_code, retryable_flag,
                       attempt_count, next_attempt_at, event_at, resolved_at, correlation_id
                """, FAILURE_FROM, where, "event_at desc, source asc, record_id desc", args, page, size,
                (r, n) -> new OperationalFailureResponse(
                        OperationalFailureResponse.Source.valueOf(r.getString("source")), r.getString("record_id"),
                        r.getString("transaction_id"), r.getString("processing_stage"), r.getString("status"),
                        r.getString("error_code"), AuditExceptionResponse.safeExplanation(r.getString("error_code")),
                        "Y".equals(r.getString("retryable_flag")), r.getInt("attempt_count"),
                        time(r, "next_attempt_at"), time(r, "event_at"), time(r, "resolved_at"),
                        r.getString("correlation_id")));
    }

    // One SELECT gives all counters one Oracle statement snapshot and a database observation time.
    public OperationalDashboardResponse statistics() {
        StringBuilder sql = new StringBuilder("select SYSTIMESTAMP as observed_at");
        for (TransactionState state : TransactionState.values()) {
            sql.append(", (select count(*) from SAFEPAY_OWNER.PAYMENT_TRANSACTION where state = '")
                    .append(state.name()).append("') as p_").append(state.name());
        }
        for (String status : EXCEPTION_STATUSES) {
            sql.append(", (select count(*) from SAFEPAY_OWNER.TRANSACTION_EXCEPTION where status = '")
                    .append(status).append("') as e_").append(status);
        }
        for (String status : NOTIFICATION_STATUSES) {
            sql.append(", (select count(*) from SAFEPAY_OWNER.APP_NOTIFICATION where delivery_status = '")
                    .append(status).append("') as n_").append(status);
        }
        sql.append("""
                , (select count(*) from SAFEPAY_OWNER.RISK_REVIEW where status = 'PENDING') as pending_reviews
                , (select count(*) from SAFEPAY_OWNER.PAYMENT_TRANSACTION
                    where state = 'PROTECTED' and risk_tier in ('MEDIUM','HIGH')
                      and protected_until is not null and protected_until <= SYSTIMESTAMP) as expired_protected
                , (select count(*) from SAFEPAY_OWNER.PAYMENT_TRANSACTION payment
                    where payment.state = 'RELEASED'
                      and (
                          not exists (
                              select 1 from SAFEPAY_OWNER.TRANSACTION_EXCEPTION history
                               where history.transaction_id = payment.transaction_id
                                 and history.processing_stage = 'SETTLEMENT')
                          or exists (
                              select 1 from SAFEPAY_OWNER.TRANSACTION_EXCEPTION exception_log
                               where exception_log.transaction_id = payment.transaction_id
                                 and exception_log.processing_stage = 'SETTLEMENT'
                                 and exception_log.status = 'RETRY_PENDING'
                                 and exception_log.next_retry_at <= SYSTIMESTAMP
                                 and exception_log.transaction_exception_id = (
                                     select max(latest.transaction_exception_id)
                                       from SAFEPAY_OWNER.TRANSACTION_EXCEPTION latest
                                      where latest.transaction_id = payment.transaction_id
                                        and latest.processing_stage = 'SETTLEMENT')
                          )
                      )) as due_settlement
                 from dual
                """);
        return jdbc.queryForObject(sql.toString(), new MapSqlParameterSource(), (r, n) -> {
            var payments = new EnumMap<TransactionState, Long>(TransactionState.class);
            for (TransactionState state : TransactionState.values()) payments.put(state, r.getLong("p_" + state.name()));
            Map<String, Long> exceptions = new LinkedHashMap<>();
            for (String status : EXCEPTION_STATUSES) exceptions.put(status, r.getLong("e_" + status));
            Map<String, Long> notifications = new LinkedHashMap<>();
            for (String status : NOTIFICATION_STATUSES) notifications.put(status, r.getLong("n_" + status));
            return new OperationalDashboardResponse(time(r, "observed_at"), payments, r.getLong("pending_reviews"),
                    exceptions, notifications, r.getLong("expired_protected"), r.getLong("due_settlement"));
        });
    }

    public RiskDashboardResponse riskDashboard() {
        OffsetDateTime observedAt = jdbc.queryForObject("select SYSTIMESTAMP from dual",
                new MapSqlParameterSource(), OffsetDateTime.class);
        Map<String, Long> reviewStatuses = groupedCounts(
                "select status, count(*) as total from SAFEPAY_OWNER.RISK_REVIEW group by status");
        Map<String, Long> riskTiers = groupedCounts(
                "select risk_tier as status, count(*) as total from SAFEPAY_OWNER.PAYMENT_TRANSACTION where risk_tier is not null group by risk_tier");
        BigDecimal pendingAmount = jdbc.queryForObject("""
                select nvl(sum(payment.amount), 0) from SAFEPAY_OWNER.RISK_REVIEW review
                  join SAFEPAY_OWNER.PAYMENT_TRANSACTION payment on payment.transaction_id = review.transaction_id
                 where review.status = 'PENDING' and payment.state = 'PENDING_RISK_REVIEW'
                """, new MapSqlParameterSource(), java.math.BigDecimal.class);
        long pending = reviewStatuses.getOrDefault("PENDING", 0L);
        return new RiskDashboardResponse(observedAt, pending, pendingAmount, reviewStatuses, riskTiers);
    }

    public AuditDashboardResponse auditDashboard() {
        OffsetDateTime observedAt = jdbc.queryForObject("select SYSTIMESTAMP from dual",
                new MapSqlParameterSource(), OffsetDateTime.class);
        return new AuditDashboardResponse(observedAt,
                groupedCounts("select reconciliation_status as status, count(*) as total from SAFEPAY_OWNER.VW_LEDGER_RECONCILIATION group by reconciliation_status"),
                groupedCounts("select reconciliation_status as status, count(*) as total from SAFEPAY_OWNER.VW_RESERVATION_RECONCILIATION group by reconciliation_status"),
                groupedCounts("select status, count(*) as total from SAFEPAY_OWNER.TRANSACTION_EXCEPTION group by status"));
    }

    private Map<String, Long> groupedCounts(String sql) {
        Map<String, Long> result = new LinkedHashMap<>();
        jdbc.query(sql, (org.springframework.jdbc.core.RowCallbackHandler)
                rs -> result.put(rs.getString("status"), rs.getLong("total")));
        return result;
    }

    private static final String[] EXCEPTION_STATUSES = {"OPEN", "RETRY_PENDING", "MANUAL_REVIEW"};
    private static final String[] NOTIFICATION_STATUSES = {"PENDING", "RETRY_PENDING", "FAILED"};

    private static void validateStatus(String status, Set<String> allowed) {
        if (status != null && !allowed.contains(status))
            throw new IllegalArgumentException("Unsupported reconciliationStatus");
    }

    private static String filters(MapSqlParameterSource args, String idColumn, Long id, String status,
            String timeColumn, OffsetDateTime from, OffsetDateTime to) {
        String where = " where 1 = 1";
        if (id != null) { where += " and " + idColumn + " = :id"; args.addValue("id", id); }
        if (status != null) { where += " and reconciliation_status = :status"; args.addValue("status", status); }
        if (from != null) {
            where += " and " + timeColumn + " >= :from";
            args.addValue("from", from.withOffsetSameInstant(ZoneOffset.UTC), Types.TIMESTAMP_WITH_TIMEZONE);
        }
        if (to != null) {
            where += " and " + timeColumn + " < :to";
            args.addValue("to", to.withOffsetSameInstant(ZoneOffset.UTC), Types.TIMESTAMP_WITH_TIMEZONE);
        }
        return where;
    }

    private <T> PagedResponse<T> page(String select, String from, String where, String order,
            MapSqlParameterSource args, int page, int size, RowMapper<T> mapper) {
        PageRequest request = pageRequest(page, size);
        Long total = jdbc.queryForObject("select count(*)" + from + where, args, Long.class);
        args.addValue("offset", request.getOffset()).addValue("limit", size);
        var rows = jdbc.query(select + from + where + " order by " + order
                + " offset :offset rows fetch next :limit rows only", args, mapper);
        return PagedResponse.from(new PageImpl<>(rows, request, total == null ? 0 : total), item -> item);
    }

    private static OffsetDateTime time(ResultSet result, String column) throws SQLException {
        OffsetDateTime value = result.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.withOffsetSameInstant(ZoneOffset.UTC);
    }
}
