package com.ofss.services;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ofss.beans.Account;
import com.ofss.beans.AuditOutcome;
import com.ofss.beans.TransactionDb;
import com.ofss.beans.TransactionExceptionLog;
import com.ofss.beans.TransactionExceptionStatus;
import com.ofss.beans.TransactionProcessingStage;
import com.ofss.beans.TransactionState;
import com.ofss.excp.BusinessRuleException;
import com.ofss.repository.AccountDao;
import com.ofss.repository.TransactionDao;
import com.ofss.repository.TransactionExceptionDao;
import com.ofss.scheduler.SettlementProcessorProperties;

import jakarta.persistence.EntityManager;

@Service
public class SettlementFailureRecorderImpl
        implements SettlementFailureRecorder {

    private static final String EXCEPTION_PREFIX = "SETTLEMENT-EXCEPTION-";

    private final TransactionDao transactionDao;
    private final TransactionExceptionDao exceptionDao;
    private final AccountDao accountDao;
    private final TransactionStateService stateService;
    private final SettlementProcessorProperties properties;
    private final TransactionLifecycleEvidenceService evidenceService;
    private final EntityManager entityManager;

    public SettlementFailureRecorderImpl(
            TransactionDao transactionDao,
            TransactionExceptionDao exceptionDao,
            AccountDao accountDao,
            TransactionStateService stateService,
            SettlementProcessorProperties properties,
            TransactionLifecycleEvidenceService evidenceService,
            EntityManager entityManager) {

        this.transactionDao = Objects.requireNonNull(transactionDao, "transactionDao is required");
        this.exceptionDao = Objects.requireNonNull(exceptionDao, "exceptionDao is required");
        this.accountDao = Objects.requireNonNull(accountDao, "accountDao is required");
        this.stateService = Objects.requireNonNull(stateService, "stateService is required");
        this.properties = Objects.requireNonNull(properties, "properties is required");
        this.evidenceService = Objects.requireNonNull(
                evidenceService,
                "evidenceService is required");
        this.entityManager = Objects.requireNonNull(entityManager, "entityManager is required");
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SettlementFailureDisposition recordFailure(
            Long transactionId,
            String correlationId,
            RuntimeException failure) {

        requirePositiveId(transactionId);
        requireText(correlationId, "correlationId");
        Objects.requireNonNull(failure, "failure is required");

        TransactionDb transaction = transactionDao
                .findByIdForUpdate(transactionId)
                .orElse(null);

        if (transaction == null
                || transaction.getState() != TransactionState.RELEASED) {
            return SettlementFailureDisposition.IGNORED;
        }

        OffsetDateTime failedAt = Objects.requireNonNull(
                transactionDao.currentDatabaseTime(),
                "database time is required");
        String errorCode = errorCode(failure);

        if (isDefinitiveCustomerAccountFailure(failure)
                && releaseReservationSafely(transaction, failedAt)) {

            TransactionExceptionLog exception = TransactionExceptionLog.open(
                    EXCEPTION_PREFIX + "FINAL-" + transactionId,
                    transaction,
                    null,
                    TransactionProcessingStage.SETTLEMENT,
                    errorCode,
                    safeMessage(failure),
                    false,
                    correlationId,
                    failedAt);
            exceptionDao.save(exception);
            stateService.transition(
                    transaction,
                    TransactionState.FAILED,
                    errorCode,
                    failedAt);
            evidenceService.appendSystemEvent(
                    TransactionLifecycleEvent.PAYMENT_FAILED,
                    transaction,
                    TransactionState.RELEASED,
                    TransactionState.FAILED,
                    AuditOutcome.FAILED,
                    errorCode,
                    new OperationContext(correlationId, null),
                    "SETTLEMENT-FINAL-FAILURE",
                    failedAt);
            entityManager.flush();
            return SettlementFailureDisposition.DEFINITIVE_FAILURE;
        }

        Optional<TransactionExceptionLog> existing = exceptionDao
                .findFirstByTransaction_TransactionIdAndProcessingStageOrderByCreatedAtDesc(
                        transactionId,
                        TransactionProcessingStage.SETTLEMENT);

        TransactionExceptionLog exception = existing.orElseGet(() ->
                TransactionExceptionLog.open(
                        EXCEPTION_PREFIX + transactionId,
                        transaction,
                        null,
                        TransactionProcessingStage.SETTLEMENT,
                        errorCode,
                        safeMessage(failure),
                        true,
                        correlationId,
                        failedAt));

        if (exception.getStatus() == TransactionExceptionStatus.MANUAL_REVIEW) {
            return SettlementFailureDisposition.MANUAL_REVIEW;
        }

        int scheduledAttempt = exception.getRetryCount();
        if (scheduledAttempt >= properties.maximumAutomaticRetries()) {
            exception.moveToManualReview(
                    properties.maximumAutomaticRetries(),
                    failedAt);
            exceptionDao.saveAndFlush(exception);
            evidenceService.appendSystemEvent(
                    TransactionLifecycleEvent
                            .SETTLEMENT_MANUAL_REVIEW_REQUIRED,
                    transaction,
                    TransactionState.RELEASED,
                    TransactionState.RELEASED,
                    AuditOutcome.FAILED,
                    errorCode,
                    new OperationContext(correlationId, null),
                    "SETTLEMENT-MANUAL-REVIEW",
                    failedAt);
            return SettlementFailureDisposition.MANUAL_REVIEW;
        }

        int nextAttempt = scheduledAttempt + 1;
        exception.scheduleRetry(
                nextAttempt,
                failedAt,
                failedAt.plus(properties.retryDelayForAttempt(nextAttempt)));
        exceptionDao.saveAndFlush(exception);
        return SettlementFailureDisposition.RETRY_SCHEDULED;
    }

    private boolean releaseReservationSafely(
            TransactionDb transaction,
            OffsetDateTime failedAt) {

        Account source = accountDao
                .findByIdForUpdate(transaction.getSourceAccount().getAccountId())
                .orElse(null);
        if (source == null
                || source.getReservedAmount() == null
                || source.getReservedAmount().compareTo(transaction.getAmount()) < 0
                || transaction.getReservedAmount() == null
                || transaction.getReservedAmount().compareTo(transaction.getAmount()) != 0
                || transaction.getReservationEndedAt() != null) {
            return false;
        }

        source.releaseReservedFunds(transaction.getAmount(), failedAt);
        transaction.endReservation(failedAt);
        return true;
    }

    private static boolean isDefinitiveCustomerAccountFailure(RuntimeException failure) {
        if (failure instanceof SettlementInvariantException invariant) {
            return "SOURCE_ACCOUNT_INACTIVE".equals(invariant.getErrorCode())
                    || "INVALID_SETTLEMENT_SOURCE".equals(invariant.getErrorCode())
                    || "SETTLEMENT_CURRENCY_MISMATCH".equals(invariant.getErrorCode());
        }
        return failure instanceof BusinessRuleException business
                && ("ACCOUNT_INACTIVE".equals(business.getErrorCode())
                        || "CUSTOMER_ACCOUNT_REQUIRED".equals(business.getErrorCode()));
    }

    private static String errorCode(RuntimeException failure) {
        if (failure instanceof SettlementInvariantException invariant) {
            return invariant.getErrorCode();
        }
        if (failure instanceof BusinessRuleException business) {
            return business.getErrorCode().trim().toUpperCase(Locale.ROOT);
        }
        String simpleName = failure.getClass().getSimpleName()
                .replaceAll("([a-z])([A-Z])", "$1_$2")
                .toUpperCase(Locale.ROOT);
        return truncate(simpleName.isBlank() ? "SETTLEMENT_FAILURE" : simpleName, 100);
    }

    private static String safeMessage(RuntimeException failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) {
            message = "Settlement processing failed";
        }
        return truncate(message.trim(), 2000);
    }

    private static String truncate(String value, int maximumLength) {
        return value.length() <= maximumLength
                ? value
                : value.substring(0, maximumLength);
    }

    private static void requirePositiveId(Long value) {
        if (value == null || value <= 0L) {
            throw new IllegalArgumentException("transactionId must be positive");
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }
}
