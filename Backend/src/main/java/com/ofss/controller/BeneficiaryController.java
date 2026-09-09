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

import com.ofss.beans.Beneficiary;
import com.ofss.services.BeneficiaryService;

@RestController
@RequestMapping("/api/beneficiaries")
public class BeneficiaryController {

    private final BeneficiaryService beneficiaryService;

    public BeneficiaryController(BeneficiaryService beneficiaryService) {
        this.beneficiaryService = beneficiaryService;
    }

    @PostMapping
    public Beneficiary addBeneficiary(@RequestParam Long accountId, @RequestParam String userEmail,
            @RequestBody Beneficiary beneficiary) {
        return beneficiaryService.addBeneficiary(accountId, beneficiary, userEmail);
    }

    @GetMapping
    public List<Beneficiary> getBeneficiaries(@RequestParam String userEmail) {
        return beneficiaryService.getBeneficiaries(userEmail);
    }

    @PatchMapping("/{beneficiaryId}/status")
    public Beneficiary updateStatus(@PathVariable Long beneficiaryId, @RequestParam String status,
            @RequestParam String userEmail) {
        return beneficiaryService.updateStatus(beneficiaryId, status, userEmail);
    }
}
