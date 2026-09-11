package com.ofss.beans;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "beneficiaries", uniqueConstraints = @UniqueConstraint(name = "sp_uq_benef_acct",
        columnNames = {"account_id", "bank_account_number", "ifsc"}))
public class Beneficiary {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "beneficiarySequence")
    @SequenceGenerator(name = "beneficiarySequence", sequenceName = "seq_beneficiary_id", allocationSize = 1)
    @Column(name = "beneficiary_id")
    private Long beneficiaryId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    @JsonIgnore
    private Account account;

    @Column(name = "beneficiary_name", nullable = false, length = 100)
    private String beneficiaryName;

    @Column(name = "bank_account_number", nullable = false, length = 30)
    private String bankAccountNumber;
    @Column(nullable = false, length = 20)
    private String ifsc;

    @Column(nullable = false, length = 10)
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public Long getBeneficiaryId() { return beneficiaryId; }
    public void setBeneficiaryId(Long beneficiaryId) { this.beneficiaryId = beneficiaryId; }
    public Account getAccount() { return account; }
    public void setAccount(Account account) { this.account = account; }
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public Long getAccountId() { return account == null ? null : account.getAccountId(); }
    public String getBeneficiaryName() { return beneficiaryName; }
    public void setBeneficiaryName(String beneficiaryName) { this.beneficiaryName = beneficiaryName; }
    public String getBankAccountNumber() { return bankAccountNumber; }
    public void setBankAccountNumber(String bankAccountNumber) { this.bankAccountNumber = bankAccountNumber; }
    public String getIfsc() { return ifsc; }
    public void setIfsc(String ifsc) { this.ifsc = ifsc; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
