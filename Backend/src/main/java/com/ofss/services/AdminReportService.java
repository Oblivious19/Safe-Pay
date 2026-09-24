package com.ofss.services;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import com.ofss.beans.TransactionDb;
import com.ofss.beans.TransactionState;
import com.ofss.beans.RiskTier;
import com.ofss.repository.TransactionDao;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ofss.beans.AdminReportDtos.*;
import com.ofss.repository.AdminReportRepository;

@Service
@Transactional(readOnly = true)
public class AdminReportService {
    private final AdminReportRepository reports;
    private final TransactionDao transactions;
    public AdminReportService(AdminReportRepository reports, TransactionDao transactions) { this.reports = reports; this.transactions = transactions; }
    public Summary summary() { return reports.summary(); }
    public List<Daily> daily(LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to) || ChronoUnit.DAYS.between(from, to) >= 366) {
            throw new IllegalArgumentException("Use from and to dates in order, spanning at most 366 days");
        }
        return reports.daily(from, to);
    }
    public TransactionPage transactions(String state, String risk, String query, int page, int size, String sort) {
        if (page < 0 || size < 1 || size > 100 || !("createdAt".equals(sort) || "amount".equals(sort))) throw new IllegalArgumentException("Invalid transaction page");
        TransactionState parsedState = state == null || state.isBlank() ? null : TransactionState.valueOf(state);
        RiskTier parsedRisk = risk == null || risk.isBlank() ? null : RiskTier.valueOf(risk);
        String text = query == null || query.isBlank() ? null : query.trim();
        var result = transactions.findAdminTransactions(parsedState, parsedRisk, text, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, sort)));
        return new TransactionPage(result.getContent().stream().map(this::row).toList(), page, size, result.getTotalElements(), result.getTotalPages());
    }
    private TransactionRow row(TransactionDb t) { return new TransactionRow(t.getTransactionId(), t.getTransactionRef(), t.getFromAccount().getUser().getName(), t.getFromAccount().getUser().getEmail(), t.getBeneficiary().getBeneficiaryName(), t.getAmount(), t.getState().name(), t.getRiskTier().name(), t.getCreatedAt()); }
}
