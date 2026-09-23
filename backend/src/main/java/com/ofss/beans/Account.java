package com.ofss.beans;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import com.ofss.excp.BusinessRuleException;

@Entity
@Table(
        name = "ACCOUNT",
        schema = "SAFEPAY_OWNER")
public class Account {

    private static final int MONEY_PRECISION = 18;
    private static final int MONEY_SCALE = 2;

    private static final Pattern IFSC_PATTERN =
            Pattern.compile("^[A-Z]{4}0[A-Z0-9]{6}$");

    @Id
    @Column(
            name = "ACCOUNT_ID",
            nullable = false,
            updatable = false)
    private Long accountId;

    /*
     * Spring Security integration seam:
     * account services will compare this owner with the user ID
     * obtained from the authenticated principal. A public request must never
     * be allowed to choose ownerUserId.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "OWNER_USER_ID",
            updatable = false)
    private User owner;

    @Column(
            name = "ACCOUNT_NUMBER",
            nullable = false,
            updatable = false,
            length = 34)
    private String accountNumber;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "ACCOUNT_TYPE",
            nullable = false,
            updatable = false,
            length = 30)
    private AccountType accountType;

    @Column(
            name = "BANK_NAME",
            nullable = false,
            updatable = false,
            length = 120)
    private String bankName;

    @Column(
            name = "IFSC_CODE",
            updatable = false,
            length = 11)
    private String ifscCode;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "CURRENCY_CODE",
            nullable = false,
            updatable = false,
            length = 3)
    private CurrencyCode currencyCode;

    @Column(
            name = "CURRENT_BALANCE",
            nullable = false,
            precision = MONEY_PRECISION,
            scale = MONEY_SCALE)
    private BigDecimal currentBalance;

    @Column(
            name = "RESERVED_AMOUNT",
            nullable = false,
            precision = MONEY_PRECISION,
            scale = MONEY_SCALE)
    private BigDecimal reservedAmount;

    /*
     * Oracle calculates this virtual column from:
     * CURRENT_BALANCE - RESERVED_AMOUNT.
     * Application code must never insert or update it.
     */
    @Column(
            name = "AVAILABLE_BALANCE",
            insertable = false,
            updatable = false,
            precision = MONEY_PRECISION,
            scale = MONEY_SCALE)
    private BigDecimal availableBalance;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "STATUS",
            nullable = false,
            length = 20)
    private AccountStatus status;

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

    protected Account() {
        // Required by JPA.
    }

    public static Account createCustomerAccount(
            User owner,
            String accountNumber,
            AccountType accountType,
            String bankName,
            String ifscCode,
            BigDecimal openingBalance,
            OffsetDateTime createdAt) {

        Objects.requireNonNull(owner, "owner is required");
        Objects.requireNonNull(
                accountType,
                "accountType is required");

        if (!accountType.isCustomerOwnedType()) {
            throw new IllegalArgumentException(
                    "Customer account must use SAVINGS or CURRENT");
        }

        if (owner.getUserId() == null) {
            throw new IllegalArgumentException(
                    "owner must already be persisted");
        }

        Account account = new Account();

        account.owner = owner;
        account.accountNumber = requireText(
                accountNumber,
                "accountNumber",
                34);
        account.accountType = accountType;
        account.bankName = requireText(
                bankName,
                "bankName",
                120);
        account.ifscCode = requireIfsc(ifscCode);
        account.currencyCode = CurrencyCode.INR;
        account.currentBalance = requireMoney(
                openingBalance,
                "openingBalance");
        account.reservedAmount =
                BigDecimal.ZERO.setScale(MONEY_SCALE);
        account.status = AccountStatus.ACTIVE;

        OffsetDateTime timestamp = requireUtcTimestamp(
                createdAt,
                "createdAt");

        account.createdAt = timestamp;
        account.updatedAt = timestamp;

        account.validateBalanceInvariant();

        return account;
    }

    public static Account createSystemAccount(
            String accountNumber,
            AccountType accountType,
            String bankName,
            BigDecimal openingBalance,
            OffsetDateTime createdAt) {

        Objects.requireNonNull(
                accountType,
                "accountType is required");

        if (!accountType.isSystemType()) {
            throw new IllegalArgumentException(
                    "System account must use a system account type");
        }

        Account account = new Account();

        account.owner = null;
        account.accountNumber = requireText(
                accountNumber,
                "accountNumber",
                34);
        account.accountType = accountType;
        account.bankName = requireText(
                bankName,
                "bankName",
                120);
        account.ifscCode = null;
        account.currencyCode = CurrencyCode.INR;
        account.currentBalance = requireMoney(
                openingBalance,
                "openingBalance");
        account.reservedAmount =
                BigDecimal.ZERO.setScale(MONEY_SCALE);
        account.status = AccountStatus.ACTIVE;

        OffsetDateTime timestamp = requireUtcTimestamp(
                createdAt,
                "createdAt");

        account.createdAt = timestamp;
        account.updatedAt = timestamp;

        account.validateBalanceInvariant();

        return account;
    }

