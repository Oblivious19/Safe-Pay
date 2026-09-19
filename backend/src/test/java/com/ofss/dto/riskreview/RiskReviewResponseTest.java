package com.ofss.dto.riskreview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ofss.beans.Account;
import com.ofss.beans.Beneficiary;
import com.ofss.beans.CurrencyCode;
import com.ofss.beans.RiskReview;
import com.ofss.beans.RiskReviewStatus;
import com.ofss.beans.RiskTier;
import com.ofss.beans.TransactionDb;
import com.ofss.beans.TransactionState;
import com.ofss.beans.User;

class RiskReviewResponseTest {

    private static final OffsetDateTime NOW =
            OffsetDateTime.parse("2026-09-16T16:00:00Z");

    private RiskReview review;
    private User customer;

    @BeforeEach
    void setUp() {
        review = mock(RiskReview.class);
        TransactionDb transaction = mock(TransactionDb.class);
        customer = user(11L, "Customer One");
        Account sourceAccount = mock(Account.class);
        Beneficiary beneficiary = mock(Beneficiary.class);

        when(review.getApprovalId()).thenReturn(501L);
        when(review.getReviewRound()).thenReturn(2);
        when(review.getStatus()).thenReturn(RiskReviewStatus.PENDING);
        when(review.getTransaction()).thenReturn(transaction);
        when(review.getCustomer()).thenReturn(customer);
        when(review.getRequestedAt()).thenReturn(NOW.minusMinutes(1));
        when(review.getUpdatedAt()).thenReturn(NOW);

        when(customer.getEmail()).thenReturn("customer@example.com");
        when(transaction.getTransactionId()).thenReturn(101L);
        when(transaction.getTransactionReference()).thenReturn("SP-101");
        when(transaction.getState()).thenReturn(
                TransactionState.PENDING_RISK_REVIEW);
        when(transaction.getSourceAccount()).thenReturn(sourceAccount);
        when(transaction.getBeneficiary()).thenReturn(beneficiary);
        when(transaction.getAmount())
                .thenReturn(new BigDecimal("125000.00"));
        when(transaction.getCurrencyCode()).thenReturn(CurrencyCode.INR);
        when(transaction.getRiskTier()).thenReturn(RiskTier.VERY_HIGH);
        when(transaction.getPolicyVersion()).thenReturn("V1");
        when(transaction.getRiskExplanation())
                .thenReturn("Manual review required");
        when(transaction.getVerificationCompletedAt())
                .thenReturn(NOW.minusSeconds(30));

        when(sourceAccount.getAccountId()).thenReturn(71L);
        when(sourceAccount.getAccountNumber())
                .thenReturn("1234567890123456");
        when(beneficiary.getBeneficiaryId()).thenReturn(81L);
        when(beneficiary.getBeneficiaryName()).thenReturn("Vendor One");
    }

    @Test
    void summaryMasksCustomerEmailAndSourceAccount() {
        RiskReviewSummaryResponse response =
                RiskReviewSummaryResponse.from(review);

        assertThat(response.reviewId()).isEqualTo("501");
        assertThat(response.reviewRound()).isEqualTo(2);
        assertThat(response.maskedCustomerEmail())
                .isEqualTo("c*******@example.com");
        assertThat(response.maskedSourceAccountNumber())
                .isEqualTo("************3456");
        assertThat(response.riskTier()).isEqualTo(RiskTier.VERY_HIGH);
    }

    @Test
    void reviewJsonIncludesCategoryAndPurposeAndPreservesLegacyNull() throws Exception {
        var mapper = org.springframework.http.converter.json.Jackson2ObjectMapperBuilder.json().build();
        when(review.getTransaction().getCategory()).thenReturn(com.ofss.beans.PaymentCategory.OTHERS);
        when(review.getTransaction().getPurpose()).thenReturn("Equipment purchase");
        var json = mapper.valueToTree(RiskReviewDetailResponse.from(review));
        assertThat(json.at("/review/category").asText()).isEqualTo("OTHERS");
        assertThat(json.at("/review/purpose").asText()).isEqualTo("Equipment purchase");
        assertThat(mapper.treeToValue(json, RiskReviewDetailResponse.class).review().category())
                .isEqualTo(com.ofss.beans.PaymentCategory.OTHERS);
        when(review.getTransaction().getCategory()).thenReturn(null);
        json = mapper.valueToTree(RiskReviewDetailResponse.from(review));
        assertThat(json.at("/review/category").isNull()).isTrue();
        assertThat(json.at("/review/purpose").asText()).isEqualTo("Equipment purchase");
    }

    @Test
    void detailExposesOnlyDecisionMetadataNotSensitiveOtpData() {
        User officer = user(22L, "Risk Officer");
        when(review.getAssignedRiskOfficer()).thenReturn(officer);
        when(review.getDecidedByUser()).thenReturn(officer);
        when(review.getDecisionReason()).thenReturn("Approved after review");
        when(review.getClaimedAt()).thenReturn(NOW);
        when(review.getDecidedAt()).thenReturn(NOW);
        when(review.getVersionNo()).thenReturn(3L);

        RiskReviewDetailResponse response =
                RiskReviewDetailResponse.from(review);

        assertThat(response.assignedRiskOfficerId()).isEqualTo("22");
        assertThat(response.decidedByUserName())
                .isEqualTo("Risk Officer");
        assertThat(response.decisionReason())
                .isEqualTo("Approved after review");
        assertThat(response.version()).isEqualTo(3L);
    }

    private static User user(Long id, String fullName) {
        User user = mock(User.class);
        when(user.getUserId()).thenReturn(id);
        when(user.getFullName()).thenReturn(fullName);
        return user;
    }
}
