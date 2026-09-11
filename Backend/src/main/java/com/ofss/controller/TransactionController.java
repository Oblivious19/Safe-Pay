package com.ofss.controller;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ofss.beans.CancelTransactionRequest;
import com.ofss.beans.InitiateTransactionRequest;
import com.ofss.beans.TransactionDb;
import com.ofss.services.TransactionService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping("/initiate")
    public Map<String, Object> initiate(@RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody InitiateTransactionRequest request) {
        return toResponse(transactionService.initiate(request.fromAccountId(), request.beneficiaryId(),
                request.amount(), request.purpose(), idempotencyKey, request.userEmail()));
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
            @Valid @RequestBody CancelTransactionRequest request) {
        return toResponse(transactionService.cancel(transactionId, idempotencyKey, request.userEmail()));
    }

    private Map<String, Object> toResponse(TransactionDb transaction) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("transactionId", transaction.getTransactionId());
        response.put("transactionRef", transaction.getTransactionRef());
        response.put("state", transaction.getState() == null ? null : transaction.getState().name());
        response.put("riskTier", transaction.getRiskTier() == null ? null : transaction.getRiskTier().name());
        response.put("protectionExpiresAt", transaction.getProtectionExpiresAt() == null
                ? "" : transaction.getProtectionExpiresAt().toString());
        response.put("riskReason", transaction.getRiskReason());
        response.put("amount", transaction.getAmount());
        response.put("purpose", transaction.getPurpose());
        response.put("fromAccountId", transaction.getFromAccount() == null
                ? null : transaction.getFromAccount().getAccountId());
        response.put("beneficiaryId", transaction.getBeneficiary() == null
                ? null : transaction.getBeneficiary().getBeneficiaryId());
        response.put("beneficiaryName", transaction.getBeneficiary() == null
                ? null : transaction.getBeneficiary().getBeneficiaryName());
        response.put("createdAt", transaction.getCreatedAt());
        response.put("authorizedAt", transaction.getAuthorizedAt());
        response.put("releasedAt", transaction.getReleasedAt());
        response.put("settledAt", transaction.getSettledAt());
        response.put("cancelledAt", transaction.getCancelledAt());
        response.put("protectionSeconds", transaction.getProtectionSeconds());
        response.put("authenticationRequired", "Y".equals(transaction.getAuthenticationRequired()));
        return response;
    }
}
