package com.ofss.excp;

import java.util.Map;
import java.sql.SQLException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(TransactionValidationException.class)
    ResponseEntity<Map<String, String>> transactionValidation(TransactionValidationException exception) {
        return ResponseEntity.status(exception.getStatus()).body(Map.of("message", exception.getMessage()));
    }

    @ExceptionHandler({org.springframework.dao.ConcurrencyFailureException.class})
    ResponseEntity<Map<String, String>> concurrentChange(RuntimeException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message",
                "The record changed concurrently. Refresh and retry with the same operation key."));
    }

    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, String>> invalidFields(org.springframework.web.bind.MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage()).sorted()
                .findFirst().orElse("Invalid request");
        return ResponseEntity.badRequest().body(Map.of("message", message));
    }

    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.bind.MissingRequestHeaderException.class})
    ResponseEntity<Map<String, String>> malformedRequest(Exception exception) {
        return ResponseEntity.badRequest().body(Map.of("message", "Invalid JSON, field type or missing required header"));
    }
    @ExceptionHandler({DuplicateEmailException.class, DuplicatePhoneException.class})
    ResponseEntity<Map<String, String>> duplicateRegistration(RuntimeException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", exception.getMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Map<String, String>> dataIntegrity(DataIntegrityViolationException exception) {
        // Pre-checks provide field-specific messages. Concurrent inserts can still
        // hit a unique constraint; never guess the field from an Oracle SYS name.
        boolean duplicate = exception instanceof DuplicateKeyException;
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && (sql.getErrorCode() == 1 || "23505".equals(sql.getSQLState()))) {
                duplicate = true; // Oracle ORA-00001: unique constraint violation.
            }
        }
        return ResponseEntity.status(duplicate ? HttpStatus.CONFLICT : HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", duplicate
                        ? "A record with these details already exists"
                        : "Unable to save the record"));
    }

    @ExceptionHandler(ResourceNotFoundExcp.class)
    ResponseEntity<Map<String, String>> notFound(ResourceNotFoundExcp exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", exception.getMessage()));
    }

    @ExceptionHandler(AccountAlreadyExistsException.class)
    ResponseEntity<Map<String, String>> accountAlreadyExists(AccountAlreadyExistsException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", exception.getMessage()));
    }

    @ExceptionHandler({InvalidStateTransitionException.class, InsufficientBalanceException.class,
            IllegalArgumentException.class})
    ResponseEntity<Map<String, String>> badRequest(RuntimeException exception) {
        return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
    }
}
