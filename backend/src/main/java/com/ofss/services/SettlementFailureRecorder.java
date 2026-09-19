package com.ofss.services;

public interface SettlementFailureRecorder {
    SettlementFailureDisposition recordFailure(
            Long transactionId,
            String correlationId,
            RuntimeException failure);
}
