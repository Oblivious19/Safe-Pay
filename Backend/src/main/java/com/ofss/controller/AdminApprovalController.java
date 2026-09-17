package com.ofss.controller;

import java.util.List;
import com.ofss.beans.*;
import com.ofss.services.AdminApprovalService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/transactions")
public class AdminApprovalController {
    private final AdminApprovalService service;
    public AdminApprovalController(AdminApprovalService service) { this.service = service; }

    @GetMapping("/hard-holds")
    public List<AdminApprovalRequestView> pending(@AuthenticationPrincipal LoginPrincipal caller) {
        return service.pending(caller);
    }

    @PostMapping("/{id}/approve")
    public VerifiedTransactionResponse approve(@PathVariable Long id, @AuthenticationPrincipal LoginPrincipal caller,
            @RequestHeader("Idempotency-Key") String key) {
        return service.approve(id, caller, key);
    }

    @PostMapping("/{id}/decline")
    public VerifiedTransactionResponse decline(@PathVariable Long id, @AuthenticationPrincipal LoginPrincipal caller,
            @RequestHeader("Idempotency-Key") String key) {
        return service.decline(id, caller, key);
    }
}
