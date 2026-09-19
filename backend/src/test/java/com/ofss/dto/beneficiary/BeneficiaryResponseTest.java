package com.ofss.dto.beneficiary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.ofss.beans.Beneficiary;
import com.ofss.beans.BeneficiaryPaymentMethod;
import com.ofss.beans.BeneficiaryStatus;

class BeneficiaryResponseTest {

    private static final OffsetDateTime CREATED_AT =
            OffsetDateTime.of(
                    2026,
                    9,
                    14,
                    12,
                    0,
                    0,
                    0,
                    ZoneOffset.UTC);

    @Test
    void mapsBankAccountWithMaskedIdentifier() {
        Beneficiary beneficiary = commonBeneficiary();

        when(beneficiary.getPaymentMethod()).thenReturn(
                BeneficiaryPaymentMethod.BANK_ACCOUNT);
        when(beneficiary.getBankName()).thenReturn(
                "SafePay Demo Bank");
        when(beneficiary.getBankAccountNumber()).thenReturn(
                "123456789012");
        when(beneficiary.getIfscCode()).thenReturn(
                "ABCD0123456");

        BeneficiaryResponse response =
                BeneficiaryResponse.from(beneficiary);

        assertThat(response.beneficiaryId()).isEqualTo("501");
        assertThat(response.paymentMethod()).isEqualTo(
                BeneficiaryPaymentMethod.BANK_ACCOUNT);
        assertThat(response.maskedDestinationIdentifier())
                .isEqualTo("********9012");
        assertThat(response.maskedDestinationIdentifier())
                .doesNotContain("123456789012");
        assertThat(response.ifscCode())
                .isEqualTo("ABCD0123456");
    }

    @Test
    void mapsUpiWithMaskedIdentifier() {
        Beneficiary beneficiary = commonBeneficiary();

        when(beneficiary.getPaymentMethod()).thenReturn(
                BeneficiaryPaymentMethod.UPI);
        when(beneficiary.getUpiId()).thenReturn(
                "merchant.pay@examplebank");

        BeneficiaryResponse response =
                BeneficiaryResponse.from(beneficiary);

        assertThat(response.paymentMethod()).isEqualTo(
                BeneficiaryPaymentMethod.UPI);
        assertThat(response.maskedDestinationIdentifier())
                .isEqualTo(
                        "m**********y@examplebank");
        assertThat(response.maskedDestinationIdentifier())
                .doesNotContain("merchant.pay");
    }

    @Test
    void rejectsUnpersistedBeneficiary() {
        Beneficiary beneficiary = mock(Beneficiary.class);

        assertThatThrownBy(
                () -> BeneficiaryResponse.from(beneficiary))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "beneficiary must already be persisted");
    }

    private Beneficiary commonBeneficiary() {
        Beneficiary beneficiary = mock(Beneficiary.class);

        when(beneficiary.getBeneficiaryId()).thenReturn(501L);
        when(beneficiary.getBeneficiaryName()).thenReturn(
                "Demo Beneficiary");
        when(beneficiary.getNickname()).thenReturn(
                "Demo");
        when(beneficiary.getRelationshipLabel()).thenReturn(
                "Supplier");
        when(beneficiary.getPurposeNote()).thenReturn(
                "Monthly invoice");
        when(beneficiary.getStatus()).thenReturn(
                BeneficiaryStatus.ACTIVE);
        when(beneficiary.getCreatedAt()).thenReturn(CREATED_AT);
        when(beneficiary.getUpdatedAt()).thenReturn(CREATED_AT);

        return beneficiary;
    }
}
