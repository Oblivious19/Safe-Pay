package com.ofss.beans;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Arrays;

import org.hibernate.annotations.Immutable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.persistence.Table;

@ExtendWith(MockitoExtension.class)
class SettlementEvidenceModelTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-09-16T12:30:00+05:30");
    @Mock private TransactionDb transaction;
    @Mock private User customer;

    @Test
    void auditMappingTargetsImmutableV7TableAndUniqueReference() {
        Table table = AuditLog.class.getAnnotation(Table.class);

        assertThat(table.name()).isEqualTo("AUDIT_LOG");
        assertThat(table.schema()).isEqualTo("SAFEPAY_OWNER");
        assertThat(AuditLog.class.isAnnotationPresent(Immutable.class)).isTrue();
        assertThat(Arrays.stream(table.uniqueConstraints()).map(jakarta.persistence.UniqueConstraint::name))
                .containsExactly("UK_AUDIT_LOG_EVENT_REF");
    }

    @Test
    void systemAuditNormalizesCodesAndUtcTimestamp() {
        persistedTransaction();

        AuditLog event = AuditLog.systemTransactionEvent(
                " EVENT-1 ",
                "payment_settled",
                transaction,
                TransactionState.RELEASED,
                TransactionState.SETTLED,
                AuditOutcome.SUCCESS,
                null,
                " CORR-1 ",
                " IDEMP-1 ",
                NOW);

        assertThat(event.getActorType()).isEqualTo(AuditActorType.SYSTEM);
        assertThat(event.getActorUser()).isNull();
        assertThat(event.getActionCode()).isEqualTo("PAYMENT_SETTLED");
        assertThat(event.getEntityType()).isEqualTo("PAYMENT_TRANSACTION");
        assertThat(event.getOccurredAt().getOffset()).isEqualTo(java.time.ZoneOffset.UTC);
    }

    @Test
    void notificationMappingCarriesBothV7UniqueConstraints() {
        Table table = AppNotification.class.getAnnotation(Table.class);

        assertThat(table.name()).isEqualTo("APP_NOTIFICATION");
        assertThat(Arrays.stream(table.uniqueConstraints()).map(jakarta.persistence.UniqueConstraint::name))
                .containsExactly("UK_APP_NOTIFICATION_REF", "UK_APP_NOTIFICATION_DEDUP");
    }

    @Test
    void settlementNotificationStartsAsUndeliveredInAppEvidence() {
        ownedPersistedTransaction();

        AppNotification notification = AppNotification.pendingSettlement(
                "NOTIFY-1",
                customer,
                transaction,
                "PAYMENT_SETTLED:SP-1",
                "CORR-1",
                NOW);

        assertThat(notification.getNotificationType()).isEqualTo(NotificationType.PAYMENT_SETTLED);
        assertThat(notification.getSeverity()).isEqualTo(NotificationSeverity.INFO);
        assertThat(notification.getDeliveryChannel()).isEqualTo(NotificationDeliveryChannel.IN_APP);
        assertThat(notification.getDeliveryStatus()).isEqualTo(NotificationDeliveryStatus.PENDING);
        assertThat(notification.getAttemptCount()).isZero();
        assertThat(notification.getMaxAttempts()).isEqualTo(5);
        assertThat(notification.getDeliveredAt()).isNull();
    }

    @Test
    void notificationRecipientMustOwnTransaction() {
        ownedPersistedTransaction();
        User other = org.mockito.Mockito.mock(User.class);
        when(other.getUserId()).thenReturn(8L);

        assertThatThrownBy(() -> AppNotification.pendingSettlement(
                "NOTIFY-1", other, transaction, "DEDUP-1", "CORR-1", NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("recipient must own transaction");
    }

    @Test
    void evidenceEnumerationsExactlyMatchV7Vocabulary() {
        assertThat(AuditActorType.values()).extracting(Enum::name)
                .containsExactly("USER", "SYSTEM");
        assertThat(AuditOutcome.values()).extracting(Enum::name)
                .containsExactly("SUCCESS", "DENIED", "FAILED");
        assertThat(NotificationDeliveryChannel.values()).extracting(Enum::name)
                .containsExactly("IN_APP");
        assertThat(NotificationType.values()).extracting(Enum::name)
                .contains("PAYMENT_SETTLED", "PAYMENT_FAILED");
    }

    private void persistedTransaction() {
        when(transaction.getTransactionId()).thenReturn(1L);
    }

    private void ownedPersistedTransaction() {
        persistedTransaction();
        when(transaction.getCustomer()).thenReturn(customer);
        when(customer.getUserId()).thenReturn(7L);
    }
}
