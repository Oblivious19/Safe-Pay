package com.ofss.repository;

import java.util.List;

import org.springframework.data.repository.Repository;

import com.ofss.beans.LedgerEntry;

public interface LedgerEntryDao
        extends Repository<LedgerEntry, Long> {

    <S extends LedgerEntry> S save(S entry);

    <S extends LedgerEntry> List<S> saveAll(
            Iterable<S> entries);

    List<LedgerEntry> findAllByPosting_PostingIdOrderByLineNumberAsc(
            Long postingId);

    List<LedgerEntry> findAllByTransaction_TransactionIdOrderByLineNumberAsc(
            Long transactionId);
}
