package com.ofss.services;

public interface SettlementService {
    SettlementAttemptOutcome settleIfReleased(
            Long transactionId,
            String correlationId);
}
