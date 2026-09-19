package com.ofss.services;

import java.util.List;
import java.util.Objects;

import com.ofss.beans.LedgerEntry;
import com.ofss.beans.LedgerEntryType;
import com.ofss.beans.LedgerPosting;

public record SettlementPostingPair(
        LedgerPosting posting,
        LedgerEntry debitEntry,
        LedgerEntry creditEntry) {

    public SettlementPostingPair {
        Objects.requireNonNull(posting, "posting is required");
        Objects.requireNonNull(
                debitEntry,
                "debitEntry is required");
        Objects.requireNonNull(
                creditEntry,
                "creditEntry is required");

        requireEntryMatchesPosting(
                debitEntry,
                posting,
                LedgerEntryType.DEBIT,
                1);

        requireEntryMatchesPosting(
                creditEntry,
                posting,
                LedgerEntryType.CREDIT,
                2);

        if (Objects.equals(
                debitEntry.getAccount().getAccountId(),
                creditEntry.getAccount().getAccountId())) {
            throw new IllegalArgumentException(
                    "Settlement entries must use different accounts");
        }

        if (debitEntry.getAmount().compareTo(
                creditEntry.getAmount()) != 0) {
            throw new IllegalArgumentException(
                    "Settlement debit and credit must balance");
        }
    }

    public List<LedgerEntry> entriesInPostingOrder() {
        return List.of(debitEntry, creditEntry);
    }

    private static void requireEntryMatchesPosting(
            LedgerEntry entry,
            LedgerPosting posting,
            LedgerEntryType expectedType,
            int expectedLineNumber) {

        if (entry.getPosting() != posting
                || entry.getTransaction()
                        != posting.getTransaction()
                || entry.getEntryType() != expectedType
                || entry.getLineNumber() != expectedLineNumber
                || !Objects.equals(
                        entry.getSourceSystem(),
                        posting.getSourceSystem())
                || !Objects.equals(
                        entry.getIdempotencyKey(),
                        posting.getIdempotencyKey())
                || entry.getAmount().compareTo(
                        posting.getAmount()) != 0
                || entry.getCurrencyCode()
                        != posting.getCurrencyCode()) {

            throw new IllegalArgumentException(
                    "Ledger entry does not match posting identity");
        }
    }
}
