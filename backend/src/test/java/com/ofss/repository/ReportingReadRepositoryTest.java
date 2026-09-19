package com.ofss.repository;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import com.ofss.dto.admin.OperationalFailureResponse;

class ReportingReadRepositoryTest {
    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final ReportingReadRepository reporting = new ReportingReadRepository(jdbc);
    @BeforeEach void setup() {
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class))).thenReturn(0L);
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class),
                org.mockito.ArgumentMatchers.<RowMapper<Object>>any())).thenReturn(List.of());
    }
    @Test void rejectsInvalidStatusesAndBoundsBeforeSql() {
        assertThatThrownBy(() -> reporting.ledger(null, "MATCH", 0, 20)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> reporting.reservations(null, "BALANCED", 0, 20)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> reporting.ledger(null, "x' OR 1=1 --", 0, 20)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> reporting.reservations(-1L, null, 0, 20)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> reporting.failures(null, null, null, null, 0, 101)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(jdbc);
    }
    @Test void reservationProjectionOmitsBalancesAndPageOffsetUsesLong() {
        var page = reporting.reservations(7L, "MISMATCH", Integer.MAX_VALUE, 100);
        assertThat(page.items()).isEmpty();
        var sql = ArgumentCaptor.forClass(String.class);
        var args = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).query(sql.capture(), args.capture(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any());
        assertThat(sql.getValue()).doesNotContain("current_balance", "available_balance", "select *")
                .contains("account_id = :id", "reconciliation_status = :status", "order by account_id asc");
        assertThat(args.getValue().getValue("offset")).isEqualTo(214748364700L);
        assertThat(args.getValue().getValue("id")).isEqualTo(7L);
    }
    @Test void combinedFailureCountAndPageShareFiltersAndHaveUniqueOrdering() {
        var from = OffsetDateTime.parse("2026-09-19T05:30:00+05:30");
        reporting.failures(OperationalFailureResponse.Source.NOTIFICATION, 9L, from, from.plusDays(1), 2, 5);
        var count = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForObject(count.capture(), any(MapSqlParameterSource.class), eq(Long.class));
        var page = ArgumentCaptor.forClass(String.class);
        var args = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).query(page.capture(), args.capture(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any());
        String filters = " where 1 = 1 and transaction_id = :id and event_at >= :from and event_at < :to and source = :source";
        assertThat(count.getValue()).endsWith(filters);
        assertThat(page.getValue()).contains(filters, "union all", "event_at desc, source asc, record_id desc")
                .doesNotContain("error_message", "recipient_user_id", "deduplication_key");
        assertThat(args.getValue().getValue("from")).isEqualTo(OffsetDateTime.parse("2026-09-19T00:00:00Z"));
    }
    @Test void invalidRangeNeverExecutesSql() {
        var now = OffsetDateTime.now();
        assertThatThrownBy(() -> reporting.failures(null, null, now, now.minusSeconds(1), 0, 20))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(jdbc);
    }
}
