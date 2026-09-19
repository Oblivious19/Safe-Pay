package com.ofss.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ofss.beans.AppNotification;
import com.ofss.beans.AuditLog;
import com.ofss.beans.AuditOutcome;
import com.ofss.beans.NotificationDeliveryStatus;
import com.ofss.beans.NotificationType;
import com.ofss.beans.RoleName;
import com.ofss.beans.TransactionDb;
import com.ofss.beans.TransactionState;
import com.ofss.beans.User;
import com.ofss.repository.AppNotificationDao;
import com.ofss.repository.AuditLogDao;

@ExtendWith(MockitoExtension.class)
class TransactionLifecycleEvidenceServiceTest {

    private static final OffsetDateTime NOW =
            OffsetDateTime.parse("2026-09-17T04:30:00Z");

    @Mock private AuditLogDao auditLogDao;
    @Mock private AppNotificationDao notificationDao;
    @Mock private TransactionDb transaction;
    @Mock private User customer;

    private TransactionLifecycleEvidenceService service;

    @BeforeEach
    void setUp() {
        service = new TransactionLifecycleEvidenceServiceImpl(
                auditLogDao,
                notificationDao);
        org.mockito.Mockito.lenient()
                .when(transaction.getTransactionId())
                .thenReturn(101L);
        org.mockito.Mockito.lenient()
                .when(transaction.getCustomer())
                .thenReturn(customer);
        org.mockito.Mockito.lenient()
                .when(customer.getUserId())
                .thenReturn(7L);
    }

    @Test
    void appendsCanonicalUserAuditAndPendingNotification() {
        service.appendUserEvent(
                TransactionLifecycleEvent.PAYMENT_PROTECTED,
                transaction,
                customer,
                RoleName.CUSTOMER,
                TransactionState.CREATED,
                TransactionState.PROTECTED,
                AuditOutcome.SUCCESS,
                null,
                new OperationContext("CORR-1", "KEY-1"),
                "AUTHORIZE",
                NOW);

        AuditLog audit = captureAudit();
        AppNotification notification = captureNotification();

        assertThat(audit.getActionCode()).isEqualTo("PAYMENT_PROTECTED");
        assertThat(audit.getPreviousState()).isEqualTo("CREATED");
        assertThat(audit.getNewState()).isEqualTo("PROTECTED");
        assertThat(audit.getActorRoleCode()).isEqualTo("CUSTOMER");
        assertThat(audit.getCorrelationId()).isEqualTo("CORR-1");
        assertThat(audit.getIdempotencyKey()).isEqualTo("KEY-1");
        assertThat(notification.getNotificationType())
                .isEqualTo(NotificationType.PAYMENT_PROTECTED);
        assertThat(notification.getDeliveryStatus())
                .isEqualTo(NotificationDeliveryStatus.PENDING);
        assertThat(notification.getAttemptCount()).isZero();
        assertThat(notification.getMaxAttempts()).isEqualTo(5);
    }

    @Test
    void appendsSystemAuditWithoutNotificationForOperationalReview() {
        service.appendSystemEvent(
                TransactionLifecycleEvent
                        .SETTLEMENT_MANUAL_REVIEW_REQUIRED,
                transaction,
                TransactionState.RELEASED,
                TransactionState.RELEASED,
                AuditOutcome.FAILED,
                "ORACLE_UNAVAILABLE",
                new OperationContext("SETTLEMENT-101", null),
                "SETTLEMENT-MANUAL-REVIEW",
                NOW);

        AuditLog audit = captureAudit();
        assertThat(audit.getActorUser()).isNull();
        assertThat(audit.getActionCode())
                .isEqualTo("SETTLEMENT_MANUAL_REVIEW_REQUIRED");
        assertThat(audit.getReasonCode())
                .isEqualTo("ORACLE_UNAVAILABLE");
        verify(notificationDao, never()).save(any());
    }

    @Test
    void canAppendNotificationWithoutDuplicatingExistingReviewAudit() {
        service.appendNotification(
                TransactionLifecycleEvent.RISK_REVIEW_APPROVED,
                transaction,
                new OperationContext("REVIEW-CORR", "REVIEW-KEY"),
                "REVIEW-501",
                NOW);

        verify(auditLogDao, never()).save(any());
        assertThat(captureNotification().getNotificationType())
                .isEqualTo(NotificationType.RISK_REVIEW_APPROVED);
    }

