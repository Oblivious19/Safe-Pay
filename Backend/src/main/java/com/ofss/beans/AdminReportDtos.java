package com.ofss.beans;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class AdminReportDtos {
    private AdminReportDtos() {}
    public record Summary(long totalTransactions, long settledTransactions, long protectedTransactions,
            long cancelledTransactions, long rejectedTransactions, long hardHolds, long highRiskTransactions,
            BigDecimal totalAmount, BigDecimal settledAmount) {}
    public record Daily(LocalDate date, Summary summary) {}
    public record TransactionRow(Long transactionId, String transactionRef, String customerName, String customerEmail,
            String beneficiaryName, BigDecimal amount, String state, String riskTier, LocalDateTime createdAt) {}
    public record TransactionPage(List<TransactionRow> items, int page, int size, long totalItems, int totalPages) {}
}
