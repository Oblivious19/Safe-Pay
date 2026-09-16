package com.ofss.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import com.ofss.beans.AdminReportDtos.*;

/** SELECT-only repository; reporting views are installed manually, not by Hibernate. */
@Repository
public class AdminReportRepository {
    private final JdbcTemplate jdbc;
    public AdminReportRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Summary summary() {
        return jdbc.queryForObject("SELECT * FROM vw_sp_tx_report_totals", (rs, row) -> mapSummary(rs));
    }

    public List<Daily> daily(LocalDate from, LocalDate to) {
        return jdbc.query("SELECT * FROM vw_sp_tx_report_daily WHERE report_day >= ? AND report_day <= ? ORDER BY report_day",
                (rs, row) -> new Daily(rs.getDate("report_day").toLocalDate(), mapSummary(rs)),
                Date.valueOf(from), Date.valueOf(to));
    }

    static Summary mapSummary(ResultSet rs) throws SQLException {
        return new Summary(rs.getLong("total_transactions"), rs.getLong("settled_transactions"),
                rs.getLong("protected_transactions"), rs.getLong("cancelled_transactions"),
                rs.getLong("rejected_transactions"), rs.getLong("hard_holds"), rs.getLong("high_risk_transactions"),
                rs.getBigDecimal("total_amount"), rs.getBigDecimal("settled_amount"));
    }
}
