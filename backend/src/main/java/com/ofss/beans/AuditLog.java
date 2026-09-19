package com.ofss.beans;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Objects;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Immutable
@Table(
        name = "AUDIT_LOG",
        schema = "SAFEPAY_OWNER",
        uniqueConstraints = @UniqueConstraint(
                name = "UK_AUDIT_LOG_EVENT_REF",
                columnNames = "EVENT_REFERENCE"))
@SequenceGenerator(
        name = "auditLogSequence",
        sequenceName = "SAFEPAY_OWNER.SEQ_AUDIT_LOG_ID",
        allocationSize = 1)
public class AuditLog {

    @Id
    @GeneratedValue(
            strategy = GenerationType.SEQUENCE,
            generator = "auditLogSequence")
    @Column(name = "AUDIT_LOG_ID", nullable = false, updatable = false)
    private Long auditLogId;

    @Column(name = "EVENT_REFERENCE", nullable = false, updatable = false, length = 64)
    private String eventReference;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ACTOR_USER_ID", updatable = false)
    private User actorUser;

    @Enumerated(EnumType.STRING)
    @Column(name = "ACTOR_TYPE", nullable = false, updatable = false, length = 20)
    private AuditActorType actorType;

    @Column(name = "ACTOR_ROLE_CODE", updatable = false, length = 40)
    private String actorRoleCode;

    @Column(name = "ACTION_CODE", nullable = false, updatable = false, length = 100)
    private String actionCode;

    @Column(name = "ENTITY_TYPE", nullable = false, updatable = false, length = 50)
    private String entityType;

