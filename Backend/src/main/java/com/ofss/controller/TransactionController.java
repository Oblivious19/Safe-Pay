package com.ofss.controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ofss.beans.TransactionDb;
import com.ofss.services.TransactionService;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping("/initiate")
    public Map<String, Object> initiate(@RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestParam Long fromAccountId, @RequestParam Long beneficiaryId,
            @RequestParam BigDecimal amount, @RequestParam(required = false) String purpose,
            @RequestParam String userEmail) {
        return toResponse(transactionService.initiate(fromAccountId, beneficiaryId, amount, purpose,
                idempotencyKey, userEmail));
    }

    @GetMapping("/{transactionId}")
    public Map<String, Object> getTransaction(@PathVariable Long transactionId,
            @RequestParam String userEmail) {
        return toResponse(transactionService.getTransaction(transactionId, userEmail));
    }

    @GetMapping
    public List<Map<String, Object>> getTransactions(@RequestParam(required = false) String state,
            @RequestParam String userEmail) {
        return transactionService.getTransactions(state, userEmail).stream()
                .map(this::toResponse).toList();
    }

    @PostMapping("/{transactionId}/cancel")
    public Map<String, Object> cancel(@PathVariable Long transactionId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestParam String userEmail) {
        return toResponse(transactionService.cancel(transactionId, idempotencyKey, userEmail));
    }

    private Map<String, Object> toResponse(TransactionDb transaction) {
        return Map.of("transactionId", transaction.getTransactionId(),
                "transactionRef", transaction.getTransactionRef(), "state", transaction.getState().name(),
                "riskTier", transaction.getRiskTier().name(), "protectionExpiresAt",
                transaction.getProtectionExpiresAt() == null ? "" : transaction.getProtectionExpiresAt().toString(),
                "riskReason", transaction.getRiskReason());
    }
}
