package com.ofss.dto.audit;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ofss.beans.*;

class AuditReportingResponseTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    @Test void openingPostingDoesNotExposeReplayIdentity() throws Exception {
        var posting = mock(LedgerPosting.class);
        when(posting.getPostingId()).thenReturn(1L);
        when(posting.getPostingType()).thenReturn(LedgerPostingType.OPENING_BALANCE);
        when(posting.getAmount()).thenReturn(new BigDecimal("123.45"));
        var response = new AuditLedgerPostingResponse(AuditLedgerPostingResponse.Header.from(posting), List.of());
        var json = mapper.valueToTree(response);
        assertThat(json.at("/posting/transactionId").isNull()).isTrue();
        assertThat(json.at("/posting/amount").decimalValue()).isEqualByComparingTo("123.45");
        assertThat(mapper.writeValueAsString(response)).doesNotContain("idempotencyKey", "sourceSystem", "currentBalance");
    }
    @Test void entryAccountNumbersAreMaskedAndMoneyIsExact() {
        var account = mock(Account.class);
        when(account.getAccountId()).thenReturn(5L);
        when(account.getAccountNumber()).thenReturn("123456789012");
        var entry = mock(LedgerEntry.class);
        when(entry.getLedgerEntryId()).thenReturn(7L);
        when(entry.getAccount()).thenReturn(account);
        when(entry.getAmount()).thenReturn(new BigDecimal("0.01"));
        var response = AuditLedgerPostingResponse.Entry.from(entry);
        assertThat(response.maskedAccountNumber()).endsWith("9012").doesNotContain("12345678");
        assertThat(response.amount()).isEqualByComparingTo("0.01");
    }
    @Test void exceptionOmitsRawMessagesResolutionNotesAndHandlesNullReferences() throws Exception {
        var exception = mock(TransactionExceptionLog.class);
        when(exception.getTransactionExceptionId()).thenReturn(9L);
        when(exception.getErrorCode()).thenReturn("UNRECOGNIZED");
        when(exception.getErrorMessage()).thenReturn("secret password");
        when(exception.getResolutionNote()).thenReturn("private note");
        var response = AuditExceptionResponse.from(exception);
        assertThat(response.transactionId()).isNull();
        assertThat(response.postingId()).isNull();
        assertThat(response.displayExplanation()).startsWith("Processing issue recorded");
        assertThat(mapper.writeValueAsString(response)).doesNotContain("errorMessage", "resolutionNote", "secret", "private note");
    }
    @Test void openEndedBandAndProtectionFlagsArePreserved() {
        var protection = mock(ProtectionPolicy.class);
        when(protection.isOtpRequired()).thenReturn(true);
        when(protection.isRiskReviewRequired()).thenReturn(true);
        var band = mock(RiskPolicyBand.class);
        when(band.getRiskPolicyBandId()).thenReturn(4L);
        when(band.getDisplayOrder()).thenReturn(4);
        when(band.getProtectionPolicy()).thenReturn(protection);
        when(band.getMinimumAmount()).thenReturn(new BigDecimal("100000.01"));
        var response = AuditRiskPolicyResponse.Band.from(band);
        assertThat(response.maximumAmount()).isNull();
        assertThat(response.otpRequired()).isTrue();
        assertThat(response.riskReviewRequired()).isTrue();
        assertThat(response.minimumAmount()).isEqualByComparingTo("100000.01");
    }
    @Test void reservationResponseContainsOnlyApprovedMoneyFields() throws Exception {
        var response = new AuditReconciliationResponse.Reservation("1", "********9012", "INR",
                new BigDecimal("20.00"), new BigDecimal("15.00"), new BigDecimal("5.00"), "MISMATCH", null);
        var json = mapper.writeValueAsString(response);
        assertThat(json).contains("storedReservedAmount", "calculatedReservedAmount", "reservationDifference")
                .doesNotContain("currentBalance", "availableBalance", "owner", "accountNumber");
    }
}
