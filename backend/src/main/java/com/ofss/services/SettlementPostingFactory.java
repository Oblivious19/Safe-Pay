package com.ofss.services;

import java.time.OffsetDateTime;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.ofss.beans.Account;
import com.ofss.beans.LedgerEntry;
import com.ofss.beans.LedgerPosting;
import com.ofss.beans.TransactionDb;
import com.ofss.scheduler.SettlementProcessorProperties;

@Component
public class SettlementPostingFactory {

    private static final String POSTING_REFERENCE_PREFIX =
            "PAYMENT-SETTLEMENT-";

    private static final String IDEMPOTENCY_KEY_PREFIX =
            "TRANSACTION_SETTLE:";

    private final SettlementProcessorProperties properties;

    public SettlementPostingFactory(
            SettlementProcessorProperties properties) {

        this.properties = Objects.requireNonNull(
                properties,
                "properties is required");
    }

    public SettlementPostingPair create(
            TransactionDb transaction,
            Account clearingAccount,
            OffsetDateTime createdAt) {

        Objects.requireNonNull(
                transaction,
                "transaction is required");
        Objects.requireNonNull(
                clearingAccount,
                "clearingAccount is required");

        long configuredClearingAccountId =
                properties.requireOutboundClearingAccountId();

        if (!Objects.equals(
                clearingAccount.getAccountId(),
                configuredClearingAccountId)) {
            throw new IllegalArgumentException(
                    "clearingAccount does not match configured outbound account");
        }

        LedgerPosting posting =
                LedgerPosting.createPaymentSettlement(
                        transaction,
                        postingReference(transaction),
                        idempotencyKey(transaction),
                        createdAt);

        LedgerEntry debitEntry =
                LedgerEntry.createPaymentSettlementDebit(
                        posting,
                        transaction.getSourceAccount(),
                        createdAt);

        LedgerEntry creditEntry =
                LedgerEntry.createPaymentSettlementCredit(
                        posting,
                        clearingAccount,
                        createdAt);

        return new SettlementPostingPair(
                posting,
                debitEntry,
                creditEntry);
    }

    public SettlementPostingPair createToDestination(
            TransactionDb transaction,
            Account destinationAccount,
            OffsetDateTime createdAt) {
        LedgerPosting posting = LedgerPosting.createPaymentSettlement(transaction,
                postingReference(transaction), idempotencyKey(transaction), createdAt);
        LedgerEntry debitEntry = LedgerEntry.createPaymentSettlementDebit(posting,
                transaction.getSourceAccount(), createdAt);
        LedgerEntry creditEntry = LedgerEntry.createPaymentSettlementDestinationCredit(posting,
                destinationAccount, createdAt);
        return new SettlementPostingPair(posting, debitEntry, creditEntry);
    }

    private static String postingReference(
            TransactionDb transaction) {

        Long transactionId = transaction.getTransactionId();

        if (transactionId == null || transactionId <= 0L) {
            throw new IllegalArgumentException(
                    "transaction must already be persisted");
        }

        return POSTING_REFERENCE_PREFIX + transactionId;
    }

    private static String idempotencyKey(
            TransactionDb transaction) {

        String transactionReference =
                transaction.getTransactionReference();

        if (transactionReference == null
                || transactionReference.isBlank()) {
            throw new IllegalArgumentException(
                    "transactionReference is required");
        }

        return IDEMPOTENCY_KEY_PREFIX
                + transactionReference.trim();
    }
}
