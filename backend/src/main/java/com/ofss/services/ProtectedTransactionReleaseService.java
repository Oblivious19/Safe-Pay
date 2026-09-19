package com.ofss.services;

public interface ProtectedTransactionReleaseService {

    int releaseDueTransactions(int batchSize);
}
