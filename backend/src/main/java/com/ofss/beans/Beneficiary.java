package com.ofss.beans;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(
        name = "BENEFICIARY",
        schema = "SAFEPAY_OWNER")
@SequenceGenerator(
        name = "beneficiarySequence",
        sequenceName = "SAFEPAY_OWNER.SEQ_BENEFICIARY_ID",
        allocationSize = 1)
public class Beneficiary {

    private static final Pattern IFSC_PATTERN =
            Pattern.compile("^[A-Z]{4}0[A-Z0-9]{6}$");

    @Id
    @GeneratedValue(
            strategy = GenerationType.SEQUENCE,
            generator = "beneficiarySequence")
    @Column(
            name = "BENEFICIARY_ID",
            nullable = false,
            updatable = false)
    private Long beneficiaryId;

    /*
     * Spring Security integration seam:
     * the owner comes only from the authenticated principal.
     * A public request must never be allowed to choose ownerUserId.
     */
    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false)
    @JoinColumn(
            name = "OWNER_USER_ID",
            nullable = false,
            updatable = false)
    private User owner;

    /*
     * V1 exposes only a status-change endpoint.
     * Destination identity is therefore immutable after creation.
     */
    @Column(
            name = "BENEFICIARY_NAME",
            nullable = false,
            updatable = false,
            length = 120)
    private String beneficiaryName;

    @Column(
            name = "NICKNAME",
            updatable = false,
            length = 60)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "PAYMENT_METHOD",
            nullable = false,
            updatable = false,
            length = 20)
    private BeneficiaryPaymentMethod paymentMethod;

    @Column(
            name = "BANK_NAME",
            updatable = false,
            length = 120)
    private String bankName;

    @Column(
            name = "BANK_ACCOUNT_NUMBER",
            updatable = false,
            length = 34)
    private String bankAccountNumber;

    @Column(
            name = "IFSC_CODE",
            updatable = false,
            length = 11)
    private String ifscCode;

    /*
     * Present only for a BANK_ACCOUNT beneficiary whose destination was
     * verified against an internal SafePay customer account.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "DESTINATION_ACCOUNT_ID",
            updatable = false)
    private Account destinationAccount;

    @Column(
            name = "UPI_ID",
            updatable = false,
            length = 255)
    private String upiId;

    @Column(
            name = "RELATIONSHIP_LABEL",
            updatable = false,
            length = 50)
    private String relationshipLabel;

    @Column(
            name = "PURPOSE_NOTE",
            updatable = false,
            length = 140)
    private String purposeNote;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "STATUS",
            nullable = false,
            length = 20)
    private BeneficiaryStatus status;

    @Version
    @Column(
            name = "VERSION_NO",
            nullable = false,
            precision = 19,
            scale = 0)
    private long versionNo;

    @Column(
            name = "CREATED_AT",
            nullable = false,
            updatable = false)
    private OffsetDateTime createdAt;

    @Column(
            name = "UPDATED_AT",
            nullable = false)
    private OffsetDateTime updatedAt;

    protected Beneficiary() {
        // Required by JPA.
    }

    public static Beneficiary createBankAccountBeneficiary(
            User owner,
            String beneficiaryName,
            String nickname,
            String bankName,
            String bankAccountNumber,
            String ifscCode,
            String relationshipLabel,
            String purposeNote,
            OffsetDateTime createdAt) {

        Beneficiary beneficiary = createBase(
                owner,
                beneficiaryName,
                nickname,
                BeneficiaryPaymentMethod.BANK_ACCOUNT,
                relationshipLabel,
                purposeNote,
                createdAt);

        beneficiary.bankName = requireText(
                bankName,
                "bankName",
                1,
                120);

        beneficiary.bankAccountNumber = requireText(
                bankAccountNumber,
                "bankAccountNumber",
                1,
                34);

        beneficiary.ifscCode = requireIfsc(ifscCode);
        beneficiary.upiId = null;

        return beneficiary;
    }

    public static Beneficiary createVerifiedBankAccountBeneficiary(
            User owner,
            String beneficiaryName,
            String nickname,
            String bankName,
            String bankAccountNumber,
            String ifscCode,
            String relationshipLabel,
            String purposeNote,
            Account destinationAccount,
            OffsetDateTime createdAt) {

        Beneficiary beneficiary = createBankAccountBeneficiary(
                owner,
                beneficiaryName,
                nickname,
                bankName,
                bankAccountNumber,
                ifscCode,
                relationshipLabel,
                purposeNote,
                createdAt);

        beneficiary.destinationAccount = requirePersistedAccount(
                destinationAccount,
                "destinationAccount");

        return beneficiary;
    }

    public static Beneficiary createUpiBeneficiary(
            User owner,
            String beneficiaryName,
            String nickname,
            String upiId,
            String relationshipLabel,
            String purposeNote,
            OffsetDateTime createdAt) {

        Beneficiary beneficiary = createBase(
                owner,
                beneficiaryName,
                nickname,
                BeneficiaryPaymentMethod.UPI,
                relationshipLabel,
                purposeNote,
                createdAt);

        beneficiary.bankName = null;
        beneficiary.bankAccountNumber = null;
        beneficiary.ifscCode = null;
        beneficiary.destinationAccount = null;
        beneficiary.upiId = requireUpiId(upiId);

        return beneficiary;
    }

    private static Beneficiary createBase(
            User owner,
            String beneficiaryName,
            String nickname,
            BeneficiaryPaymentMethod paymentMethod,
            String relationshipLabel,
            String purposeNote,
            OffsetDateTime createdAt) {

        requirePersistedOwner(owner);

        Beneficiary beneficiary = new Beneficiary();

        beneficiary.owner = owner;

        beneficiary.beneficiaryName = requireText(
                beneficiaryName,
                "beneficiaryName",
                2,
                120);

        beneficiary.nickname = normalizeOptionalText(
                nickname,
                "nickname",
                60);

        beneficiary.paymentMethod = Objects.requireNonNull(
                paymentMethod,
                "paymentMethod is required");

        beneficiary.relationshipLabel = normalizeOptionalText(
                relationshipLabel,
                "relationshipLabel",
                50);

        beneficiary.purposeNote = normalizeOptionalText(
                purposeNote,
                "purposeNote",
                140);

        beneficiary.status = BeneficiaryStatus.ACTIVE;

        OffsetDateTime timestamp = requireUtcTimestamp(
                createdAt,
                "createdAt");

        beneficiary.createdAt = timestamp;
        beneficiary.updatedAt = timestamp;

        return beneficiary;
    }

    public void disable(OffsetDateTime changedAt) {
        changeStatus(
                BeneficiaryStatus.DISABLED,
                changedAt);
    }

    public void enable(OffsetDateTime changedAt) {
        changeStatus(
                BeneficiaryStatus.ACTIVE,
                changedAt);
    }

    private void changeStatus(
            BeneficiaryStatus newStatus,
            OffsetDateTime changedAt) {

        Objects.requireNonNull(
                newStatus,
                "newStatus is required");

        OffsetDateTime timestamp = requireUtcTimestamp(
                changedAt,
                "changedAt");

        if (status == newStatus) {
            return;
        }

        status = newStatus;
        updatedAt = timestamp;
    }

    public boolean canReceiveNewPayment() {
        return status == BeneficiaryStatus.ACTIVE;
    }

    private static void requirePersistedOwner(User owner) {
        Objects.requireNonNull(owner, "owner is required");

        if (owner.getUserId() == null) {
            throw new IllegalArgumentException(
                    "owner must already be persisted");
        }
    }

    private static Account requirePersistedAccount(
            Account account,
            String fieldName) {

        Objects.requireNonNull(account, fieldName + " is required");

        if (account.getAccountId() == null
                || account.getAccountId() <= 0L) {
            throw new IllegalArgumentException(
                    fieldName + " must already be persisted");
        }

        return account;
    }

    private static String requireText(
            String value,
            String fieldName,
            int minimumLength,
            int maximumLength) {

        if (value == null) {
            throw new IllegalArgumentException(
                    fieldName + " is required");
        }

        String normalizedValue = value.trim();

        if (normalizedValue.length() < minimumLength
                || normalizedValue.length() > maximumLength) {

            throw new IllegalArgumentException(
                    fieldName + " has an invalid length");
        }

        return normalizedValue;
    }

    private static String normalizeOptionalText(
            String value,
            String fieldName,
            int maximumLength) {

        if (value == null || value.isBlank()) {
            return null;
        }

        String normalizedValue = value.trim();

        if (normalizedValue.length() > maximumLength) {
            throw new IllegalArgumentException(
                    fieldName + " has an invalid length");
        }

        return normalizedValue;
    }

    private static String requireIfsc(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "ifscCode is required");
        }

        String normalizedValue =
                value.trim().toUpperCase(Locale.ROOT);

        if (!IFSC_PATTERN.matcher(normalizedValue).matches()) {
            throw new IllegalArgumentException(
                    "ifscCode has an invalid format");
        }

        return normalizedValue;
    }

    private static String requireUpiId(String value) {
        String normalizedValue = requireText(
                value,
                "upiId",
                1,
                255).toLowerCase(Locale.ROOT);

        return normalizedValue;
    }

    private static OffsetDateTime requireUtcTimestamp(
            OffsetDateTime value,
            String fieldName) {

        return Objects.requireNonNull(
                value,
                fieldName + " is required")
                .withOffsetSameInstant(ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.MICROS);
    }

    public Long getBeneficiaryId() {
        return beneficiaryId;
    }

    public User getOwner() {
        return owner;
    }

    public String getBeneficiaryName() {
        return beneficiaryName;
    }

    public String getNickname() {
        return nickname;
    }

    public BeneficiaryPaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public String getBankName() {
        return bankName;
    }

    public String getBankAccountNumber() {
        return bankAccountNumber;
    }

    public String getIfscCode() {
        return ifscCode;
    }

    public Account getDestinationAccount() {
        return destinationAccount;
    }

    public String getUpiId() {
        return upiId;
    }

    public String getRelationshipLabel() {
        return relationshipLabel;
    }

    public String getPurposeNote() {
        return purposeNote;
    }

    public BeneficiaryStatus getStatus() {
        return status;
    }

    public long getVersionNo() {
        return versionNo;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
