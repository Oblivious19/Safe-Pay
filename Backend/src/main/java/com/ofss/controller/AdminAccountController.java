package com.ofss.controller;

import java.util.List;
import com.ofss.beans.AdminAccountDtos.*;
import com.ofss.services.AdminAccountService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/accounts")
public class AdminAccountController {
    private final AdminAccountService accounts;
    public AdminAccountController(AdminAccountService accounts) { this.accounts = accounts; }

    @GetMapping
    public List<Response> list() { return accounts.list(); }

    @PutMapping(value = "/{id}", consumes = "application/json")
    public Response update(@PathVariable Long id, @Valid @RequestBody Update input) {
        return accounts.update(id, input);
    }
}
