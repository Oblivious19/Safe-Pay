package com.ofss.services;

import java.util.List;

import com.ofss.beans.Beneficiary;

public interface BeneficiaryService {
    Beneficiary addBeneficiary(Long accountId, Beneficiary beneficiary, String email);
    List<Beneficiary> getBeneficiaries(String email);
    Beneficiary updateStatus(Long beneficiaryId, String status, String email);
}