/*
 * These methods modify only an already-managed Account.
 * Financial services must first load the required account rows
 * with an appropriate database lock inside one transaction.
 */

public void reserveFunds(
        BigDecimal amount,
        OffsetDateTime reservedAt) {

    requireActiveCustomerAccount();

    BigDecimal normalizedAmount =
            requirePositiveMoney(amount, "amount");

    OffsetDateTime timestamp = requireUtcTimestamp(
            reservedAt,
            "reservedAt");

    if (getAvailableBalance()
            .compareTo(normalizedAmount) < 0) {

        throw new BusinessRuleException(
                "INSUFFICIENT_AVAILABLE_BALANCE",
                "Insufficient available balance");
    }

    reservedAmount = requireMoney(
            reservedAmount.add(normalizedAmount),
            "reservedAmount");

    updatedAt = timestamp;

    validateBalanceInvariant();
}

public void releaseReservedFunds(
        BigDecimal amount,
        OffsetDateTime releasedAt) {

    BigDecimal normalizedAmount =
            requirePositiveMoney(amount, "amount");

    OffsetDateTime timestamp = requireUtcTimestamp(
            releasedAt,
            "releasedAt");

    if (reservedAmount.compareTo(normalizedAmount) < 0) {
        throw new BusinessRuleException(
                "RESERVATION_NOT_AVAILABLE",
                "Requested reservation is not available");
    }

    reservedAmount = reservedAmount
            .subtract(normalizedAmount)
            .setScale(MONEY_SCALE);

    updatedAt = timestamp;

    validateBalanceInvariant();
}

public void consumeReservedFunds(
        BigDecimal amount,
        OffsetDateTime settledAt) {

    requireActiveCustomerAccount();

    BigDecimal normalizedAmount =
            requirePositiveMoney(amount, "amount");

    OffsetDateTime timestamp = requireUtcTimestamp(
            settledAt,
            "settledAt");

    if (reservedAmount.compareTo(normalizedAmount) < 0) {
        throw new BusinessRuleException(
                "RESERVATION_NOT_AVAILABLE",
                "Requested reservation is not available");
    }

    if (currentBalance.compareTo(normalizedAmount) < 0) {
        throw new IllegalStateException(
                "Current balance cannot satisfy reserved debit");
    }

    currentBalance = currentBalance
            .subtract(normalizedAmount)
            .setScale(MONEY_SCALE);

    reservedAmount = reservedAmount
            .subtract(normalizedAmount)
            .setScale(MONEY_SCALE);

    updatedAt = timestamp;

    validateBalanceInvariant();
}

public void creditSettlementFunds(
        BigDecimal amount,
        OffsetDateTime settledAt) {

    requireActiveSystemAccount();

    BigDecimal normalizedAmount =
            requirePositiveMoney(amount, "amount");

    OffsetDateTime timestamp = requireUtcTimestamp(
            settledAt,
            "settledAt");

    currentBalance = requireMoney(
            currentBalance.add(normalizedAmount),
            "currentBalance");

    updatedAt = timestamp;

    validateBalanceInvariant();
}

    /** Credits an active SafePay customer account when an inbound payment settles. */
    public void creditIncomingSettlementFunds(
            BigDecimal amount,
            OffsetDateTime settledAt) {

        requireActiveCustomerAccount();
        BigDecimal normalizedAmount = requirePositiveMoney(amount, "amount");
        OffsetDateTime timestamp = requireUtcTimestamp(settledAt, "settledAt");
        currentBalance = requireMoney(currentBalance.add(normalizedAmount), "currentBalance");
        updatedAt = timestamp;
        validateBalanceInvariant();
    }

