package com.ofss.repository;

import java.util.List;
import java.time.OffsetDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.ofss.beans.AuditLog;
import com.ofss.beans.AuditActorType;
import com.ofss.beans.AuditOutcome;

public interface AuditLogDao
        extends Repository<AuditLog, Long> {

    <S extends AuditLog> S save(S auditLog);

    Page<AuditLog> findAllByTransaction_TransactionId(
            Long transactionId,
            Pageable pageable);

    @Query(
            value = """
                    select audit
                      from AuditLog audit
                     where (:transactionId is null
                            or audit.transaction.transactionId = :transactionId)
                       and (:actionCode is null
                            or audit.actionCode = :actionCode)
                       and (:outcome is null
                            or audit.outcome = :outcome)
                       and (:actorType is null
                            or audit.actorType = :actorType)
                       and (:correlationId is null
                            or audit.correlationId = :correlationId)
                       and (:fromTime is null
                            or audit.occurredAt >= :fromTime)
                       and (:toTime is null
                            or audit.occurredAt <= :toTime)
                    """,
            countQuery = """
                    select count(audit)
                      from AuditLog audit
                     where (:transactionId is null
                            or audit.transaction.transactionId = :transactionId)
                       and (:actionCode is null
                            or audit.actionCode = :actionCode)
                       and (:outcome is null
                            or audit.outcome = :outcome)
                       and (:actorType is null
                            or audit.actorType = :actorType)
                       and (:correlationId is null
                            or audit.correlationId = :correlationId)
                       and (:fromTime is null
                            or audit.occurredAt >= :fromTime)
                       and (:toTime is null
                            or audit.occurredAt <= :toTime)
                    """)
    Page<AuditLog> search(
            @Param("transactionId") Long transactionId,
            @Param("actionCode") String actionCode,
            @Param("outcome") AuditOutcome outcome,
            @Param("actorType") AuditActorType actorType,
            @Param("correlationId") String correlationId,
            @Param("fromTime") OffsetDateTime fromTime,
            @Param("toTime") OffsetDateTime toTime,
            Pageable pageable);

    @Query(
            value = """
                    select audit
                      from AuditLog audit
                     where audit.transaction.transactionId = :transactionId
                       and audit.actionCode in :safeActionCodes
                    """,
            countQuery = """
                    select count(audit)
                      from AuditLog audit
                     where audit.transaction.transactionId = :transactionId
                       and audit.actionCode in :safeActionCodes
                    """)
    Page<AuditLog> findCustomerVisibleTimeline(
            @Param("transactionId") Long transactionId,
            @Param("safeActionCodes") List<String> safeActionCodes,
            Pageable pageable);

    List<AuditLog> findAllByTransaction_TransactionIdOrderByOccurredAtAsc(
            Long transactionId);
}
