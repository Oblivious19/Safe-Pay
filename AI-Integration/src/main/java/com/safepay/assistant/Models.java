package com.safepay.assistant;
import java.time.LocalDateTime;
import java.time.Instant;
import java.util.List;
public final class Models {
    private Models() {}
    public record Identity(Long userId,String name,String email,String role,String status) {}
    public record LoginInput(String email,String phone,String password) {}
    public record BackendSession(String cookie,Identity identity) {}
    public record Pending(Long transactionId,String transactionRef,String amount,Long userId,String customerName,
        Long fromAccountId,String sourceAccountNumber,String beneficiaryName,String beneficiaryBankAccountNumber,
        String beneficiaryIfsc,String purpose,String riskReason,String createdAt) {}
    public record Fact(String id,String label,String text,List<Long> transactionIds) {}
    public record HistoryRow(Long transactionId,String reference,String amount,LocalDateTime createdAt,LocalDateTime settledAt) {}
    public record Evidence(Long transactionId,String reference,Long version,Long ownerId,Long accountId,
        String amount,String state,String riskTier,String riskReason,String beneficiaryName,String maskedBeneficiary,
        String purpose,LocalDateTime paymentCreatedAt,Instant observedAt,String historyScope,List<Fact> facts,
        List<HistoryRow> recentSettledPayments) {}
    public record AiSelection(List<String> focusEvidenceIds,List<String> suggestedChecks) {}
    public record Review(String reportId,Evidence evidence,String mode,String model,String message,
        List<Fact> focus,List<String> suggestedChecks,Instant generatedAt,String evidenceSha256) {}
}
