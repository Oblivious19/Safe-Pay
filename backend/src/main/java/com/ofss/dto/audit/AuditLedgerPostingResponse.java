package com.ofss.dto.audit;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import com.ofss.beans.*;
import com.ofss.common.SensitiveDataMasker;

public record AuditLedgerPostingResponse(Header posting, List<Entry> entries) {
    public AuditLedgerPostingResponse { entries = List.copyOf(entries); }

    public record Header(String postingId, String postingReference, LedgerPostingType postingType,
            String transactionId, BigDecimal amount, CurrencyCode currencyCode, int expectedEntryCount,
            LedgerPostingStatus status, OffsetDateTime createdAt, OffsetDateTime postedAt,
            OffsetDateTime failedAt, OffsetDateTime updatedAt) {
        public static Header from(LedgerPosting p) {
            return new Header(p.getPostingId().toString(), p.getPostingReference(), p.getPostingType(),
                    p.getTransaction() == null ? null : p.getTransaction().getTransactionId().toString(),
                    p.getAmount(), p.getCurrencyCode(), p.getExpectedEntryCount(), p.getStatus(),
                    p.getCreatedAt(), p.getPostedAt(), p.getFailedAt(), p.getUpdatedAt());
        }
    }
    public record Entry(String ledgerEntryId, int lineNumber, String accountId, String maskedAccountNumber,
            LedgerEntryType entryType, BigDecimal amount, CurrencyCode currencyCode,
            LedgerEntryStatus status, OffsetDateTime createdAt) {
        public static Entry from(LedgerEntry e) {
            return new Entry(e.getLedgerEntryId().toString(), e.getLineNumber(),
                    e.getAccount().getAccountId().toString(),
                    SensitiveDataMasker.maskAccountNumber(e.getAccount().getAccountNumber()),
                    e.getEntryType(), e.getAmount(), e.getCurrencyCode(), e.getStatus(), e.getCreatedAt());
        }
    }
}