    @Test
    void rejectsNotificationOnlyCallForAuditOnlyEvent() {
        assertThatThrownBy(() -> service.appendNotification(
                TransactionLifecycleEvent.OTP_ISSUED,
                transaction,
                new OperationContext("CORR", "KEY"),
                "CHALLENGE-1",
                NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("event does not create a notification");

        verify(notificationDao, never()).save(any());
    }

    @Test
    void hashesBusinessIdentityInsteadOfLeakingItIntoReferences() {
        String secretLookingIdentity =
                "customer@example.com|raw-idempotency-key";

        service.appendUserEvent(
                TransactionLifecycleEvent.OTP_REQUIRED,
                transaction,
                customer,
                RoleName.CUSTOMER,
                TransactionState.RISK_ASSESSED,
                TransactionState.VERIFICATION_REQUIRED,
                AuditOutcome.SUCCESS,
                null,
                new OperationContext("CORR", "KEY"),
                secretLookingIdentity,
                NOW);

        AuditLog audit = captureAudit();
        AppNotification notification = captureNotification();
        assertThat(audit.getEventReference())
                .startsWith("AUD-")
                .doesNotContain("customer", "idempotency");
        assertThat(notification.getNotificationReference())
                .startsWith("NOTIFY-")
                .doesNotContain("customer", "idempotency");
        assertThat(notification.getDeduplicationKey())
                .startsWith("OTP_REQUIRED:")
                .doesNotContain("customer", "idempotency");
    }

    @Test
    void createsDeterministicIdentityForTheSameOccurrence() {
        appendProtected("AUTHORIZE");
        appendProtected("AUTHORIZE");

        ArgumentCaptor<AuditLog> audits =
                ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogDao, org.mockito.Mockito.times(2))
                .save(audits.capture());
        assertThat(audits.getAllValues())
                .extracting(AuditLog::getEventReference)
                .containsExactly(
                        audits.getAllValues().getFirst()
                                .getEventReference(),
                        audits.getAllValues().getFirst()
                                .getEventReference());
    }

    @Test
    void separatesDifferentBusinessOccurrences() {
        appendProtected("ROUND-1");
        appendProtected("ROUND-2");

        ArgumentCaptor<AuditLog> audits =
                ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogDao, org.mockito.Mockito.times(2))
                .save(audits.capture());
        assertThat(audits.getAllValues())
                .extracting(AuditLog::getEventReference)
                .doesNotHaveDuplicates();
    }

    @Test
    void rejectsUnpersistedTransactionBeforeWritingEvidence() {
        org.mockito.Mockito.when(transaction.getTransactionId())
                .thenReturn(null);

        assertThatThrownBy(() -> appendProtected("AUTHORIZE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("transaction must already be persisted");

        verify(auditLogDao, never()).save(any());
        verify(notificationDao, never()).save(any());
    }

    @Test
    void lifecycleCatalogCoversEveryDatabaseNotificationType() {
        assertThat(java.util.Arrays.stream(
                        TransactionLifecycleEvent.values())
                .filter(TransactionLifecycleEvent::createsNotification)
                .map(TransactionLifecycleEvent::notificationType)
                .collect(java.util.stream.Collectors.toSet()))
                .containsExactlyInAnyOrder(
                        NotificationType.values());
    }

    private void appendProtected(String occurrence) {
        service.appendUserEvent(
                TransactionLifecycleEvent.PAYMENT_PROTECTED,
                transaction,
                customer,
                RoleName.CUSTOMER,
                TransactionState.CREATED,
                TransactionState.PROTECTED,
                AuditOutcome.SUCCESS,
                null,
                new OperationContext("CORR", "KEY"),
                occurrence,
                NOW);
    }

    private AuditLog captureAudit() {
        ArgumentCaptor<AuditLog> captor =
                ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogDao).save(captor.capture());
        return captor.getValue();
    }

    private AppNotification captureNotification() {
        ArgumentCaptor<AppNotification> captor =
                ArgumentCaptor.forClass(AppNotification.class);
        verify(notificationDao).save(captor.capture());
        return captor.getValue();
    }
}
