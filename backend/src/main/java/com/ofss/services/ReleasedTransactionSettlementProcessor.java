package com.ofss.services;

public interface ReleasedTransactionSettlementProcessor {
    int processDueTransactions(int batchSize);
}
