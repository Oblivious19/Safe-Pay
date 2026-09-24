package com.ofss.services;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import com.ofss.beans.*;
import com.ofss.beans.AdminAccountDtos.*;
import com.ofss.excp.ResourceNotFoundExcp;
import com.ofss.excp.TransactionValidationException;
import com.ofss.repository.AdminAccountRepository;
import com.ofss.repository.TransactionDao;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminAccountService {
    private static final BigDecimal MAXIMUM = new BigDecimal("9999999999999999.99");
    private final AdminAccountRepository accounts;
    private final TransactionDao transactions;
    public AdminAccountService(AdminAccountRepository accounts, TransactionDao transactions) {
        this.accounts = accounts;
        this.transactions = transactions;
    }
    @Transactional(readOnly = true)
    public List<Response> list() {
        return accounts.findAllByOrderByAccountId().stream().map(Response::from).toList();
    }
    @Transactional
    public Response update(Long id, Update input) {
        if (id == null || id <= 0 || input == null || input.accountType() == null || input.balance() == null
                || input.balance().signum() < 0 || input.balance().compareTo(MAXIMUM) > 0
                || input.balance().scale() > 2) {
            throw new IllegalArgumentException("Valid account type and non-negative NUMBER(18,2) balance are required");
        }
        accounts.lockAccount(id).orElseThrow(() -> new ResourceNotFoundExcp("Account not found"));
        BigDecimal held = transactions.pendingAmount(id, List.of(TransactionState.PROTECTED, TransactionState.HARD_HOLD));
        if (held == null || held.signum() < 0 || input.balance().subtract(held).signum() < 0) {
            throw new TransactionValidationException(409, "Balance must cover all held payments");
        }
        if (accounts.updateDetails(id, input.balance().setScale(2), input.accountType(), LocalDateTime.now()) != 1) {
            throw new TransactionValidationException(409, "Account changed concurrently; refresh and retry");
        }
        return Response.from(accounts.lockAccount(id).orElseThrow(() -> new ResourceNotFoundExcp("Account not found")));
    }
}
