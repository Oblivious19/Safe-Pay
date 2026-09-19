package com.ofss.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.ofss.beans.AppNotification;
import com.ofss.beans.AuditLog;
import com.ofss.beans.AuditOutcome;
import com.ofss.beans.LedgerPosting;
import com.ofss.beans.NotificationType;
import com.ofss.beans.TransactionDb;
import com.ofss.beans.TransactionState;
import com.ofss.beans.User;
import com.ofss.repository.AppNotificationDao;
import com.ofss.repository.AuditLogDao;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SettlementEvidenceServiceTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-09-16T12:00:00Z");
    @Mock private AuditLogDao auditDao;
    @Mock private AppNotificationDao notificationDao;
    @Mock private TransactionDb transaction;
    @Mock private LedgerPosting posting;
    @Mock private User customer;
    private SettlementEvidenceService service;

    @BeforeEach
    void setUp() {
        service = new SettlementEvidenceServiceImpl(auditDao, notificationDao);
        when(transaction.getTransactionId()).thenReturn(77L);
        when(transaction.getTransactionReference()).thenReturn("SP-77");
        when(transaction.getState()).thenReturn(TransactionState.SETTLED);
        when(transaction.getCustomer()).thenReturn(customer);
        when(customer.getUserId()).thenReturn(7L);
        when(posting.getTransaction()).thenReturn(transaction);
        when(posting.getIdempotencyKey()).thenReturn("TRANSACTION_SETTLE:SP-77");
    }

    @Test
    void appendsDeterministicAuditAndPendingInAppNotification() {
        service.appendSuccessfulSettlement(transaction, posting, "CORR-77", NOW);

        ArgumentCaptor<AuditLog> audit = ArgumentCaptor.forClass(AuditLog.class);
        ArgumentCaptor<AppNotification> notification = ArgumentCaptor.forClass(AppNotification.class);
        verify(auditDao).save(audit.capture());
        verify(notificationDao).save(notification.capture());
        assertThat(audit.getValue().getEventReference()).isEqualTo("SETTLEMENT-AUDIT-77");
        assertThat(audit.getValue().getOutcome()).isEqualTo(AuditOutcome.SUCCESS);
        assertThat(audit.getValue().getPreviousState()).isEqualTo("RELEASED");
        assertThat(audit.getValue().getNewState()).isEqualTo("SETTLED");
        assertThat(notification.getValue().getNotificationType()).isEqualTo(NotificationType.PAYMENT_SETTLED);
        assertThat(notification.getValue().getDeduplicationKey()).isEqualTo("PAYMENT_SETTLED:SP-77");
    }

    @Test
    void rejectsEvidenceBeforeSuccessfulStateTransition() {
        when(transaction.getState()).thenReturn(TransactionState.RELEASED);

        assertThatThrownBy(() -> service.appendSuccessfulSettlement(transaction, posting, "CORR-77", NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("transaction must be SETTLED");
        verify(auditDao, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsPostingFromAnotherTransaction() {
        when(posting.getTransaction()).thenReturn(org.mockito.Mockito.mock(TransactionDb.class));

        assertThatThrownBy(() -> service.appendSuccessfulSettlement(transaction, posting, "CORR-77", NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("posting must belong to transaction");
        verify(notificationDao, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
