package com.ofss.controller;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestBody;
import com.ofss.beans.LoginPrincipal;
import com.ofss.beans.TransactionRequest;
import com.ofss.beans.TransactionState;
import com.ofss.repository.UserDao;
import com.ofss.excp.ResourceNotFoundExcp;
import com.ofss.excp.TransactionValidationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.time.Duration;
import java.time.LocalDateTime;

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
    private final UserDao users;

    public TransactionController(TransactionService transactionService, UserDao users) {
        this.transactionService = transactionService;
        this.users = users;
    }

    @PostMapping(consumes = "application/json")
    public Map<String, Object> initiate(@RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody TransactionRequest input, @AuthenticationPrincipal LoginPrincipal caller) {
        return toResponse(transactionService.initiate(input.fromAccountId(), input.beneficiaryId(),
                input.amount(), input.purpose(), idempotencyKey, caller.userId()), caller.userId());
    }

    @GetMapping("/{transactionId}")
    public Map<String, Object> getTransaction(@PathVariable Long transactionId,
            @AuthenticationPrincipal LoginPrincipal caller) {
        return toResponse(transactionService.getTransaction(transactionId, currentEmail(caller)), caller.userId());
    }

    @GetMapping
    public List<Map<String, Object>> getTransactions(@RequestParam(required = false) String state,
            @AuthenticationPrincipal LoginPrincipal caller) {
        List<TransactionDb> transactions = transactionService.getTransactions(state, currentEmail(caller));
        // One database clock read for the whole list; never infer a timezone from the JVM.
        LocalDateTime now = transactions.stream().filter(this::needsProtectionClock).findFirst()
                .map(this::databaseTime).orElse(null);
        return transactions.stream().map(transaction -> toResponse(transaction, now, caller.userId())).toList();
    }

    @PostMapping("/{transactionId}/cancel")
    public Map<String, Object> cancel(@PathVariable Long transactionId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @AuthenticationPrincipal LoginPrincipal caller) {
        return toResponse(transactionService.cancel(transactionId, idempotencyKey, currentEmail(caller)), caller.userId());
    }

    // Resolve current identity by stable session ID, never by a client parameter.
    private String currentEmail(LoginPrincipal caller) {
        return users.findById(caller.userId())
                .orElseThrow(() -> new ResourceNotFoundExcp("User not found")).getEmail();
    }

    @ExceptionHandler(TransactionValidationException.class)
    ResponseEntity<Map<String, String>> validationFailure(TransactionValidationException exception) {
        return ResponseEntity.status(exception.getStatus()).body(Map.of("message", exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, String>> invalidFields(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage()).sorted()
                .findFirst().orElse("Invalid transaction request");
        return ResponseEntity.badRequest().body(Map.of("message", message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Map<String, String>> invalidJson() {
        return ResponseEntity.badRequest().body(Map.of("message", "Invalid transaction JSON or field type"));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    ResponseEntity<Map<String, String>> missingHeader() {
        return ResponseEntity.badRequest().body(Map.of("message", "Idempotency-Key header is required"));
    }

    private Map<String, Object> toResponse(TransactionDb transaction, Long viewerId) {
        return toResponse(transaction, needsProtectionClock(transaction) ? databaseTime(transaction) : null, viewerId);
    }

    private boolean needsProtectionClock(TransactionDb transaction) {
        return transaction.getState() == TransactionState.PROTECTED && transaction.getProtectionExpiresAt() != null;
    }

    private LocalDateTime databaseTime(TransactionDb transaction) {
        return Objects.requireNonNull(transactionService.currentDatabaseTime(transaction.getFromAccount().getAccountId()),
                "Database time is unavailable");
    }

    private Map<String, Object> toResponse(TransactionDb transaction, LocalDateTime now, Long viewerId) {
        Map<String, Object> response = new LinkedHashMap<>();
        boolean incoming = !Objects.equals(transaction.getFromAccount().getUserId(), viewerId)
                && transaction.getToAccount() != null && Objects.equals(transaction.getToAccount().getUserId(), viewerId);
        response.put("direction", incoming ? "CREDIT" : "DEBIT");
        response.put("toAccountId", transaction.getToAccount() == null ? null : transaction.getToAccount().getAccountId());
        response.put("senderName", transaction.getFromAccount().getUser() == null ? "" : transaction.getFromAccount().getUser().getName());
        response.put("counterpartyName", incoming ? transaction.getFromAccount().getUser().getName()
                : transaction.getBeneficiary().getBeneficiaryName());
        response.put("counterpartyAccountNumber", incoming ? transaction.getFromAccount().getAccountNumber()
                : transaction.getBeneficiary().getBankAccountNumber());
        response.put("transactionId", transaction.getTransactionId());
        response.put("transactionRef", transaction.getTransactionRef());
        response.put("amount", transaction.getAmount());
        response.put("purpose", transaction.getPurpose() == null ? "" : transaction.getPurpose());
        response.put("fromAccountId", transaction.getFromAccount().getAccountId());
        response.put("beneficiaryId", transaction.getBeneficiary().getBeneficiaryId());
        response.put("beneficiaryName", transaction.getBeneficiary().getBeneficiaryName());
        response.put("beneficiaryBankAccountNumber", transaction.getBeneficiary().getBankAccountNumber());
        response.put("beneficiaryIfsc", transaction.getBeneficiary().getIfsc());
        response.put("state", transaction.getState().name());
        response.put("riskTier", transaction.getRiskTier().name());
        response.put("riskReason", transaction.getRiskReason());
        response.put("authenticationRequired", transaction.isAuthenticationRequired());
        response.put("verifiedAt", transaction.getVerifiedAt() == null ? "" : transaction.getVerifiedAt().toString());
        response.put("authorizedAt", transaction.getAuthorizedAt() == null ? "" : transaction.getAuthorizedAt().toString());
        response.put("releasedAt", transaction.getReleasedAt() == null ? "" : transaction.getReleasedAt().toString());
        response.put("protectionSeconds", transaction.getProtectionSeconds());
        response.put("protectionExpiresAt", transaction.getProtectionExpiresAt() == null ? "" : transaction.getProtectionExpiresAt().toString());
        boolean protectedWithClock = needsProtectionClock(transaction) && now != null;
        response.put("protectionRemainingMillis", protectedWithClock
                ? Math.max(0L, Duration.between(now, transaction.getProtectionExpiresAt()).toMillis()) : null);
        response.put("canCancel", !incoming && (transaction.getState() == TransactionState.HARD_HOLD
                || (protectedWithClock && now.isBefore(transaction.getProtectionExpiresAt()))));
        response.put("createdAt", transaction.getCreatedAt() == null ? "" : transaction.getCreatedAt().toString());
        response.put("settledAt", transaction.getSettledAt() == null ? "" : transaction.getSettledAt().toString());
        response.put("cancelledAt", transaction.getCancelledAt() == null ? "" : transaction.getCancelledAt().toString());
        return response;
    }
}
