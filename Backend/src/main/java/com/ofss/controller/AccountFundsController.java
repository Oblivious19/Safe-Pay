package com.ofss.controller;
import java.math.BigDecimal;
import java.util.List;
import com.ofss.beans.LoginPrincipal;
import com.ofss.beans.TransactionState;
import com.ofss.repository.AccountFundsRepository;
import com.ofss.excp.ResourceNotFoundExcp;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/accounts")
public class AccountFundsController {
    private final AccountFundsRepository accounts;
    public AccountFundsController(AccountFundsRepository accounts) { this.accounts = accounts; }
    public record FundsView(Long accountId, BigDecimal balance, BigDecimal reservedBalance,
            BigDecimal minimumBalance, BigDecimal availableToTransfer) {}
    @GetMapping("/{id}/funds")
    @Transactional(readOnly = true)
    public FundsView funds(@PathVariable Long id, @AuthenticationPrincipal LoginPrincipal caller) {
        if (id == null || id <= 0) throw new IllegalArgumentException("Account ID must be positive");
        var funds = accounts.funds(id, caller.userId(), List.of(TransactionState.PROTECTED, TransactionState.HARD_HOLD))
                .orElseThrow(() -> new ResourceNotFoundExcp("Account not found"));
        return new FundsView(funds.getAccountId(), funds.getBalance(), funds.getReservedBalance(), BigDecimal.ZERO,
                funds.getBalance().subtract(funds.getReservedBalance()).max(BigDecimal.ZERO));
    }
}
