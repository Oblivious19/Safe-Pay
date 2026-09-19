package com.ofss.services;

import java.time.OffsetDateTime;

import com.ofss.beans.TransactionState;
import com.ofss.common.api.PagedResponse;
import com.ofss.dto.transaction.AuthorizeTransactionRequest;
import com.ofss.dto.transaction.CreateTransactionRequest;
import com.ofss.dto.transaction.TransactionRiskExplanationResponse;
import com.ofss.dto.transaction.TransactionResponse;
import com.ofss.dto.transaction.TransactionSummaryResponse;

public interface TransactionService {

    /*
     * Deferred Spring Security integration:
     * customerUserId comes from the authenticated principal and must never
     * be accepted from public request JSON.
     */
    TransactionResponse createTransaction(
            Long customerUserId,
            CreateTransactionRequest request);

    TransactionResponse createTransaction(
            Long customerUserId,
            CreateTransactionRequest request,
            OperationContext context);

    TransactionResponse authorizeTransaction(
            Long customerUserId,
            Long transactionId,
            AuthorizeTransactionRequest request);

    TransactionResponse authorizeTransaction(
            Long customerUserId,
            Long transactionId,
            AuthorizeTransactionRequest request,
            OperationContext context);

    TransactionResponse cancelTransaction(
            Long customerUserId,
            Long transactionId);

    TransactionResponse cancelTransaction(
            Long customerUserId,
            Long transactionId,
            OperationContext context);

    PagedResponse<TransactionSummaryResponse> listTransactions(
            Long customerUserId,
            int page,
            int size);

    PagedResponse<TransactionSummaryResponse> listTransactions(
            Long customerUserId, OffsetDateTime from, OffsetDateTime to,
            TransactionState state, Long sourceAccountId, int page, int size);

    TransactionResponse getTransaction(
            Long customerUserId,
            Long transactionId);

    TransactionRiskExplanationResponse getRiskExplanation(
            Long customerUserId,
            Long transactionId);
}
