package com.ofss.beans;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class AdminReportDtos {
    private AdminReportDtos() {}
    public record Summary(long totalTransactions, long settledTransactions, long protectedTransactions,
            long cancelledTransactions, long rejectedTransactions, long hardHolds, long highRiskTransactions,
            BigDecimal totalAmount, BigDecimal settledAmount) {}
    public record Daily(LocalDate date, Summary summary) {}
}
