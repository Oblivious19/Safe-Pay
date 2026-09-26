package com.ofss.services;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.ofss.beans.Account;
import com.ofss.beans.AccountStatus;
import com.ofss.excp.ResourceNotFoundExcp;
import com.ofss.excp.TransactionValidationException;
import com.ofss.repository.AccountDao;

/** Used only inside the caller's database transaction, after its state/reservation checks. */
final class TransferBalances {
    private static final BigDecimal MAX = new BigDecimal("9999999999999999.99");
    private TransferBalances() { }

    static Account lock(AccountDao accounts, Long id) {
        return accounts.findForSettlement(id).orElseThrow(() -> new ResourceNotFoundExcp("Account not found"));
    }

    /** Every two-account money writer takes locks in ID order, including opposite-direction transfers. */
    static Account lockPair(AccountDao accounts, Long source, Long receiver) {
        if (receiver != null && receiver < source) lock(accounts, receiver);
        Account account = lock(accounts, source);
        if (receiver != null && receiver > source) lock(accounts, receiver);
        return account;
    }

    static void validateReceiver(Account source, Account receiver, BigDecimal amount) {
        if (receiver == null) return; // Existing external-bank simulation: no local recipient row.
        if (source.getAccountId().equals(receiver.getAccountId()))
            throw new TransactionValidationException(409, "Choose a different receiving account");
        if (receiver.getStatus() != AccountStatus.ACTIVE)
            throw new TransactionValidationException(409, "The receiving account is unavailable for this payment");
        if (receiver.getBalance() == null || receiver.getBalance().add(amount).compareTo(MAX) > 0)
            throw new TransactionValidationException(409, "The receiving account cannot accept this amount");
    }

    static void move(AccountDao accounts, Account source, Account receiver, BigDecimal amount, LocalDateTime now) {
        validateReceiver(source, receiver, amount);
        source.setBalance(source.getBalance().subtract(amount));
        source.setUpdatedAt(now);
        accounts.save(source);
        if (receiver != null) {
            receiver.setBalance(receiver.getBalance().add(amount));
            receiver.setUpdatedAt(now);
            accounts.save(receiver);
        }
    }
}
