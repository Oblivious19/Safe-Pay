package com.ofss.controller;

import java.util.Map;
import com.ofss.beans.AdminProvisionRequest;
import com.ofss.beans.AdminDtos.UserResponse;
import com.ofss.beans.LoginPrincipal;
import com.ofss.services.AdminProvisioningService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/users")
public class AdminProvisioningController {
    private final AdminProvisioningService service;
    public AdminProvisioningController(AdminProvisioningService service) { this.service = service; }
    @PostMapping(consumes = "application/json")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody AdminProvisionRequest request,
            @AuthenticationPrincipal LoginPrincipal caller) {
        return service.create(request, caller.userId());
    }
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentNotValidException.class})
    ResponseEntity<Map<String, String>> invalidRequest() {
        return ResponseEntity.badRequest().body(Map.of("message", "Provide a valid name, email, mobile number and initialPassword (8 characters minimum, 72 UTF-8 bytes maximum)"));
    }
    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<Map<String, String>> unavailable() {
        return ResponseEntity.status(503).body(Map.of("message", "Unable to provision administrator"));
    }
}