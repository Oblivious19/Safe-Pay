package com.ofss.excp;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import jakarta.persistence.OptimisticLockException;
import jakarta.validation.ConstraintViolationException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundExcp.class)
    ResponseEntity<Map<String, String>> notFound(ResourceNotFoundExcp exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", exception.getMessage()));
    }

    @ExceptionHandler({InvalidStateTransitionException.class, InsufficientBalanceException.class,
            IllegalArgumentException.class})
    ResponseEntity<Map<String, String>> badRequest(RuntimeException exception) {
        return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> invalidBody(MethodArgumentNotValidException exception) {
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error -> {
            String field = "passwordHash".equals(error.getField()) ? "password" : error.getField();
            errors.putIfAbsent(field, error.getDefaultMessage() == null ? "is invalid" : error.getDefaultMessage());
        });
        return ResponseEntity.badRequest().body(Map.of("message", "Validation failed", "errors", errors));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<Map<String, String>> invalidConstraint(ConstraintViolationException exception) {
        return ResponseEntity.badRequest().body(Map.of("message", "Request fields failed validation"));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<Map<String, String>> invalidMethod(HandlerMethodValidationException exception) {
        HttpStatus status = exception.isForReturnValue() ? HttpStatus.INTERNAL_SERVER_ERROR : HttpStatus.BAD_REQUEST;
        String message = exception.isForReturnValue() ? "Response validation failed" : "Request parameters failed validation";
        return ResponseEntity.status(status).body(Map.of("message", message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Map<String, String>> unreadableBody(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest().body(Map.of("message", "Request body must contain valid JSON with the expected field types"));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<Map<String, String>> missingParameter(MissingServletRequestParameterException exception) {
        return ResponseEntity.badRequest().body(Map.of("message", "Required parameter is missing: " + exception.getParameterName()));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    ResponseEntity<Map<String, String>> missingHeader(MissingRequestHeaderException exception) {
        return ResponseEntity.badRequest().body(Map.of("message", "Required header is missing: " + exception.getHeaderName()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<Map<String, String>> invalidParameterType(MethodArgumentTypeMismatchException exception) {
        return ResponseEntity.badRequest().body(Map.of("message", "Parameter has an invalid type: " + exception.getName()));
    }

    @ExceptionHandler({ResourceConflictException.class, IdempotencyConflictException.class})
    ResponseEntity<Map<String, String>> conflict(RuntimeException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", exception.getMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Map<String, String>> dataConflict(DataIntegrityViolationException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("message", "Request conflicts with existing data. Check for duplicate values or related records."));
    }

    @ExceptionHandler({ConcurrencyFailureException.class, OptimisticLockException.class})
    ResponseEntity<Map<String, String>> concurrentChange(RuntimeException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("message", "Resource is being changed by another request. Retry the request."));
    }
}
