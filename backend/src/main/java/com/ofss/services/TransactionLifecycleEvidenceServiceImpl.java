package com.ofss.services;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ofss.beans.AppNotification;
import com.ofss.beans.AuditLog;
import com.ofss.beans.AuditOutcome;
import com.ofss.beans.RoleName;
import com.ofss.beans.TransactionDb;
import com.ofss.beans.TransactionState;
import com.ofss.beans.User;
import com.ofss.repository.AppNotificationDao;
import com.ofss.repository.AuditLogDao;

@Service
public class TransactionLifecycleEvidenceServiceImpl
        implements TransactionLifecycleEvidenceService {

    private final AuditLogDao auditLogDao;
    private final AppNotificationDao notificationDao;

    public TransactionLifecycleEvidenceServiceImpl(
            AuditLogDao auditLogDao,
            AppNotificationDao notificationDao) {
        this.auditLogDao = Objects.requireNonNull(
                auditLogDao,
                "auditLogDao is required");
        this.notificationDao = Objects.requireNonNull(
                notificationDao,
                "notificationDao is required");
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void appendUserEvent(
            TransactionLifecycleEvent event,
            TransactionDb transaction,
            User actor,
            RoleName actorRole,
            TransactionState previousState,
            TransactionState newState,
            AuditOutcome outcome,
            String reasonCode,
            OperationContext context,
            String occurrenceIdentity,
            OffsetDateTime occurredAt) {

        TransactionLifecycleEvent validatedEvent = requireEvent(event);
        String identity = identity(
                validatedEvent,
                transaction,
                occurrenceIdentity);

        auditLogDao.save(AuditLog.userTransactionEvent(
                reference("AUD-", identity),
                actor,
                actorRole,
                validatedEvent.actionCode(),
                transaction,
                previousState,
                newState,
                outcome,
                reasonCode,
                requireContext(context).correlationId(),
                context.idempotencyKey(),
                null,
                occurredAt));

        appendNotificationIfRequired(
                validatedEvent,
                transaction,
                context,
                identity,
                occurredAt);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void appendSystemEvent(
            TransactionLifecycleEvent event,
            TransactionDb transaction,
            TransactionState previousState,
            TransactionState newState,
            AuditOutcome outcome,
            String reasonCode,
            OperationContext context,
            String occurrenceIdentity,
            OffsetDateTime occurredAt) {

        TransactionLifecycleEvent validatedEvent = requireEvent(event);
        String identity = identity(
                validatedEvent,
                transaction,
                occurrenceIdentity);

        auditLogDao.save(AuditLog.systemTransactionEvent(
                reference("AUD-", identity),
                validatedEvent.actionCode(),
                transaction,
                previousState,
                newState,
                outcome,
                reasonCode,
                requireContext(context).correlationId(),
                context.idempotencyKey(),
                occurredAt));

        appendNotificationIfRequired(
                validatedEvent,
                transaction,
                context,
                identity,
                occurredAt);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void appendNotification(
            TransactionLifecycleEvent event,
            TransactionDb transaction,
            OperationContext context,
            String occurrenceIdentity,
            OffsetDateTime occurredAt) {

        TransactionLifecycleEvent validatedEvent = requireEvent(event);
        if (!validatedEvent.createsNotification()) {
            throw new IllegalArgumentException(
                    "event does not create a notification");
        }
        appendNotificationIfRequired(
                validatedEvent,
                transaction,
                requireContext(context),
                identity(validatedEvent, transaction, occurrenceIdentity),
                occurredAt);
    }

    private void appendNotificationIfRequired(
            TransactionLifecycleEvent event,
            TransactionDb transaction,
            OperationContext context,
            String identity,
            OffsetDateTime occurredAt) {

        if (!event.createsNotification()) {
            return;
        }

        notificationDao.save(AppNotification.pendingLifecycle(
                reference("NOTIFY-", identity),
                transaction.getCustomer(),
                transaction,
                event.notificationType(),
                event.severity(),
                event.title(),
                event.message(),
                event.notificationType().name()
                        + ":"
                        + digest(identity),
                context.correlationId(),
                occurredAt));
    }

    private static String identity(
            TransactionLifecycleEvent event,
            TransactionDb transaction,
            String occurrenceIdentity) {
        Objects.requireNonNull(transaction, "transaction is required");
        if (transaction.getTransactionId() == null
                || transaction.getTransactionId() <= 0L) {
            throw new IllegalArgumentException(
                    "transaction must already be persisted");
        }
        if (occurrenceIdentity == null
                || occurrenceIdentity.isBlank()) {
            throw new IllegalArgumentException(
                    "occurrenceIdentity is required");
        }
        return event.actionCode()
                + "|"
                + transaction.getTransactionId()
                + "|"
                + occurrenceIdentity.trim();
    }

    private static String reference(
            String prefix,
            String identity) {
        return prefix + digest(identity).substring(0, 48);
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(
                                    StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable",
                    exception);
        }
    }

    private static TransactionLifecycleEvent requireEvent(
            TransactionLifecycleEvent event) {
        return Objects.requireNonNull(event, "event is required");
    }

    private static OperationContext requireContext(
            OperationContext context) {
        return Objects.requireNonNull(context, "context is required");
    }
}
