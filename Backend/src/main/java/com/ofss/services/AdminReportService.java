package com.ofss.services;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ofss.beans.AdminReportDtos.*;
import com.ofss.repository.AdminReportRepository;

@Service
@Transactional(readOnly = true)
public class AdminReportService {
    private final AdminReportRepository reports;
    public AdminReportService(AdminReportRepository reports) { this.reports = reports; }
    public Summary summary() { return reports.summary(); }
    public List<Daily> daily(LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to) || ChronoUnit.DAYS.between(from, to) >= 366) {
            throw new IllegalArgumentException("Use from and to dates in order, spanning at most 366 days");
        }
        return reports.daily(from, to);
    }
}
