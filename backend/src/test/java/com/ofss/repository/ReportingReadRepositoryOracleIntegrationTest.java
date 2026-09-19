package com.ofss.repository;

import static org.assertj.core.api.Assertions.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import com.ofss.beans.*;
import com.ofss.dto.admin.OperationalFailureResponse;
import com.ofss.dto.audit.*;

/** Reads the existing showcase data; no fixture, schema or financial writes. */
@SpringBootTest
@Transactional(readOnly = true)
class ReportingReadRepositoryOracleIntegrationTest {
    @Autowired private ReportingReadRepository reporting;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private LedgerPostingDao postings;
    @Autowired private LedgerEntryDao entries;
    @Autowired private TransactionExceptionDao exceptions;
    @Autowired private RiskPolicyDao policies;
    @Autowired private RiskPolicyBandDao bands;

    @Test void ledgerProjectionMapsOracleNumbersTimestampsAndMatchingCounts() {
        var page = reporting.ledger(null, null, 0, 2);
        assertThat(page.totalElements()).isEqualTo(count("select count(*) from SAFEPAY_OWNER.VW_LEDGER_RECONCILIATION"));
        assertThat(page.items()).hasSize((int) Math.min(2L, page.totalElements()));
        assertThat(page.items()).isNotEmpty();
        var first = page.items().getFirst();
        assertThat(first.createdAt()).isNotNull();
        assertThat(first.postingAmount()).isInstanceOf(BigDecimal.class);
        var filtered = reporting.ledger(Long.valueOf(first.postingId()), first.reconciliationStatus(), 0, 1);
        assertThat(filtered.totalElements()).isEqualTo(1);
        assertThat(filtered.items()).containsExactly(first);
        assertThat(reporting.ledger(Long.valueOf(first.postingId()), null, 1, 1).items()).isEmpty();
    }

    @Test void reservationProjectionMatchesExactStoredAndCalculatedAmounts() {
        var page = reporting.reservations(null, null, 0, 100);
        assertThat(page.totalElements()).isEqualTo(count("select count(*) from SAFEPAY_OWNER.ACCOUNT"));
        assertThat(page.items()).isNotEmpty();
        for (var item : page.items()) {
            assertThat(item.reservationDifference()).isEqualByComparingTo(
                    item.storedReservedAmount().subtract(item.calculatedReservedAmount()));
            assertThat(item.updatedAt()).isNotNull();
            assertThat(item.maskedAccountNumber()).contains("*");
        }
        var first = page.items().getFirst();
        assertThat(reporting.reservations(Long.valueOf(first.accountId()), first.reconciliationStatus(), 0, 1)
                .totalElements()).isEqualTo(1);
    }

    @Test void operationalStatisticsMatchIndependentCurrentStateCounts() {
        var stats = reporting.statistics();
        assertThat(stats.observedAt()).isNotNull();
        for (TransactionState state : TransactionState.values()) {
            assertThat(stats.paymentsByState().get(state)).isEqualTo(jdbc.queryForObject(
                    "select count(*) from SAFEPAY_OWNER.PAYMENT_TRANSACTION where state = ?", Long.class, state.name()));
        }
        assertThat(stats.pendingReviews()).isEqualTo(count(
                "select count(*) from SAFEPAY_OWNER.RISK_REVIEW where status = 'PENDING'"));
        stats.exceptionsByStatus().forEach((status, total) -> assertThat(total).isEqualTo(jdbc.queryForObject(
                "select count(*) from SAFEPAY_OWNER.TRANSACTION_EXCEPTION where status = ?", Long.class, status)));
        stats.notificationsByStatus().forEach((status, total) -> assertThat(total).isEqualTo(jdbc.queryForObject(
                "select count(*) from SAFEPAY_OWNER.APP_NOTIFICATION where delivery_status = ?", Long.class, status)));
    }

    @Test void failuresUnionSourceFiltersCountsAndOracleTimestampBindingsWork() {
        long transactionCount = count("select count(*) from SAFEPAY_OWNER.TRANSACTION_EXCEPTION");
        long notificationCount = count("select count(*) from SAFEPAY_OWNER.APP_NOTIFICATION where delivery_status in ('RETRY_PENDING','FAILED')");
        assertThat(reporting.failures(null, null, null, null, 0, 2).totalElements())
                .isEqualTo(transactionCount + notificationCount);
        assertThat(reporting.failures(OperationalFailureResponse.Source.TRANSACTION, null, null, null, 0, 2).totalElements())
                .isEqualTo(transactionCount);
        assertThat(reporting.failures(OperationalFailureResponse.Source.NOTIFICATION, null, null, null, 0, 2).totalElements())
                .isEqualTo(notificationCount);
        var from = OffsetDateTime.parse("2000-01-01T00:00:00Z");
        var page = reporting.failures(null, null, from, from.plusYears(100), 0, 100);
        assertThat(page.totalElements()).isEqualTo(transactionCount + notificationCount);
        assertThat(page.items()).allSatisfy(item -> assertThat(item.occurredAt()).isNotNull());
    }

