package com.ofss.beans;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class BeneficiaryTest {

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
    void createsCanonicalBankAccountBeneficiary() {
        User owner = persistedOwner();

        Beneficiary beneficiary =
                Beneficiary.createBankAccountBeneficiary(
                        owner,
                        "  Aditya Supplier  ",
                        "  Office Vendor  ",
                        "  SafePay Demo Bank  ",
                        "  123456789012  ",
                        " abcd0123456 ",
                        "  Supplier  ",
                        "  Monthly invoice  ",
                        CREATED_AT);

        assertThat(beneficiary.getBeneficiaryId()).isNull();
        assertThat(beneficiary.getOwner()).isSameAs(owner);
        assertThat(beneficiary.getBeneficiaryName())
                .isEqualTo("Aditya Supplier");
        assertThat(beneficiary.getNickname())
                .isEqualTo("Office Vendor");
        assertThat(beneficiary.getPaymentMethod())
                .isEqualTo(
                        BeneficiaryPaymentMethod.BANK_ACCOUNT);
        assertThat(beneficiary.getBankName())
                .isEqualTo("SafePay Demo Bank");
        assertThat(beneficiary.getBankAccountNumber())
                .isEqualTo("123456789012");
        assertThat(beneficiary.getIfscCode())
                .isEqualTo("ABCD0123456");
        assertThat(beneficiary.getUpiId()).isNull();
        assertThat(beneficiary.getRelationshipLabel())
                .isEqualTo("Supplier");
        assertThat(beneficiary.getPurposeNote())
                .isEqualTo("Monthly invoice");
        assertThat(beneficiary.getStatus())
                .isEqualTo(BeneficiaryStatus.ACTIVE);
        assertThat(beneficiary.canReceiveNewPayment())
                .isTrue();
        assertThat(beneficiary.getCreatedAt())
                .isEqualTo(CREATED_AT);
        assertThat(beneficiary.getUpdatedAt())
                .isEqualTo(CREATED_AT);
    }

    @Test
    void createsCanonicalUpiBeneficiary() {
        User owner = persistedOwner();

        Beneficiary beneficiary =
                Beneficiary.createUpiBeneficiary(
                        owner,
                        "Demo Merchant",
                        null,
                        " Merchant.Pay@ExampleBank ",
                        "Merchant",
                        null,
                        CREATED_AT);

        assertThat(beneficiary.getPaymentMethod())
                .isEqualTo(BeneficiaryPaymentMethod.UPI);
        assertThat(beneficiary.getUpiId())
                .isEqualTo("merchant.pay@examplebank");
        assertThat(beneficiary.getBankName()).isNull();
        assertThat(beneficiary.getBankAccountNumber()).isNull();
        assertThat(beneficiary.getIfscCode()).isNull();
        assertThat(beneficiary.getStatus())
                .isEqualTo(BeneficiaryStatus.ACTIVE);
    }

    @Test
    void rejectsUnpersistedOwner() {
        User owner = User.createActiveUser(
                "Unpersisted Customer",
                "unpersisted@example.com",
                null,
                "stored-password-hash",
                CREATED_AT);

        assertThat(owner.getUserId()).isNull();

        assertThatThrownBy(
                () -> Beneficiary.createUpiBeneficiary(
                        owner,
                        "Demo Merchant",
                        null,
                        "merchant@examplebank",
                        null,
                        null,
                        CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("owner must already be persisted");
    }

    @Test
    void rejectsShortBeneficiaryName() {
        assertThatThrownBy(
                () -> Beneficiary.createUpiBeneficiary(
                        persistedOwner(),
                        " A ",
                        null,
                        "merchant@examplebank",
                        null,
                        null,
                        CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "beneficiaryName has an invalid length");
    }

    @Test
    void rejectsInvalidIfsc() {
        assertThatThrownBy(
                () -> Beneficiary.createBankAccountBeneficiary(
                        persistedOwner(),
                        "Demo Supplier",
                        null,
                        "SafePay Demo Bank",
                        "123456789012",
                        "INVALID",
                        null,
                        null,
                        CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ifscCode has an invalid format");
    }

    @Test
    void rejectsOversizedUpiIdentifier() {
        assertThatThrownBy(
                () -> Beneficiary.createUpiBeneficiary(
                        persistedOwner(),
                        "Demo Merchant",
                        null,
                        "a".repeat(256),
                        null,
                        null,
                        CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("upiId has an invalid length");
    }

    @Test
    void normalizesBlankOptionalTextToNull() {
        Beneficiary beneficiary =
                Beneficiary.createUpiBeneficiary(
                        persistedOwner(),
                        "Demo Merchant",
                        "   ",
                        "merchant@examplebank",
                        "   ",
                        "   ",
                        CREATED_AT);

        assertThat(beneficiary.getNickname()).isNull();
        assertThat(beneficiary.getRelationshipLabel()).isNull();
        assertThat(beneficiary.getPurposeNote()).isNull();
    }

    @Test
    void disablesAndReEnablesBeneficiary() {
        Beneficiary beneficiary =
                Beneficiary.createUpiBeneficiary(
                        persistedOwner(),
                        "Demo Merchant",
                        null,
                        "merchant@examplebank",
                        null,
                        null,
                        CREATED_AT);

        OffsetDateTime disabledAt =
                CREATED_AT.plusMinutes(1);

        beneficiary.disable(disabledAt);

        assertThat(beneficiary.getStatus())
                .isEqualTo(BeneficiaryStatus.DISABLED);
        assertThat(beneficiary.canReceiveNewPayment())
                .isFalse();
        assertThat(beneficiary.getUpdatedAt())
                .isEqualTo(disabledAt);

        OffsetDateTime enabledAt =
                disabledAt.plusMinutes(1);

        beneficiary.enable(enabledAt);

        assertThat(beneficiary.getStatus())
                .isEqualTo(BeneficiaryStatus.ACTIVE);
        assertThat(beneficiary.canReceiveNewPayment())
                .isTrue();
        assertThat(beneficiary.getUpdatedAt())
                .isEqualTo(enabledAt);
    }

    private User persistedOwner() {
        User owner = mock(User.class);
        when(owner.getUserId()).thenReturn(101L);
        return owner;
    }
}