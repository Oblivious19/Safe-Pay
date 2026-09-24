package com.ofss.controller;

import java.util.List;
import java.util.Map;
import java.sql.SQLException;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;

import com.ofss.beans.BeneficiaryRequest;
import com.ofss.beans.BeneficiaryResponse;
import com.ofss.beans.BeneficiaryStatusRequest;
import com.ofss.beans.LoginPrincipal;
import com.ofss.excp.DuplicateBeneficiaryException;
import com.ofss.services.BeneficiaryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

@RestController
@RequestMapping("/api/beneficiaries")
public class BeneficiaryController {

    private final BeneficiaryService beneficiaryService;

    public BeneficiaryController(BeneficiaryService beneficiaryService) {
        this.beneficiaryService = beneficiaryService;
    }

    @PostMapping(consumes = "application/json")
    @ResponseStatus(HttpStatus.CREATED)
    public BeneficiaryResponse addBeneficiary(@AuthenticationPrincipal LoginPrincipal caller,
            @Valid @RequestBody BeneficiaryRequest request) {
        return beneficiaryService.addBeneficiary(caller.userId(), request);
    }

    @GetMapping
    public List<BeneficiaryResponse> getBeneficiaries(@AuthenticationPrincipal LoginPrincipal caller,
            @RequestParam(defaultValue = "false") boolean includeInactive,
            @RequestParam(required = false) Long accountId) {
        if (accountId != null) return beneficiaryService.getBeneficiaries(caller.userId(), accountId, includeInactive);
        return includeInactive ? beneficiaryService.getBeneficiaries(caller.userId(), true)
                : beneficiaryService.getBeneficiaries(caller.userId());
    }

    @GetMapping("/{beneficiaryId}")
    public BeneficiaryResponse getBeneficiary(@PathVariable Long beneficiaryId,
            @AuthenticationPrincipal LoginPrincipal caller) {
        return beneficiaryService.getBeneficiary(caller.userId(), beneficiaryId);
    }

    @DeleteMapping("/{beneficiaryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBeneficiary(@PathVariable Long beneficiaryId, @AuthenticationPrincipal LoginPrincipal caller) {
        beneficiaryService.deleteBeneficiary(caller.userId(), beneficiaryId);
    }

    @PatchMapping(value = "/{beneficiaryId}/status", consumes = "application/json")
    public BeneficiaryResponse changeStatus(@PathVariable Long beneficiaryId,
            @AuthenticationPrincipal LoginPrincipal caller, @Valid @RequestBody BeneficiaryStatusRequest request) {
        return beneficiaryService.changeStatus(caller.userId(), beneficiaryId, request.status());
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentNotValidException.class})
    ResponseEntity<Map<String, String>> invalidRequest() {
        return ResponseEntity.badRequest().body(Map.of("message",
                "Invalid beneficiary details. Check the name, account number and IFSC code."));
    }

    @ExceptionHandler(DuplicateBeneficiaryException.class)
    ResponseEntity<Map<String, String>> duplicate() {
        return ResponseEntity.status(409).body(Map.of("message", "Beneficiary is already registered for this account"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Map<String, String>> integrityFailure(DataIntegrityViolationException exception) {
        boolean duplicate = exception instanceof DuplicateKeyException;
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && sql.getErrorCode() == 1) duplicate = true;
        }
        return ResponseEntity.status(duplicate ? 409 : 500).body(Map.of("message", duplicate
                ? "A beneficiary record with these details already exists"
                : "Unable to save the beneficiary"));
    }
}