    @Test void ledgerJpaReadsIncludeOpeningPostingsAndOrderedEntries() {
        var page = postings.searchForAudit(null, null, null, null, PageRequest.of(0, 100));
        assertThat(page.getTotalElements()).isEqualTo(count("select count(*) from SAFEPAY_OWNER.LEDGER_POSTING"));
        var openingId = jdbc.queryForObject(
                "select min(posting_id) from SAFEPAY_OWNER.LEDGER_POSTING where posting_type = 'OPENING_BALANCE'", Long.class);
        assertThat(openingId).isNotNull();
        var opening = postings.findByPostingId(openingId).orElseThrow();
        assertThat(AuditLedgerPostingResponse.Header.from(opening).transactionId()).isNull();
        var lines = entries.findAllByPosting_PostingIdOrderByLineNumberAsc(openingId);
        assertThat(lines).isNotEmpty();
        assertThat(lines.stream().map(LedgerEntry::getLineNumber).toList()).isSorted();
        assertThat(lines.stream().map(AuditLedgerPostingResponse.Entry::from).toList())
                .allSatisfy(line -> assertThat(line.maskedAccountNumber()).contains("*"));
        assertThat(postings.searchForAudit(Long.MAX_VALUE, null, null, null, PageRequest.of(0, 1))).isEmpty();
    }

    @Test void exceptionJpaSearchUsesLastOccurrenceAndNullPostingIsSafe() {
        var page = exceptions.searchForAudit(null, null, null, null, null, PageRequest.of(0, 100));
        assertThat(page.getTotalElements()).isEqualTo(count("select count(*) from SAFEPAY_OWNER.TRANSACTION_EXCEPTION"));
        for (var exception : page) {
            var detail = exceptions.findByTransactionExceptionId(exception.getTransactionExceptionId()).orElseThrow();
            assertThat(AuditExceptionResponse.from(detail).exceptionId()).isEqualTo(exception.getTransactionExceptionId().toString());
        }
        assertThat(exceptions.searchForAudit(Long.MAX_VALUE, TransactionProcessingStage.SETTLEMENT,
                TransactionExceptionStatus.OPEN, OffsetDateTime.parse("2000-01-01T00:00:00Z"),
                OffsetDateTime.parse("2100-01-01T00:00:00Z"), PageRequest.of(0, 1))).isEmpty();
    }

    @Test void policiesReadStoredBandsIncludingUnboundedTopBand() {
        var page = policies.searchForAudit(null, PageRequest.of(0, 20));
        assertThat(page.getTotalElements()).isEqualTo(count("select count(*) from SAFEPAY_OWNER.RISK_POLICY"));
        var policy = policies.findByPolicyVersion("AMOUNT_ONLY_V1").orElseThrow();
        var stored = bands.findAllForPolicy(policy.getRiskPolicyId());
        assertThat(stored).hasSize(4);
        assertThat(stored.stream().map(RiskPolicyBand::getDisplayOrder).toList()).isSorted();
        var top = AuditRiskPolicyResponse.Band.from(stored.getLast());
        assertThat(top.maximumAmount()).isNull();
        assertThat(top.otpRequired()).isTrue();
        assertThat(top.riskReviewRequired()).isTrue();
        assertThat(policies.searchForAudit(RiskPolicyStatus.RETIRED, PageRequest.of(0, 1)).getTotalElements())
                .isEqualTo(count("select count(*) from SAFEPAY_OWNER.RISK_POLICY where status = 'RETIRED'"));
    }

    @Test void emptyReconciliationAndFailurePagesKeepRequestedSize() {
        assertThat(reporting.ledger(Long.MAX_VALUE, "MISMATCH", 0, 7).items()).isEmpty();
        assertThat(reporting.reservations(Long.MAX_VALUE, "MATCH", 0, 7).items()).isEmpty();
        var page = reporting.failures(null, Long.MAX_VALUE, null, null, 0, 7);
        assertThat(page.items()).isEmpty();
        assertThat(page.size()).isEqualTo(7);
        assertThat(page.totalElements()).isZero();
    }

    private long count(String sql) { return jdbc.queryForObject(sql, Long.class); }
}
