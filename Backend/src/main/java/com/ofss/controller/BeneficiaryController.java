package com.ofss.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ofss.beans.AddBeneficiaryRequest;
import com.ofss.beans.Beneficiary;
import com.ofss.beans.UpdateBeneficiaryStatusRequest;
import com.ofss.services.BeneficiaryService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/beneficiaries")
public class BeneficiaryController {

    private final BeneficiaryService beneficiaryService;

    public BeneficiaryController(BeneficiaryService beneficiaryService) {
        this.beneficiaryService = beneficiaryService;
    }

    @PostMapping
    public Beneficiary addBeneficiary(@Valid @RequestBody AddBeneficiaryRequest request) {
        Beneficiary beneficiary = new Beneficiary();
        beneficiary.setBeneficiaryName(request.beneficiaryName());
        beneficiary.setBankAccountNumber(request.bankAccountNumber());
        beneficiary.setIfsc(request.ifsc());
        return beneficiaryService.addBeneficiary(request.accountId(), beneficiary, request.userEmail());
    }

    @GetMapping
    public List<Beneficiary> getBeneficiaries(@RequestParam String userEmail) {
        return beneficiaryService.getBeneficiaries(userEmail);
    }

    @PatchMapping("/{beneficiaryId}/status")
    public Beneficiary updateStatus(@PathVariable Long beneficiaryId,
            @Valid @RequestBody UpdateBeneficiaryStatusRequest request) {
        return beneficiaryService.updateStatus(beneficiaryId, request.status(), request.userEmail());
    }
}
