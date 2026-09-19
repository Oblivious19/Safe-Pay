package com.ofss.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.Repository;

import com.ofss.beans.AppNotification;
import com.ofss.beans.NotificationDeliveryStatus;

import jakarta.persistence.LockModeType;

public interface AppNotificationDao extends Repository<AppNotification, Long> {
    <S extends AppNotification> S save(S notification);
    Optional<AppNotification> findByRecipient_UserIdAndDeduplicationKey(Long recipientUserId, String deduplicationKey);
    List<AppNotification> findAllByTransaction_TransactionIdOrderByCreatedAtAsc(Long transactionId);

    Page<AppNotification> findAllByRecipient_UserId(
            Long recipientUserId,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select notification
              from AppNotification notification
             where notification.notificationId = :notificationId
               and notification.recipient.userId = :recipientUserId
            """)
    Optional<AppNotification> findOwnedByIdForUpdate(
            @Param("notificationId") Long notificationId,
            @Param("recipientUserId") Long recipientUserId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select notification
              from AppNotification notification
             where notification.notificationId = :notificationId
            """)
    Optional<AppNotification> findByIdForDispatch(
            @Param("notificationId") Long notificationId);

    @Query("""
            select notification.notificationId
              from AppNotification notification
             where notification.deliveryStatus = :pendingStatus
                or (
                    notification.deliveryStatus = :retryStatus
                    and notification.nextAttemptAt <= :databaseTime
                )
             order by notification.createdAt asc,
                      notification.notificationId asc
            """)
    List<Long> findDueNotificationIds(
            @Param("pendingStatus") NotificationDeliveryStatus pendingStatus,
            @Param("retryStatus") NotificationDeliveryStatus retryStatus,
            @Param("databaseTime") OffsetDateTime databaseTime,
            Pageable pageable);

    @Query(
            value = "SELECT SYSTIMESTAMP FROM DUAL",
            nativeQuery = true)
    OffsetDateTime currentDatabaseTime();
}
