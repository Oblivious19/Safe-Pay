package com.ofss.services;

import java.util.List;

import com.ofss.beans.BeneficiaryRequest;
import com.ofss.beans.BeneficiaryResponse;

public interface BeneficiaryService {
    BeneficiaryResponse addBeneficiary(Long callerId, BeneficiaryRequest request);
    List<BeneficiaryResponse> getBeneficiaries(Long callerId);
    List<BeneficiaryResponse> getBeneficiaries(Long callerId, boolean includeInactive);
    List<BeneficiaryResponse> getBeneficiaries(Long callerId, Long accountId, boolean includeInactive);
    BeneficiaryResponse getBeneficiary(Long callerId, Long beneficiaryId);
    BeneficiaryResponse changeStatus(Long callerId, Long beneficiaryId, String status);
    void deleteBeneficiary(Long callerId, Long beneficiaryId);
}
