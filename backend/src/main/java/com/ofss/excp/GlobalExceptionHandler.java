package com.ofss.excp;

import java.net.URI;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;

import com.ofss.common.CorrelationIdFilter;
import com.ofss.common.api.FieldValidationError;
import com.ofss.services.OtpDeliveryException;
import com.ofss.services.SecurityIncidentAuditService;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final Clock clock;
    private final SecurityIncidentAuditService incidentAuditService;

    @Autowired
    public GlobalExceptionHandler(
            Clock clock,
            SecurityIncidentAuditService incidentAuditService) {
        this.clock = clock;
        this.incidentAuditService = incidentAuditService;
    }

    public GlobalExceptionHandler(Clock clock) {
        this(clock, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidationFailure(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {

        List<FieldValidationError> fieldErrors =
                exception.getBindingResult()
                        .getAllErrors()
                        .stream()
                        .map(error -> {
                            String field =
                                    error instanceof FieldError fieldError
                                            ? fieldError.getField()
                                            : error.getObjectName();

                            String message = Objects.requireNonNullElse(
                                    error.getDefaultMessage(),
                                    "Invalid value");

                            return new FieldValidationError(
                                    field,
                                    message);
                        })
                        .sorted(
                                Comparator
                                        .comparing(
                                                FieldValidationError::field)
                                        .thenComparing(
                                                FieldValidationError::message))
                        .toList();

        ResponseEntity<ProblemDetail> response = buildResponse(
                HttpStatus.BAD_REQUEST,
                "Request validation failed",
                "One or more request fields are invalid.",
                "VALIDATION_FAILED",
                request);

        ProblemDetail problem = response.getBody();

        if (problem != null) {
            problem.setProperty("fieldErrors", fieldErrors);
        }

        return response;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleMalformedRequest(
            HttpMessageNotReadableException exception,
            HttpServletRequest request) {

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "Malformed request",
                "The request body is malformed or contains an incompatible value.",
                "MALFORMED_REQUEST",
                request);
    }
    
    @ExceptionHandler(DuplicateResourceExcp.class)
    public ResponseEntity<ProblemDetail> handleDuplicateResource(
            DuplicateResourceExcp exception,
            HttpServletRequest request) {

        return buildResponse(
                HttpStatus.CONFLICT,
                "Resource conflict",
                exception.getMessage(),
                exception.getErrorCode(),
                request);
}

    @ExceptionHandler(InvalidStateTransitionException.class)
    public ResponseEntity<ProblemDetail> handleStateConflict(
            InvalidStateTransitionException exception,
            HttpServletRequest request) {

        return buildResponse(
                HttpStatus.CONFLICT,
                "Transaction state conflict",
                exception.getMessage(),
                "STATE_TRANSITION_CONFLICT",
                request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleArgumentTypeMismatch(
            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request) {

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "Invalid request",
                "The request contains an invalid value.",
                "INVALID_REQUEST",
                request);
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<ProblemDetail> handleIdempotencyConflict(
            IdempotencyConflictException exception,
            HttpServletRequest request) {

        return buildResponse(
                HttpStatus.CONFLICT,
                "Idempotency conflict",
                exception.getMessage(),
                exception.getErrorCode(),
                request);
    }


    @ExceptionHandler(ResourceNotFoundExcp.class)
    public ResponseEntity<ProblemDetail> handleResourceNotFound(
            ResourceNotFoundExcp exception,
            HttpServletRequest request) {

        return buildResponse(
                HttpStatus.NOT_FOUND,
                "Resource not found",
                exception.getMessage(),
                exception.getErrorCode(),
                request);
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ProblemDetail> handleUnmappedResource(
            Exception exception,
            HttpServletRequest request) {
        return buildResponse(
                HttpStatus.NOT_FOUND,
                "Resource not found",
                "The requested resource was not found.",
                "RESOURCE_NOT_FOUND",
                request);
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ProblemDetail> handleBusinessRule(
            BusinessRuleException exception,
            HttpServletRequest request) {

        return buildResponse(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "Business rule violation",
                exception.getMessage(),
                exception.getErrorCode(),
                request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(
            AccessDeniedException exception,
            HttpServletRequest request) {

        if (incidentAuditService != null) {
            Object correlation = request.getAttribute(
                    CorrelationIdFilter.REQUEST_ATTRIBUTE);
            try {
                incidentAuditService.recordForbidden(
                        SecurityContextHolder.getContext().getAuthentication(),
                        "METHOD_ACCESS_DENIED",
                        correlation instanceof String value ? value : null);
            } catch (RuntimeException auditFailure) {
                LOGGER.error(
                        "Unable to persist forbidden-access audit evidence",
                        auditFailure);
            }
        }

        return buildResponse(
                HttpStatus.FORBIDDEN,
                "Access denied",
                "The authenticated user is not authorized for this operation.",
                "ACCESS_DENIED",
                request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthenticationFailure(
            AuthenticationException exception,
            HttpServletRequest request) {
        return buildResponse(
                HttpStatus.UNAUTHORIZED,
                "Authentication failed",
                "Authentication is required or the supplied credentials are invalid.",
                "AUTHENTICATION_FAILED",
                request);
    }

    @ExceptionHandler(OtpVerificationFailureException.class)
    public ResponseEntity<ProblemDetail> handleOtpVerificationFailure(
            OtpVerificationFailureException exception,
            HttpServletRequest request) {

        ResponseEntity<ProblemDetail> response = buildResponse(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "OTP verification failed",
                exception.getMessage(),
                exception.getErrorCode(),
                request);

        if (response.getBody() != null) {
            response.getBody().setProperty(
                    "remainingAttempts",
                    exception.getRemainingAttempts());
        }

        return response;
    }

    @ExceptionHandler(OtpDeliveryException.class)
    public ResponseEntity<ProblemDetail> handleOtpDeliveryFailure(
            OtpDeliveryException exception,
            HttpServletRequest request) {

        return buildResponse(
                HttpStatus.SERVICE_UNAVAILABLE,
                "OTP delivery unavailable",
                "The verification code could not be delivered. Please try again later.",
                "OTP_DELIVERY_UNAVAILABLE",
                request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleInvalidArgument(
            IllegalArgumentException exception,
            HttpServletRequest request) {

        String safeDetail =
                "Authenticated SafePay principal is required"
                        .equals(exception.getMessage())
                                ? exception.getMessage()
                                : "The request contains an invalid value.";

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "Invalid request",
                safeDetail,
                "INVALID_REQUEST",
                request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpectedException(
            Exception exception,
            HttpServletRequest request) {

        String correlationId = resolveCorrelationId(request);

        LOGGER.error(
                "Unhandled request failure; correlationId={}",
                correlationId,
                exception);

        return buildResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal server error",
                "An unexpected error occurred.",
                "INTERNAL_ERROR",
                request,
                correlationId);
    }

    private ResponseEntity<ProblemDetail> buildResponse(
            HttpStatus status,
            String title,
            String safeDetail,
            String errorCode,
            HttpServletRequest request) {

        return buildResponse(
                status,
                title,
                safeDetail,
                errorCode,
                request,
                resolveCorrelationId(request));
    }

    private ResponseEntity<ProblemDetail> buildResponse(
            HttpStatus status,
            String title,
            String safeDetail,
            String errorCode,
            HttpServletRequest request,
            String correlationId) {

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                status,
                safeDetail);

        problem.setTitle(title);
        problem.setType(createProblemType(errorCode));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("errorCode", errorCode);
        problem.setProperty("traceId", correlationId);
        problem.setProperty(
                "timestamp",
                OffsetDateTime.now(clock).toString());

        return ResponseEntity
                .status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .header(
                        CorrelationIdFilter.HEADER_NAME,
                        correlationId)
                .body(problem);
    }

    private URI createProblemType(String errorCode) {
        return URI.create(
                "urn:safepay:problem:"
                        + errorCode.toLowerCase(Locale.ROOT));
    }

    private String resolveCorrelationId(
            HttpServletRequest request) {

        Object correlationId = request.getAttribute(
                CorrelationIdFilter.REQUEST_ATTRIBUTE);

        if (correlationId instanceof String value
                && !value.isBlank()) {
            return value;
        }

        return UUID.randomUUID().toString();
    }
}
