package com.ofss.services;

import java.time.OffsetDateTime;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ofss.beans.TransactionDb;
import com.ofss.beans.TransactionState;
import com.ofss.beans.AuditOutcome;
import com.ofss.repository.TransactionDao;

import jakarta.persistence.EntityManager;

@Service
public class ProtectedTransactionReleaseWorkerImpl
        implements ProtectedTransactionReleaseWorker {

    private final TransactionDao transactionDao;
    private final TransactionStateService stateService;
    private final TransactionLifecycleEvidenceService evidenceService;
    private final EntityManager entityManager;

    public ProtectedTransactionReleaseWorkerImpl(
            TransactionDao transactionDao,
            TransactionStateService stateService,
            TransactionLifecycleEvidenceService evidenceService,
            EntityManager entityManager) {

        this.transactionDao = transactionDao;
        this.stateService = stateService;
        this.evidenceService = Objects.requireNonNull(
                evidenceService,
                "evidenceService is required");
        this.entityManager = entityManager;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ProtectedTransactionReleaseOutcome releaseIfExpired(
            Long transactionId) {

        requirePositiveId(transactionId);

        TransactionDb transaction = transactionDao
                .findByIdForUpdate(transactionId)
                .orElse(null);

        if (transaction == null) {
            return ProtectedTransactionReleaseOutcome.NOT_FOUND;
        }

        if (transaction.getState() != TransactionState.PROTECTED) {
            return ProtectedTransactionReleaseOutcome.NOT_ELIGIBLE;
        }

        OffsetDateTime databaseTime = Objects.requireNonNull(
                transactionDao.currentDatabaseTime(),
                "database time is required");
        OffsetDateTime deadline = transaction.getProtectedUntil();

        if (deadline == null || databaseTime.isBefore(deadline)) {
            return ProtectedTransactionReleaseOutcome.NOT_DUE;
        }

        stateService.transition(
                transaction,
                TransactionState.RELEASED,
                databaseTime);
        evidenceService.appendSystemEvent(
                TransactionLifecycleEvent.PAYMENT_RELEASED,
                transaction,
                TransactionState.PROTECTED,
                TransactionState.RELEASED,
                AuditOutcome.SUCCESS,
                null,
                OperationContext.internal(),
                "PROTECTION-DEADLINE-" + deadline,
                databaseTime);
        entityManager.flush();

        return ProtectedTransactionReleaseOutcome.RELEASED;
    }

    private static void requirePositiveId(Long transactionId) {
        if (transactionId == null || transactionId <= 0L) {
            throw new IllegalArgumentException(
                    "transactionId must be positive");
        }
    }
}
