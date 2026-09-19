package com.ofss.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.ofss.beans.IdempotencyOperation;
import com.ofss.beans.IdempotencyRecord;

import jakarta.persistence.LockModeType;

public interface IdempotencyRecordDao
        extends Repository<IdempotencyRecord, Long> {

    <S extends IdempotencyRecord> S save(S record);

    <S extends IdempotencyRecord> S saveAndFlush(S record);

    @Query("""
            select record
              from IdempotencyRecord record
             where record.user.userId = :userId
               and record.operationCode = :operationCode
               and record.idempotencyKey = :idempotencyKey
            """)
    Optional<IdempotencyRecord> findByScope(
            @Param("userId") Long userId,
            @Param("operationCode")
                    IdempotencyOperation operationCode,
            @Param("idempotencyKey") String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select record
              from IdempotencyRecord record
             where record.user.userId = :userId
               and record.operationCode = :operationCode
               and record.idempotencyKey = :idempotencyKey
            """)
    Optional<IdempotencyRecord> findByScopeForUpdate(
            @Param("userId") Long userId,
            @Param("operationCode")
                    IdempotencyOperation operationCode,
            @Param("idempotencyKey") String idempotencyKey);
}
