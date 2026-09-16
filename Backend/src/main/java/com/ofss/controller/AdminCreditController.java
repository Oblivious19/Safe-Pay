package com.ofss.controller;

import java.util.List;
import java.util.Map;
import com.ofss.beans.AdminCreditDtos.*;
import com.ofss.beans.LoginPrincipal;
import com.ofss.services.AdminCreditService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.*;
import org.springframework.http.converter.HttpMessageNotReadableException;

@RestController
@RequestMapping("/api/admin")
public class AdminCreditController {
    private final AdminCreditService service;
    public AdminCreditController(AdminCreditService service) { this.service = service; }
    @GetMapping("/users/{id}/accounts")
    public List<AccountView> accounts(@PathVariable Long id) { return service.accounts(id); }
    @PostMapping(value = "/accounts/{id}/interest-credits", consumes = "application/json")
    public Receipt credit(@PathVariable Long id, @AuthenticationPrincipal LoginPrincipal caller,
            @RequestHeader("Idempotency-Key") String requestKey, @Valid @RequestBody Request request) {
        return service.credit(id, caller.userId(), request.amount(), requestKey);
    }
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    ResponseEntity<?> invalid() { return ResponseEntity.badRequest().body(Map.of("message", "Supply only a positive amount (maximum two decimals)")); }
    @ExceptionHandler(MissingRequestHeaderException.class)
    ResponseEntity<?> missingKey() { return ResponseEntity.badRequest().body(Map.of("message", "Idempotency-Key header is required")); }
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    ResponseEntity<?> duplicateKey() { return ResponseEntity.status(409).body(Map.of("message", "Credit request conflicted. Retry the same request with the same Idempotency-Key.")); }
    @ExceptionHandler({org.springframework.dao.DataAccessException.class, IllegalStateException.class})
    ResponseEntity<?> unavailable() { return ResponseEntity.status(503).body(Map.of("message", "Credit could not be confirmed. Retry the same request with the same Idempotency-Key.")); }
}