package com.ofss.services;

public interface ProtectedTransactionReleaseWorker {

    ProtectedTransactionReleaseOutcome releaseIfExpired(
            Long transactionId);
}
