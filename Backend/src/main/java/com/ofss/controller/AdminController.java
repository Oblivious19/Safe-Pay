package com.ofss.controller;

import java.util.List;
import java.util.Map;
import com.ofss.beans.AdminDtos.*;
import com.ofss.beans.LoginPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.ofss.excp.InvalidAdminStatusTransitionException;
import com.ofss.services.AdminService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AdminService service;

    public AdminController(AdminService service) { this.service = service; }

    @GetMapping("/users")
    public List<UserResponse> users() { return service.getUsers(); }

    @GetMapping("/users/search")
    public List<UserResponse> searchUsers(@RequestParam String by, @RequestParam String query) {
        return service.searchUsers(by, query);
    }

    @GetMapping("/users/{id}")
    public UserResponse user(@PathVariable Long id) { return service.getUser(id); }

    @PatchMapping(value = "/users/{id}/status", consumes = "application/json")
    public UserResponse userStatus(@PathVariable Long id, @Valid @RequestBody UserStatusRequest request,
            @AuthenticationPrincipal LoginPrincipal caller) {
        return service.changeUserStatus(id, request.status(), caller.userId());
    }

    @PatchMapping(value = "/accounts/{id}/status", consumes = "application/json")
    public AccountResponse accountStatus(@PathVariable Long id, @Valid @RequestBody AccountStatusRequest request) {
        return service.changeAccountStatus(id, request.status());
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentNotValidException.class})
    ResponseEntity<Map<String, String>> invalidRequest() {
        return ResponseEntity.badRequest().body(Map.of("message", "Send only a valid, non-null status field"));
    }

    @ExceptionHandler(InvalidAdminStatusTransitionException.class)
    ResponseEntity<Map<String, String>> invalidTransition(InvalidAdminStatusTransitionException exception) {
        return ResponseEntity.status(409).body(Map.of("message", exception.getMessage()));
    }
}
