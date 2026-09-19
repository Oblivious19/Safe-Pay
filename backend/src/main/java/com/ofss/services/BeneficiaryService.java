package com.ofss.services;

import java.util.List;

import com.ofss.beans.Beneficiary;
import com.ofss.dto.beneficiary.BeneficiaryResponse;
import com.ofss.dto.beneficiary.CreateBeneficiaryRequest;
import com.ofss.dto.beneficiary.UpdateBeneficiaryStatusRequest;

public interface BeneficiaryService {

    /*
     * Deferred Spring Security integration:
     * ownerUserId is obtained from the authenticated principal.
     * Controllers must never accept it from request JSON.
     */
    BeneficiaryResponse createOwnedBeneficiary(
            Long ownerUserId,
            CreateBeneficiaryRequest request);

    List<BeneficiaryResponse> listOwnedBeneficiaries(
            Long ownerUserId);

    BeneficiaryResponse getOwnedBeneficiary(
            Long ownerUserId,
            Long beneficiaryId);

    BeneficiaryResponse updateOwnedBeneficiaryStatus(
            Long ownerUserId,
            Long beneficiaryId,
            UpdateBeneficiaryStatusRequest request);

    /*
     * Transaction authorization uses the entity-returning seam
     * so ownership and ACTIVE status are revalidated server-side.
     */
    Beneficiary getRequiredActiveOwnedBeneficiary(
            Long ownerUserId,
            Long beneficiaryId);
}
