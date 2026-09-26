package com.ofss.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import com.ofss.beans.AdminReportDtos.*;
import com.ofss.services.AdminReportService;

@RestController
@RequestMapping("/api/admin/reports/transactions")
public class AdminReportController {
    private final AdminReportService reports;
    public AdminReportController(AdminReportService reports) { this.reports = reports; }

    @GetMapping
    public TransactionPage transactions(@RequestParam(required = false) String state,
            @RequestParam(required = false) String risk, @RequestParam(required = false) String category,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sort) {
        return reports.transactions(state, risk, category, query, from, to, page, size, sort);
    }

    @GetMapping("/summary")
    public Summary summary() { return reports.summary(); }

    @GetMapping
    public TransactionPage transactions(@RequestParam(required=false) String state, @RequestParam(required=false) String risk,
            @RequestParam(required=false) String query, @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size, @RequestParam(defaultValue="createdAt") String sort) {
        return reports.transactions(state, risk, query, page, size, sort);
    }

    @GetMapping("/daily")
    public List<Daily> daily(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reports.daily(from, to);
    }

    @ExceptionHandler({IllegalArgumentException.class, MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class})
    ResponseEntity<Map<String, String>> invalidRange(Exception ignored) {
        return ResponseEntity.badRequest().body(Map.of("message",
                "Check the filters and page limits. Use dates as YYYY-MM-DD in order; daily reports allow at most 366 days."));
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<Map<String, String>> unavailable(DataAccessException ignored) {
        return ResponseEntity.status(503).body(Map.of("message", "Reporting is temporarily unavailable"));
    }
}
