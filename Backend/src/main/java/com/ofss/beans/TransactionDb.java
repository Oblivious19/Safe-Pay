package com.ofss.beans;

import java.math.BigDecimal;
import java.sql.Types;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;

@Entity
@Table(name = "transaction_db")
public class TransactionDb {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "transactionSequence")
    @SequenceGenerator(name = "transactionSequence", sequenceName = "seq_transaction_id", allocationSize = 1)
    @Column(name = "transaction_id")
    private Long transactionId;

    @Column(name = "transaction_ref", nullable = false, unique = true, length = 50)
    private String transactionRef;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    @JsonIgnore
    @Column(name = "cancel_idempotency_key", unique = true, length = 100)
    private String cancelIdempotencyKey;

    @JsonIgnore
    @Column(name = "verification_idempotency_key", unique = true, length = 100)
    private String verificationIdempotencyKey;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "authorized_at")
    private LocalDateTime authorizedAt;

    @Column(name = "released_at")
    private LocalDateTime releasedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_account_id", nullable = false)
    @JsonIgnore
    private Account fromAccount;

    /** The internal destination, resolved by the server, never supplied by a caller. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_account_id")
    @JsonIgnore
    private Account toAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "beneficiary_id", nullable = false)
    @JsonIgnore
    private Beneficiary beneficiary;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;
    private String purpose;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_category", length = 20, updatable = false)
    private PaymentCategory category;

    @Enumerated(EnumType.STRING)
    private TransactionState state;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_tier")
    private RiskTier riskTier;

    @Column(name = "protection_seconds")
    private int protectionSeconds;

    @Convert(converter = BooleanToYNConverter.class)
    @JdbcTypeCode(Types.CHAR)
    @Column(name = "authentication_required", nullable = false, length = 1)
    private boolean authenticationRequired;

    @Column(name = "risk_reason", length = 2000)
    private String riskReason;

    @Column(name = "protection_expires_at")
    private LocalDateTime protectionExpiresAt;

    @Version
    private Long version;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "settled_at")
    private LocalDateTime settledAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    /** Read-only customer-history metadata; deliberately not stored in the transaction table. */
    @Transient
    private String direction;

    /** The other SafePay customer for an internal payment; never persisted. */
    @Transient
    private String counterpartyName;

    public Long getTransactionId() { return transactionId; }
    public void setTransactionId(Long transactionId) { this.transactionId = transactionId; }
    public String getTransactionRef() { return transactionRef; }
    public void setTransactionRef(String transactionRef) { this.transactionRef = transactionRef; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public String getCancelIdempotencyKey() { return cancelIdempotencyKey; }
    public void setCancelIdempotencyKey(String key) { this.cancelIdempotencyKey = key; }
    public String getVerificationIdempotencyKey() { return verificationIdempotencyKey; }
    public void setVerificationIdempotencyKey(String key) { this.verificationIdempotencyKey = key; }
    public LocalDateTime getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(LocalDateTime time) { this.verifiedAt = time; }
    public LocalDateTime getAuthorizedAt() { return authorizedAt; }
    public void setAuthorizedAt(LocalDateTime time) { this.authorizedAt = time; }
    public LocalDateTime getReleasedAt() { return releasedAt; }
    public void setReleasedAt(LocalDateTime time) { this.releasedAt = time; }
    public Account getFromAccount() { return fromAccount; }
    public void setFromAccount(Account fromAccount) { this.fromAccount = fromAccount; }
    public Account getToAccount() { return toAccount; }
    public void setToAccount(Account toAccount) { this.toAccount = toAccount; }
    public Beneficiary getBeneficiary() { return beneficiary; }
    public void setBeneficiary(Beneficiary beneficiary) { this.beneficiary = beneficiary; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }
    public PaymentCategory getCategory() { return category; }
    public void setCategory(PaymentCategory category) { this.category = category; }
    public TransactionState getState() { return state; }
    public void setState(TransactionState state) { this.state = state; }
    public RiskTier getRiskTier() { return riskTier; }
    public void setRiskTier(RiskTier riskTier) { this.riskTier = riskTier; }
    public int getProtectionSeconds() { return protectionSeconds; }
    public void setProtectionSeconds(int protectionSeconds) { this.protectionSeconds = protectionSeconds; }
    public boolean isAuthenticationRequired() { return authenticationRequired; }
    public void setAuthenticationRequired(boolean authenticationRequired) { this.authenticationRequired = authenticationRequired; }
    public String getRiskReason() { return riskReason; }
    public void setRiskReason(String riskReason) { this.riskReason = riskReason; }
    public LocalDateTime getProtectionExpiresAt() { return protectionExpiresAt; }
    public void setProtectionExpiresAt(LocalDateTime protectionExpiresAt) { this.protectionExpiresAt = protectionExpiresAt; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getSettledAt() { return settledAt; }
    public void setSettledAt(LocalDateTime settledAt) { this.settledAt = settledAt; }
    public LocalDateTime getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(LocalDateTime cancelledAt) { this.cancelledAt = cancelledAt; }
    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }
    public String getCounterpartyName() { return counterpartyName; }
    public void setCounterpartyName(String counterpartyName) { this.counterpartyName = counterpartyName; }
}
