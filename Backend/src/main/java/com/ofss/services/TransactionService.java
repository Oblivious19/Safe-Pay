package com.ofss.services;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.ofss.beans.TransactionDb;
import com.ofss.beans.PaymentCategory;

public interface TransactionService {
    default TransactionDb initiate(Long accountId, Long beneficiaryId, BigDecimal amount, String purpose,
            String idempotencyKey, Long callerId) {
        return initiate(accountId, beneficiaryId, amount, purpose, idempotencyKey, callerId, null);
    }
    TransactionDb initiate(Long accountId, Long beneficiaryId, BigDecimal amount, String purpose,
            String idempotencyKey, Long callerId, PaymentCategory category);
    TransactionDb getTransaction(Long transactionId, String email);
    List<TransactionDb> getTransactions(String state, String email);
    TransactionDb cancel(Long transactionId, String idempotencyKey, String email);
    LocalDateTime currentDatabaseTime(Long accountId);
    void releaseExpiredTransactions();
}