private void requireActiveCustomerAccount() {
    if (!isActive()) {
        throw new BusinessRuleException(
                "ACCOUNT_INACTIVE",
                "Account is inactive");
    }

    if (!isCustomerOwnedAccount()) {
        throw new BusinessRuleException(
                "CUSTOMER_ACCOUNT_REQUIRED",
                "A customer-owned account is required");
    }
}

private void requireActiveSystemAccount() {
    if (!isActive()) {
        throw new BusinessRuleException(
                "ACCOUNT_INACTIVE",
                "Account is inactive");
    }

    if (!isSystemAccount()) {
        throw new BusinessRuleException(
                "SYSTEM_ACCOUNT_REQUIRED",
                "A system account is required");
    }
}

private static BigDecimal requirePositiveMoney(
        BigDecimal value,
        String fieldName) {

    BigDecimal normalizedValue =
            requireMoney(value, fieldName);

    if (normalizedValue.signum() <= 0) {
        throw new IllegalArgumentException(
                fieldName + " must be positive");
    }

    return normalizedValue;
}

    public boolean isCustomerOwnedAccount() {
        return accountType.isCustomerOwnedType();
    }

    public boolean isSystemAccount() {
        return accountType.isSystemType();
    }

    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }

    private void validateBalanceInvariant() {
        if (currentBalance.signum() < 0) {
            throw new IllegalStateException(
                    "currentBalance cannot be negative");
        }

        if (reservedAmount.signum() < 0) {
            throw new IllegalStateException(
                    "reservedAmount cannot be negative");
        }

        if (reservedAmount.compareTo(currentBalance) > 0) {
            throw new IllegalStateException(
                    "reservedAmount cannot exceed currentBalance");
        }
    }

    private static String requireText(
            String value,
            String fieldName,
            int maximumLength) {

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + " is required");
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

    private static BigDecimal requireMoney(
            BigDecimal value,
            String fieldName) {

        Objects.requireNonNull(
                value,
                fieldName + " is required");

        BigDecimal normalizedValue;

        try {
            normalizedValue = value.setScale(
                    MONEY_SCALE,
                    RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(
                    fieldName
                            + " must not contain more than two decimal places",
                    exception);
        }

        if (normalizedValue.precision() > MONEY_PRECISION) {
            throw new IllegalArgumentException(
                    fieldName + " exceeds NUMBER(18,2)");
        }

        if (normalizedValue.signum() < 0) {
            throw new IllegalArgumentException(
                    fieldName + " cannot be negative");
        }

        return normalizedValue;
    }

    private static OffsetDateTime requireUtcTimestamp(
            OffsetDateTime value,
            String fieldName) {

        return Objects.requireNonNull(
                value,
                fieldName + " is required")
                .withOffsetSameInstant(ZoneOffset.UTC);
    }

    public Long getAccountId() {
        return accountId;
    }

    public User getOwner() {
        return owner;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public AccountType getAccountType() {
        return accountType;
    }

    public String getBankName() {
        return bankName;
    }

    public String getIfscCode() {
        return ifscCode;
    }

    public CurrencyCode getCurrencyCode() {
        return currencyCode;
    }

    public BigDecimal getCurrentBalance() {
        return currentBalance;
    }

    public BigDecimal getReservedAmount() {
        return reservedAmount;
    }

    public BigDecimal getAvailableBalance() {
        if (currentBalance == null || reservedAmount == null) {
            return availableBalance;
        }

        return currentBalance
                .subtract(reservedAmount)
                .setScale(MONEY_SCALE);
    }

    public AccountStatus getStatus() {
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
