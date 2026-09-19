package com.ofss.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import com.ofss.beans.AppNotification;
import com.ofss.beans.NotificationSeverity;
import com.ofss.beans.NotificationType;
import com.ofss.beans.TransactionDb;
import com.ofss.beans.User;
import com.ofss.dto.notification.NotificationResponse;
import com.ofss.excp.ResourceNotFoundExcp;
import com.ofss.repository.AppNotificationDao;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    private static final OffsetDateTime CREATED =
            OffsetDateTime.parse("2026-09-17T06:00:00Z");

    @Mock private AppNotificationDao notificationDao;
    @Mock private User customer;
    @Mock private TransactionDb transaction;

    private NotificationService service;
    private AppNotification notification;

    @BeforeEach
    void setUp() {
        service = new NotificationServiceImpl(notificationDao);
        org.mockito.Mockito.lenient()
                .when(customer.getUserId()).thenReturn(7L);
        org.mockito.Mockito.lenient()
                .when(transaction.getTransactionId()).thenReturn(101L);
        org.mockito.Mockito.lenient()
                .when(transaction.getCustomer()).thenReturn(customer);
        notification = AppNotification.pendingLifecycle(
                "NOTIFY-101", customer, transaction,
                NotificationType.PAYMENT_PROTECTED,
                NotificationSeverity.WARNING,
                "Payment protected",
                "Your payment is protected.",
                "DEDUP-101", "CORR-101", CREATED);
        ReflectionTestUtils.setField(
                notification,
                "notificationId",
                501L);
    }

    @Test
    void listsOnlyRepositoryOwnedRowsNewestFirst() {
        when(notificationDao.findAllByRecipient_UserId(
                org.mockito.ArgumentMatchers.eq(7L),
                any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(notification)));

        var response = service.listOwnedNotifications(7L, 0, 20);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().getFirst().notificationReference())
                .isEqualTo("NOTIFY-101");
        org.mockito.ArgumentCaptor<Pageable> pageable =
                org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(notificationDao).findAllByRecipient_UserId(
                org.mockito.ArgumentMatchers.eq(7L),
                pageable.capture());
        assertThat(pageable.getValue().getSort().toString())
                .contains("createdAt: DESC", "notificationId: DESC");
    }

    @Test
    void markReadUsesOwnedPessimisticLookupAndDatabaseTime() {
        OffsetDateTime readAt = CREATED.plusMinutes(1);
        when(notificationDao.findOwnedByIdForUpdate(501L, 7L))
                .thenReturn(Optional.of(notification));
        when(notificationDao.currentDatabaseTime()).thenReturn(readAt);

        NotificationResponse response =
                service.markOwnedNotificationRead(7L, 501L);

        assertThat(response.read()).isTrue();
        assertThat(response.readAt()).isEqualTo(readAt);
        verify(notificationDao).save(notification);
    }

    @Test
    void repeatedMarkReadIsNaturallyIdempotent() {
        notification.markRead(CREATED.plusMinutes(1));
        when(notificationDao.findOwnedByIdForUpdate(501L, 7L))
                .thenReturn(Optional.of(notification));

        NotificationResponse response =
                service.markOwnedNotificationRead(7L, 501L);

        assertThat(response.readAt())
                .isEqualTo(CREATED.plusMinutes(1));
        verify(notificationDao, never()).currentDatabaseTime();
        verify(notificationDao, never()).save(any());
    }

    @Test
    void crossUserLookupIsIndistinguishableFromMissingRow() {
        when(notificationDao.findOwnedByIdForUpdate(501L, 8L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.markOwnedNotificationRead(8L, 501L))
                .isInstanceOf(ResourceNotFoundExcp.class)
                .hasMessage("Notification was not found");
    }

    @Test
    void responseOmitsDeliveryInternalsAndPreservesClientDedupReference() {
        NotificationResponse response = NotificationResponse.from(notification);

        assertThat(response.notificationReference())
                .isEqualTo("NOTIFY-101");
        assertThat(NotificationResponse.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .doesNotContain(
                        "deduplicationKey",
                        "correlationId",
                        "lastErrorCode",
                        "attemptCount");
    }

    @Test
    void rejectsInvalidPaginationBeforeRepositoryAccess() {
        assertThatThrownBy(() ->
                service.listOwnedNotifications(7L, -1, 20))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                service.listOwnedNotifications(7L, 0, 101))
                .isInstanceOf(IllegalArgumentException.class);
        verify(notificationDao, never())
                .findAllByRecipient_UserId(any(), any());
    }

    @Test
    void rejectsInvalidActorAndNotificationIdentifiers() {
        assertThatThrownBy(() ->
                service.markOwnedNotificationRead(0L, 501L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                service.markOwnedNotificationRead(7L, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
