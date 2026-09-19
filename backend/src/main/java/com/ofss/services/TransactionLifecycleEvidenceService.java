package com.ofss.services;

import java.time.OffsetDateTime;

import com.ofss.beans.AuditOutcome;
import com.ofss.beans.RoleName;
import com.ofss.beans.TransactionDb;
import com.ofss.beans.TransactionState;
import com.ofss.beans.User;

public interface TransactionLifecycleEvidenceService {

    void appendUserEvent(
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
            OffsetDateTime occurredAt);

    void appendSystemEvent(
            TransactionLifecycleEvent event,
            TransactionDb transaction,
            TransactionState previousState,
            TransactionState newState,
            AuditOutcome outcome,
            String reasonCode,
            OperationContext context,
            String occurrenceIdentity,
            OffsetDateTime occurredAt);

    void appendNotification(
            TransactionLifecycleEvent event,
            TransactionDb transaction,
            OperationContext context,
            String occurrenceIdentity,
            OffsetDateTime occurredAt);
}
