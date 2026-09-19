package com.ofss.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.Repository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import com.ofss.beans.AuthSession;

public interface AuthSessionDao
        extends Repository<AuthSession, Long> {

    <S extends AuthSession> S save(S session);

    Optional<AuthSession> findById(Long sessionId);

    Optional<AuthSession> findByRefreshTokenHash(
            String refreshTokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select authSession
              from AuthSession authSession
              join fetch authSession.user
             where authSession.refreshTokenHash = :refreshTokenHash
            """)
    Optional<AuthSession> findByRefreshTokenHashForUpdate(
            @Param("refreshTokenHash") String refreshTokenHash);

    boolean existsByRefreshTokenHash(
            String refreshTokenHash);

    List<AuthSession> findAllByTokenFamilyKeyOrderByCreatedAtAsc(
            String tokenFamilyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select authSession
              from AuthSession authSession
             where authSession.tokenFamilyKey = :tokenFamilyKey
             order by authSession.createdAt asc
            """)
    List<AuthSession> findFamilyForUpdate(
            @Param("tokenFamilyKey") String tokenFamilyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<AuthSession>
            findAllByUser_UserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtAsc(
                    Long userId,
                    OffsetDateTime instant);
}
