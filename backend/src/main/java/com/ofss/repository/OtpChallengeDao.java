package com.ofss.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.ofss.beans.OtpChallenge;
import jakarta.persistence.LockModeType;

public interface OtpChallengeDao
        extends Repository<OtpChallenge, Long> {

    <S extends OtpChallenge> S save(S challenge);

    <S extends OtpChallenge> S saveAndFlush(S challenge);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select challenge
              from OtpChallenge challenge
             where challenge.otpChallengeId = :challengeId
               and challenge.transaction.transactionId = :transactionId
               and challenge.customer.userId = :customerUserId
            """)
    Optional<OtpChallenge> findOwnedByIdForUpdate(
            @Param("challengeId") Long challengeId,
            @Param("transactionId") Long transactionId,
            @Param("customerUserId") Long customerUserId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select challenge
              from OtpChallenge challenge
             where challenge.transaction.transactionId = :transactionId
               and challenge.customer.userId = :customerUserId
               and challenge.status = com.ofss.beans.OtpChallengeStatus.PENDING
             order by challenge.createdAt desc,
                      challenge.otpChallengeId desc
            """)
    Optional<OtpChallenge> findPendingOwnedForUpdate(
            @Param("transactionId") Long transactionId,
            @Param("customerUserId") Long customerUserId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select challenge
              from OtpChallenge challenge
             where challenge.transaction.transactionId = :transactionId
               and challenge.customer.userId = :customerUserId
             order by challenge.createdAt desc,
                      challenge.otpChallengeId desc
            """)
    Optional<OtpChallenge> findLatestOwnedForUpdate(
            @Param("transactionId") Long transactionId,
            @Param("customerUserId") Long customerUserId);

    Optional<OtpChallenge>
            findFirstByTransaction_TransactionIdAndCustomer_UserIdOrderByCreatedAtDescOtpChallengeIdDesc(
                    Long transactionId,
                    Long customerUserId);

    List<OtpChallenge>
            findAllByTransaction_TransactionIdAndCustomer_UserIdOrderByCreatedAtAscOtpChallengeIdAsc(
                    Long transactionId,
                    Long customerUserId);

    long countByTransaction_TransactionIdAndCustomer_UserIdAndCreatedAtGreaterThanEqual(
            Long transactionId,
            Long customerUserId,
            OffsetDateTime cycleStartedAt);
}
