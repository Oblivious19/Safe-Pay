package com.ofss.controller;

import java.util.Map;
import com.ofss.beans.*;
import com.ofss.excp.TransactionValidationException;
import com.ofss.services.VerificationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/transactions")
public class VerificationController {
    private final VerificationService service;
    public VerificationController(VerificationService service) { this.service = service; }

    @PostMapping(value = "/{id}/verify", consumes = "application/json")
    public VerifiedTransactionResponse verify(@PathVariable Long id, @AuthenticationPrincipal LoginPrincipal caller,
            @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody VerificationRequest request) {
        return service.verify(id, caller.userId(), request.password(), key);
    }

    @ExceptionHandler(BadCredentialsException.class)
    ResponseEntity<Map<String, String>> wrongPassword() {
        return ResponseEntity.status(403).body(Map.of("message", "Incorrect password; payment remains on hold"));
    }
    @ExceptionHandler({LockedException.class, DisabledException.class})
    ResponseEntity<Map<String, String>> unavailableUser() {
        return ResponseEntity.status(403).body(Map.of("message", "User is locked or inactive; payment remains on hold"));
    }
    @ExceptionHandler(TransactionValidationException.class)
    ResponseEntity<Map<String, String>> invalidState(TransactionValidationException exception) {
        return ResponseEntity.status(exception.getStatus()).body(Map.of("message", exception.getMessage()));
    }
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            MissingRequestHeaderException.class})
    ResponseEntity<Map<String, String>> invalidRequest() {
        return ResponseEntity.badRequest().body(Map.of("message", "Supply only password and an Idempotency-Key header"));
    }
}
