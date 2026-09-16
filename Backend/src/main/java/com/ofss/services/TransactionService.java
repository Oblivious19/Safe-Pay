package com.ofss.services;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.ofss.beans.TransactionDb;

public interface TransactionService {
    TransactionDb initiate(Long accountId, Long beneficiaryId, BigDecimal amount, String purpose,
            String idempotencyKey, Long callerId);
    TransactionDb getTransaction(Long transactionId, String email);
    List<TransactionDb> getTransactions(String state, String email);
    TransactionDb cancel(Long transactionId, String idempotencyKey, String email);
    LocalDateTime currentDatabaseTime(Long accountId);
    void releaseExpiredTransactions();
}
