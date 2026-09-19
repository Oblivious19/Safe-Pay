package com.ofss.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.ofss.beans.LedgerPosting;

import jakarta.persistence.LockModeType;

public interface LedgerPostingDao
        extends Repository<LedgerPosting, Long> {

    <S extends LedgerPosting> S save(S posting);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"transaction"})
    @Query(value = """
            select p from LedgerPosting p left join p.transaction t
             where (:transactionId is null or t.transactionId = :transactionId)
               and (:status is null or p.status = :status)
               and (:from is null or p.createdAt >= :from)
               and (:to is null or p.createdAt < :to)
             order by p.createdAt desc, p.postingId desc
            """, countQuery = """
            select count(p) from LedgerPosting p left join p.transaction t
             where (:transactionId is null or t.transactionId = :transactionId)
               and (:status is null or p.status = :status)
               and (:from is null or p.createdAt >= :from)
               and (:to is null or p.createdAt < :to)
            """)
    org.springframework.data.domain.Page<LedgerPosting> searchForAudit(
            @Param("transactionId") Long transactionId,
            @Param("status") com.ofss.beans.LedgerPostingStatus status,
            @Param("from") java.time.OffsetDateTime from, @Param("to") java.time.OffsetDateTime to,
            org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"transaction"})
    Optional<LedgerPosting> findByPostingId(Long postingId);

    <S extends LedgerPosting> S saveAndFlush(S posting);

    Optional<LedgerPosting> findByPostingReference(
            String postingReference);

    Optional<LedgerPosting> findByTransaction_TransactionId(
            Long transactionId);

    Optional<LedgerPosting> findBySourceSystemAndIdempotencyKey(
            String sourceSystem,
            String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select posting
              from LedgerPosting posting
             where posting.postingId = :postingId
            """)
    Optional<LedgerPosting> findByIdForUpdate(
            @Param("postingId") Long postingId);
}
