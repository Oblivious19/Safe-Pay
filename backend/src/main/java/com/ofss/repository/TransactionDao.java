package com.ofss.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.ofss.beans.TransactionDb;
import com.ofss.beans.TransactionState;

import jakarta.persistence.LockModeType;

public interface TransactionDao
        extends Repository<TransactionDb, Long> {

    <S extends TransactionDb> S save(S transaction);

    Optional<TransactionDb> findByTransactionReference(
            String transactionReference);

    boolean existsByTransactionId(Long transactionId);

    @Query(value = """
            select payment from TransactionDb payment
              join fetch payment.customer customer
              join fetch payment.sourceAccount sourceAccount
              join fetch payment.beneficiary beneficiary
              left join payment.destinationAccount destinationAccount
              left join destinationAccount.owner destinationOwner
             where (:customerId is null or customer.userId = :customerId)
               and (:state is null or payment.state = :state)
               and (:fromTime is null or payment.createdAt >= :fromTime)
               and (:toTime is null or payment.createdAt < :toTime)
             order by payment.createdAt desc, payment.transactionId desc
            """, countQuery = """
            select count(payment) from TransactionDb payment
             where (:customerId is null or payment.customer.userId = :customerId)
               and (:state is null or payment.state = :state)
               and (:fromTime is null or payment.createdAt >= :fromTime)
               and (:toTime is null or payment.createdAt < :toTime)
            """)
    Page<TransactionDb> searchForAudit(@Param("customerId") Long customerId,
            @Param("state") TransactionState state, @Param("fromTime") OffsetDateTime from,
            @Param("toTime") OffsetDateTime to, Pageable pageable);

    @Query("""
            select payment from TransactionDb payment
              join fetch payment.customer
              join fetch payment.sourceAccount
              join fetch payment.beneficiary
              left join fetch payment.riskPolicy
              left join fetch payment.riskPolicyBand
              left join fetch payment.protectionPolicy
             where payment.transactionId = :transactionId
            """)
    Optional<TransactionDb> findForAuditById(@Param("transactionId") Long transactionId);

    @Query(value = """
            select payment from TransactionDb payment
              join fetch payment.customer customer
              join fetch payment.sourceAccount sourceAccount
              join fetch payment.beneficiary beneficiary
              left join payment.destinationAccount destinationAccount
              left join destinationAccount.owner destinationOwner
             where (customer.userId = :customerUserId
                or destinationOwner.userId = :customerUserId)
               and (:fromTime is null or payment.createdAt >= :fromTime)
               and (:toTime is null or payment.createdAt < :toTime)
               and (:state is null or payment.state = :state)
               and (:sourceAccountId is null or sourceAccount.accountId = :sourceAccountId)
             order by payment.createdAt desc, payment.transactionId desc
            """, countQuery = """
            select count(payment) from TransactionDb payment
              left join payment.destinationAccount destinationAccount
              left join destinationAccount.owner destinationOwner
             where (payment.customer.userId = :customerUserId
                or destinationOwner.userId = :customerUserId)
               and (:fromTime is null or payment.createdAt >= :fromTime)
               and (:toTime is null or payment.createdAt < :toTime)
               and (:state is null or payment.state = :state)
               and (:sourceAccountId is null or payment.sourceAccount.accountId = :sourceAccountId)
            """)
    Page<TransactionDb> searchOwned(@Param("customerUserId") Long customerUserId,
            @Param("fromTime") OffsetDateTime from, @Param("toTime") OffsetDateTime to,
            @Param("state") TransactionState state, @Param("sourceAccountId") Long sourceAccountId,
            Pageable pageable);

    @Query("""
            select payment
              from TransactionDb payment
              join fetch payment.customer customer
              join fetch payment.sourceAccount sourceAccount
              join fetch payment.beneficiary beneficiary
              left join fetch payment.riskPolicy riskPolicy
              left join fetch payment.riskPolicyBand riskPolicyBand
              left join fetch payment.protectionPolicy protectionPolicy
              left join payment.destinationAccount destinationAccount
              left join destinationAccount.owner destinationOwner
             where payment.transactionId = :transactionId
               and (customer.userId = :customerUserId
                    or destinationOwner.userId = :customerUserId)
            """)
    Optional<TransactionDb> findOwnedById(
            @Param("transactionId") Long transactionId,
            @Param("customerUserId") Long customerUserId);

    @Query(
            value = """
                    select payment
                      from TransactionDb payment
                      join fetch payment.customer customer
                      join fetch payment.sourceAccount sourceAccount
                      join fetch payment.beneficiary beneficiary
                      left join payment.destinationAccount destinationAccount
                      left join destinationAccount.owner destinationOwner
                     where customer.userId = :customerUserId
                        or destinationOwner.userId = :customerUserId
                     order by payment.createdAt desc,
                              payment.transactionId desc
                    """,
            countQuery = """
                    select count(payment)
                      from TransactionDb payment
                      left join payment.destinationAccount destinationAccount
                      left join destinationAccount.owner destinationOwner
                     where payment.customer.userId = :customerUserId
                        or destinationOwner.userId = :customerUserId
                    """)
    Page<TransactionDb> findAllOwned(
            @Param("customerUserId") Long customerUserId,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select payment
              from TransactionDb payment
             where payment.transactionId = :transactionId
               and payment.customer.userId = :customerUserId
            """)
    Optional<TransactionDb> findOwnedByIdForUpdate(
            @Param("transactionId") Long transactionId,
            @Param("customerUserId") Long customerUserId);

    @Query(
            value = """
                    select transaction_id
                      from (
                            select transaction_id
                              from SAFEPAY_OWNER.PAYMENT_TRANSACTION
                             where state = 'PROTECTED'
                               and risk_tier in ('MEDIUM', 'HIGH')
                               and protected_until is not null
                               and protected_until <= SYSTIMESTAMP
                             order by protected_until asc,
                                      transaction_id asc
                           )
                     where rownum <= :batchSize
                    """,
            nativeQuery = true)
    List<Long> findExpiredProtectedTransactionIds(
            @Param("batchSize") int batchSize);

    @Query(
            value = """
                    select transaction_id
                      from (
                            select payment.transaction_id,
                                   nvl(exception_log.next_retry_at,
                                       payment.released_at) as due_at
                              from SAFEPAY_OWNER.PAYMENT_TRANSACTION payment
                              left join SAFEPAY_OWNER.TRANSACTION_EXCEPTION exception_log
                                on exception_log.transaction_exception_id = (
                                       select max(latest.transaction_exception_id)
                                         from SAFEPAY_OWNER.TRANSACTION_EXCEPTION latest
                                        where latest.transaction_id = payment.transaction_id
                                          and latest.processing_stage = 'SETTLEMENT'
                                   )
                             where payment.state = 'RELEASED'
                               and (
                                    exception_log.transaction_exception_id is null
                                    or (
                                        exception_log.status = 'RETRY_PENDING'
                                        and exception_log.next_retry_at <= SYSTIMESTAMP
                                    )
                               )
                             order by nvl(exception_log.next_retry_at,
                                          payment.released_at) asc,
                                      payment.transaction_id asc
                           )
                     where rownum <= :batchSize
                    """,
            nativeQuery = true)
    List<Long> findDueSettlementTransactionIds(
            @Param("batchSize") int batchSize);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select payment
              from TransactionDb payment
             where payment.transactionId = :transactionId
            """)
    Optional<TransactionDb> findByIdForUpdate(
            @Param("transactionId") Long transactionId);

    @Query(
            value = "SELECT SYSTIMESTAMP FROM DUAL",
            nativeQuery = true)
    OffsetDateTime currentDatabaseTime();
}
