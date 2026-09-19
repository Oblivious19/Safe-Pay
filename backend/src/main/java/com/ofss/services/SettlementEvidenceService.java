package com.ofss.services;

import java.time.OffsetDateTime;

import com.ofss.beans.LedgerPosting;
import com.ofss.beans.TransactionDb;

public interface SettlementEvidenceService {
    void appendSuccessfulSettlement(
            TransactionDb transaction,
            LedgerPosting posting,
            String correlationId,
            OffsetDateTime occurredAt);
}
