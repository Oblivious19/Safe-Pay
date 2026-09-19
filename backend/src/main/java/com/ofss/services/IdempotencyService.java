package com.ofss.services;

import java.util.function.Supplier;

import com.ofss.beans.IdempotencyOperation;

public interface IdempotencyService {

    <T> IdempotencyExecutionResult<T> execute(
            Long userId,
            IdempotencyOperation operationCode,
            String idempotencyKey,
            String requestHash,
            String correlationId,
            Class<T> responseType,
            Supplier<IdempotencyExecutionResult<T>> action);
}
