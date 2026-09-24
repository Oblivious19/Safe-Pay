package com.ofss.services;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ofss.beans.AdminReportDtos.*;
import com.ofss.repository.AdminReportRepository;
import com.ofss.repository.TransactionDao;
import com.ofss.beans.TransactionState;
import com.ofss.beans.RiskTier;
import com.ofss.beans.PaymentCategory;

@Service
@Transactional(readOnly = true)
public class AdminReportService {
    private final AdminReportRepository reports;
    private final TransactionDao transactions;
    public AdminReportService(AdminReportRepository reports, TransactionDao transactions) {
        this.reports = reports; this.transactions = transactions;
    }
    public TransactionPage transactions(String state, String risk, String category, String query,
            LocalDate from, LocalDate to, int page, int size, String sort) {
        if (page < 0 || size < 1 || size > 100 || (from != null && to != null && from.isAfter(to))
                || (query != null && query.length() > 100)
                || !("createdAt".equals(sort) || "amount".equals(sort))) {
            throw new IllegalArgumentException("Choose valid transaction filters and page limits");
        }
        var result = transactions.findAdminTransactions(
                blank(state) ? null : TransactionState.valueOf(state),
                blank(risk) ? null : RiskTier.valueOf(risk),
                blank(category) ? null : PaymentCategory.valueOf(category),
                blank(query) ? null : query.strip().toLowerCase(Locale.ROOT).replace("!", "!!"),
                from == null ? null : from.atStartOfDay(), to == null ? null : to.plusDays(1).atStartOfDay(),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, sort, "transactionId")));
        var items = result.getContent().stream().map(t -> new TransactionRow(t.getTransactionId(),
                t.getTransactionRef(), t.getFromAccount().getUser().getName(), t.getFromAccount().getUser().getEmail(),
                t.getBeneficiary().getBeneficiaryName(), t.getAmount(), t.getState().name(),
                t.getRiskTier().name(), t.getCategory() == null ? null : t.getCategory().name(), t.getCreatedAt())).toList();
        return new TransactionPage(items, page, size, result.getTotalElements(), result.getTotalPages());
    }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    public Summary summary() { return reports.summary(); }
    public List<Daily> daily(LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to) || ChronoUnit.DAYS.between(from, to) >= 366) {
            throw new IllegalArgumentException("Use from and to dates in order, spanning at most 366 days");
        }
        return reports.daily(from, to);
    }
}
