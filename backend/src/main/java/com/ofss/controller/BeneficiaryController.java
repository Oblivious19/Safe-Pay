package com.ofss.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ofss.dto.beneficiary.BeneficiaryResponse;
import com.ofss.dto.beneficiary.CreateBeneficiaryRequest;
import com.ofss.dto.beneficiary.UpdateBeneficiaryStatusRequest;
import com.ofss.security.AuthenticatedUser;
import com.ofss.services.BeneficiaryService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/beneficiaries")
@PreAuthorize("hasAuthority('CUSTOMER')")
public class BeneficiaryController {

    private final BeneficiaryService beneficiaryService;

    public BeneficiaryController(
            BeneficiaryService beneficiaryService) {

        this.beneficiaryService = beneficiaryService;
    }

    @PostMapping
    public ResponseEntity<BeneficiaryResponse> create(
            Authentication authentication,
            @Valid @RequestBody CreateBeneficiaryRequest request) {

        BeneficiaryResponse response = beneficiaryService
                .createOwnedBeneficiary(
                        authenticatedUserId(authentication),
                        request);

        URI location = URI.create(
                "/api/v1/beneficiaries/"
                        + response.beneficiaryId());

        return ResponseEntity
                .created(location)
                .body(response);
    }

    @GetMapping
    public ResponseEntity<List<BeneficiaryResponse>> list(
            Authentication authentication) {

        return ResponseEntity.ok(
                beneficiaryService.listOwnedBeneficiaries(
                        authenticatedUserId(authentication)));
    }

    @GetMapping("/{beneficiaryId}")
    public ResponseEntity<BeneficiaryResponse> get(
            Authentication authentication,
            @PathVariable("beneficiaryId")
                    Long beneficiaryId) {

        return ResponseEntity.ok(
                beneficiaryService.getOwnedBeneficiary(
                        authenticatedUserId(authentication),
                        beneficiaryId));
    }

    @PatchMapping("/{beneficiaryId}/status")
    public ResponseEntity<BeneficiaryResponse> updateStatus(
            Authentication authentication,
            @PathVariable("beneficiaryId")
                    Long beneficiaryId,
            @Valid @RequestBody
                    UpdateBeneficiaryStatusRequest request) {

        return ResponseEntity.ok(
                beneficiaryService.updateOwnedBeneficiaryStatus(
                        authenticatedUserId(authentication),
                        beneficiaryId,
                        request));
    }

    private Long authenticatedUserId(
            Authentication authentication) {

        return AuthenticatedUser.userId(authentication);
    }
}
