package com.ofss.repository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.sql.ResultSet;
import org.junit.jupiter.api.Test;

class AdminReportRepositoryTest {
    @Test
    void oracleAggregateMappingPreservesLargeCountsAndExactDecimalAmounts() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("total_transactions")).thenReturn(3000000000L);
        when(rs.getLong("settled_transactions")).thenReturn(8L);
        when(rs.getLong("protected_transactions")).thenReturn(3L);
        when(rs.getLong("cancelled_transactions")).thenReturn(2L);
        when(rs.getLong("rejected_transactions")).thenReturn(1L);
        when(rs.getLong("hard_holds")).thenReturn(4L);
        when(rs.getLong("high_risk_transactions")).thenReturn(9L);
        BigDecimal total = new BigDecimal("99999999999999999999.99");
        when(rs.getBigDecimal("total_amount")).thenReturn(total);
        when(rs.getBigDecimal("settled_amount")).thenReturn(new BigDecimal("0.01"));
        var result = AdminReportRepository.mapSummary(rs);
        assertEquals(3000000000L, result.totalTransactions());
        assertEquals(8, result.settledTransactions());
        assertEquals(3, result.protectedTransactions());
        assertEquals(2, result.cancelledTransactions());
        assertEquals(1, result.rejectedTransactions());
        assertEquals(4, result.hardHolds());
        assertEquals(9, result.highRiskTransactions());
        assertEquals(total, result.totalAmount());
        assertEquals(new BigDecimal("0.01"), result.settledAmount());
    }
}
