package com.ofss.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.ofss.beans.TransactionExceptionLog;
import com.ofss.beans.TransactionProcessingStage;

import jakarta.persistence.LockModeType;

public interface TransactionExceptionDao
        extends Repository<TransactionExceptionLog, Long> {

    <S extends TransactionExceptionLog> S save(S exception);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"transaction", "posting", "resolvedByUser"})
    @Query(value = """
            select e from TransactionExceptionLog e left join e.transaction t
             where (:transactionId is null or t.transactionId = :transactionId)
               and (:stage is null or e.processingStage = :stage)
               and (:status is null or e.status = :status)
               and (:from is null or e.lastOccurredAt >= :from)
               and (:to is null or e.lastOccurredAt < :to)
             order by e.lastOccurredAt desc, e.transactionExceptionId desc
            """, countQuery = """
            select count(e) from TransactionExceptionLog e left join e.transaction t
             where (:transactionId is null or t.transactionId = :transactionId)
               and (:stage is null or e.processingStage = :stage)
               and (:status is null or e.status = :status)
               and (:from is null or e.lastOccurredAt >= :from)
               and (:to is null or e.lastOccurredAt < :to)
            """)
    org.springframework.data.domain.Page<TransactionExceptionLog> searchForAudit(
            @Param("transactionId") Long transactionId, @Param("stage") TransactionProcessingStage stage,
            @Param("status") com.ofss.beans.TransactionExceptionStatus status,
            @Param("from") java.time.OffsetDateTime from, @Param("to") java.time.OffsetDateTime to,
            org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"transaction", "posting", "resolvedByUser"})
    Optional<TransactionExceptionLog> findByTransactionExceptionId(Long exceptionId);

    <S extends TransactionExceptionLog> S saveAndFlush(
            S exception);

    Optional<TransactionExceptionLog> findByExceptionReference(
            String exceptionReference);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<TransactionExceptionLog>
            findFirstByTransaction_TransactionIdAndProcessingStageOrderByCreatedAtDesc(
                    Long transactionId,
                    TransactionProcessingStage processingStage);

    List<TransactionExceptionLog>
            findAllByTransaction_TransactionIdOrderByCreatedAtAsc(
                    Long transactionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select exceptionLog
              from TransactionExceptionLog exceptionLog
             where exceptionLog.transactionExceptionId = :exceptionId
            """)
    Optional<TransactionExceptionLog> findByIdForUpdate(
            @Param("exceptionId") Long exceptionId);
}