    @Column(name = "ENTITY_ID", updatable = false)
    private Long entityId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "TRANSACTION_ID", updatable = false)
    private TransactionDb transaction;

    @Column(name = "PREVIOUS_STATE", updatable = false, length = 40)
    private String previousState;

    @Column(name = "NEW_STATE", updatable = false, length = 40)
    private String newState;

    @Enumerated(EnumType.STRING)
    @Column(name = "OUTCOME", nullable = false, updatable = false, length = 20)
    private AuditOutcome outcome;

    @Column(name = "REASON_CODE", updatable = false, length = 100)
    private String reasonCode;

    @Column(name = "CORRELATION_ID", nullable = false, updatable = false, length = 64)
    private String correlationId;

    @Column(name = "IDEMPOTENCY_KEY", updatable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "DETAILS_JSON", updatable = false, columnDefinition = "CLOB")
    private String detailsJson;

    @Column(name = "OCCURRED_AT", nullable = false, updatable = false)
    private OffsetDateTime occurredAt;

    protected AuditLog() {
        // Required by JPA.
    }

    public static AuditLog systemTransactionEvent(
            String eventReference,
            String actionCode,
            TransactionDb transaction,
            TransactionState previousState,
            TransactionState newState,
            AuditOutcome outcome,
            String reasonCode,
            String correlationId,
            String idempotencyKey,
            OffsetDateTime occurredAt) {

        requirePersistedTransaction(transaction);

        AuditLog event = new AuditLog();
        event.eventReference = requireText(eventReference, "eventReference", 64);
        event.actorType = AuditActorType.SYSTEM;
        event.actionCode = requireUppercaseText(actionCode, "actionCode", 100);
        event.entityType = "PAYMENT_TRANSACTION";
        event.entityId = transaction.getTransactionId();
        event.transaction = transaction;
        event.previousState = enumName(previousState);
        event.newState = enumName(newState);
        event.outcome = Objects.requireNonNull(outcome, "outcome is required");
        event.reasonCode = normalizeUppercaseText(reasonCode, "reasonCode", 100);
        event.correlationId = requireText(correlationId, "correlationId", 64);
        event.idempotencyKey = normalizeText(idempotencyKey, "idempotencyKey", 128);
        event.occurredAt = requireUtcTimestamp(occurredAt, "occurredAt");
        return event;
    }

    public static AuditLog userRiskReviewEvent(
            String eventReference,
            User actor,
            String actionCode,
            Long reviewId,
            TransactionDb transaction,
            TransactionState previousState,
            TransactionState newState,
            String reasonCode,
            String correlationId,
            String idempotencyKey,
            String detailsJson,
            OffsetDateTime occurredAt) {

        requirePersistedUser(actor);
        requirePersistedTransaction(transaction);

        if (reviewId == null || reviewId <= 0L) {
            throw new IllegalArgumentException(
                    "reviewId must be positive");
        }

        AuditLog event = new AuditLog();
        event.eventReference = requireText(
                eventReference,
                "eventReference",
                64);
        event.actorUser = actor;
        event.actorType = AuditActorType.USER;
        event.actorRoleCode = RoleName.RISK_OFFICER.name();
        event.actionCode = requireUppercaseText(
                actionCode,
                "actionCode",
                100);
        event.entityType = "RISK_REVIEW";
        event.entityId = reviewId;
        event.transaction = transaction;
        event.previousState = enumName(previousState);
        event.newState = enumName(newState);
        event.outcome = AuditOutcome.SUCCESS;
        event.reasonCode = normalizeUppercaseText(
                reasonCode,
                "reasonCode",
                100);
        event.correlationId = requireText(
                correlationId,
                "correlationId",
                64);
        event.idempotencyKey = requireText(
                idempotencyKey,
                "idempotencyKey",
                128);
        event.detailsJson = requireText(
                detailsJson,
                "detailsJson",
                Integer.MAX_VALUE);
        event.occurredAt = requireUtcTimestamp(
                occurredAt,
                "occurredAt");
        return event;
    }

    public static AuditLog userTransactionEvent(
            String eventReference,
            User actor,
            RoleName actorRole,
            String actionCode,
            TransactionDb transaction,
            TransactionState previousState,
            TransactionState newState,
            AuditOutcome outcome,
            String reasonCode,
            String correlationId,
            String idempotencyKey,
            String detailsJson,
            OffsetDateTime occurredAt) {

        requirePersistedUser(actor);
        requirePersistedTransaction(transaction);

        AuditLog event = new AuditLog();
        event.eventReference = requireText(
                eventReference,
                "eventReference",
                64);
        event.actorUser = actor;
        event.actorType = AuditActorType.USER;
        event.actorRoleCode = Objects.requireNonNull(
                actorRole,
                "actorRole is required").name();
        event.actionCode = requireUppercaseText(
                actionCode,
                "actionCode",
                100);
        event.entityType = "PAYMENT_TRANSACTION";
        event.entityId = transaction.getTransactionId();
        event.transaction = transaction;
        event.previousState = enumName(previousState);
        event.newState = enumName(newState);
        event.outcome = Objects.requireNonNull(
                outcome,
                "outcome is required");
        event.reasonCode = normalizeUppercaseText(
                reasonCode,
                "reasonCode",
                100);
        event.correlationId = requireText(
                correlationId,
                "correlationId",
                64);
        event.idempotencyKey = normalizeText(
                idempotencyKey,
                "idempotencyKey",
                128);
        event.detailsJson = normalizeText(
                detailsJson,
                "detailsJson",
                Integer.MAX_VALUE);
        event.occurredAt = requireUtcTimestamp(
                occurredAt,
                "occurredAt");
        return event;
    }

    public static AuditLog authenticationEvent(
            String eventReference,
            User actor,
            String actionCode,
            AuditOutcome outcome,
            String reasonCode,
            String correlationId,
            OffsetDateTime occurredAt) {

        if (actor != null) {
            requirePersistedUser(actor);
        }

        AuditLog event = new AuditLog();
        event.eventReference = requireText(
                eventReference,
                "eventReference",
                64);
        event.actorUser = actor;
        event.actorType = actor == null
                ? AuditActorType.SYSTEM
                : AuditActorType.USER;
        event.actionCode = requireUppercaseText(
                actionCode,
                "actionCode",
                100);
        event.entityType = actor == null
                ? "AUTHENTICATION"
                : "APP_USER";
        event.entityId = actor == null
                ? null
                : actor.getUserId();
        event.outcome = Objects.requireNonNull(
                outcome,
                "outcome is required");
        event.reasonCode = normalizeUppercaseText(
                reasonCode,
                "reasonCode",
                100);
        event.correlationId = requireText(
                correlationId,
                "correlationId",
                64);
        event.occurredAt = requireUtcTimestamp(
                occurredAt,
                "occurredAt");
        return event;
    }

    public static AuditLog administrativeUserEvent(
            String eventReference,
            User administrator,
            User targetUser,
            String actionCode,
            AuditOutcome outcome,
            String reasonCode,
            String correlationId,
            OffsetDateTime occurredAt) {

        requirePersistedUser(administrator);
        requirePersistedUser(targetUser);

        AuditLog event = new AuditLog();
        event.eventReference = requireText(
                eventReference,
                "eventReference",
                64);
        event.actorUser = administrator;
        event.actorType = AuditActorType.USER;
        event.actorRoleCode = RoleName.SYSTEM_ADMIN.name();
        event.actionCode = requireUppercaseText(
                actionCode,
                "actionCode",
                100);
        event.entityType = "APP_USER";
        event.entityId = targetUser.getUserId();
        event.outcome = Objects.requireNonNull(
                outcome,
                "outcome is required");
        event.reasonCode = normalizeUppercaseText(
                reasonCode,
                "reasonCode",
                100);
        event.correlationId = requireText(
                correlationId,
                "correlationId",
                64);
        event.occurredAt = requireUtcTimestamp(
                occurredAt,
                "occurredAt");
        return event;
    }

    private static void requirePersistedTransaction(TransactionDb transaction) {
        Objects.requireNonNull(transaction, "transaction is required");
        if (transaction.getTransactionId() == null || transaction.getTransactionId() <= 0L) {
            throw new IllegalArgumentException("transaction must already be persisted");
        }
    }

    private static void requirePersistedUser(User user) {
        Objects.requireNonNull(user, "actor is required");
        if (user.getUserId() == null || user.getUserId() <= 0L) {
            throw new IllegalArgumentException(
                    "actor must already be persisted");
        }
    }

    private static String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private static String requireText(String value, String fieldName, int maximumLength) {
        String normalized = normalizeText(value, fieldName, maximumLength);
        if (normalized == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return normalized;
    }

    private static String requireUppercaseText(String value, String fieldName, int maximumLength) {
        return requireText(value, fieldName, maximumLength).toUpperCase(Locale.ROOT);
    }

    private static String normalizeUppercaseText(String value, String fieldName, int maximumLength) {
        String normalized = normalizeText(value, fieldName, maximumLength);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private static String normalizeText(String value, String fieldName, int maximumLength) {
        if (value == null) {
            return null;
        }
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " cannot be blank");
        }
        String normalized = value.trim();
        if (normalized.length() > maximumLength) {
            throw new IllegalArgumentException(fieldName + " has an invalid length");
        }
        return normalized;
    }

    private static OffsetDateTime requireUtcTimestamp(OffsetDateTime value, String fieldName) {
        return Objects.requireNonNull(value, fieldName + " is required")
                .withOffsetSameInstant(ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.MICROS);
    }

    public Long getAuditLogId() { return auditLogId; }
    public String getEventReference() { return eventReference; }
    public User getActorUser() { return actorUser; }
    public AuditActorType getActorType() { return actorType; }
    public String getActorRoleCode() { return actorRoleCode; }
    public String getActionCode() { return actionCode; }
    public String getEntityType() { return entityType; }
    public Long getEntityId() { return entityId; }
    public TransactionDb getTransaction() { return transaction; }
    public String getPreviousState() { return previousState; }
    public String getNewState() { return newState; }
    public AuditOutcome getOutcome() { return outcome; }
    public String getReasonCode() { return reasonCode; }
    public String getCorrelationId() { return correlationId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getDetailsJson() { return detailsJson; }
    public OffsetDateTime getOccurredAt() { return occurredAt; }
}
