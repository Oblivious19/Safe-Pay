package com.ofss.services;

import java.time.OffsetDateTime;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.ofss.beans.AppNotification;
import com.ofss.beans.AuditLog;
import com.ofss.beans.AuditOutcome;
import com.ofss.beans.LedgerPosting;
import com.ofss.beans.TransactionDb;
import com.ofss.beans.TransactionState;
import com.ofss.repository.AppNotificationDao;
import com.ofss.repository.AuditLogDao;

@Service
public class SettlementEvidenceServiceImpl
        implements SettlementEvidenceService {

    private static final String AUDIT_PREFIX = "SETTLEMENT-AUDIT-";
    private static final String NOTIFICATION_PREFIX = "SETTLEMENT-NOTIFY-";
    private static final String DEDUPLICATION_PREFIX = "PAYMENT_SETTLED:";

    private final AuditLogDao auditLogDao;
    private final AppNotificationDao notificationDao;

    public SettlementEvidenceServiceImpl(
            AuditLogDao auditLogDao,
            AppNotificationDao notificationDao) {
        this.auditLogDao = Objects.requireNonNull(auditLogDao, "auditLogDao is required");
        this.notificationDao = Objects.requireNonNull(notificationDao, "notificationDao is required");
    }

    @Override
    public void appendSuccessfulSettlement(
            TransactionDb transaction,
            LedgerPosting posting,
            String correlationId,
            OffsetDateTime occurredAt) {

        Objects.requireNonNull(transaction, "transaction is required");
        Objects.requireNonNull(posting, "posting is required");

        Long transactionId = transaction.getTransactionId();
        if (transactionId == null || transactionId <= 0L) {
            throw new IllegalArgumentException("transaction must already be persisted");
        }
        if (transaction.getState() != TransactionState.SETTLED) {
            throw new IllegalArgumentException("transaction must be SETTLED");
        }
        if (posting.getTransaction() != transaction) {
            throw new IllegalArgumentException("posting must belong to transaction");
        }

        AuditLog audit = AuditLog.systemTransactionEvent(
                AUDIT_PREFIX + transactionId,
                "PAYMENT_SETTLED",
                transaction,
                TransactionState.RELEASED,
                TransactionState.SETTLED,
                AuditOutcome.SUCCESS,
                null,
                correlationId,
                posting.getIdempotencyKey(),
                occurredAt);

        AppNotification notification = AppNotification.pendingSettlement(
                NOTIFICATION_PREFIX + transactionId,
                transaction.getCustomer(),
                transaction,
                DEDUPLICATION_PREFIX + transaction.getTransactionReference(),
                correlationId,
                occurredAt);

        auditLogDao.save(audit);
        notificationDao.save(notification);
    }
}
