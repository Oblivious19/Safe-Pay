package com.ofss.services;

import java.util.Locale;
import java.util.Optional;

import com.ofss.beans.Account;
import com.ofss.beans.Beneficiary;
import com.ofss.repository.AccountDao;

/** Resolves a recipient from the existing beneficiary and account records only. */
final class InternalPaymentRecipientResolver {
    private final AccountDao accounts;

    InternalPaymentRecipientResolver(AccountDao accounts) {
        this.accounts = accounts;
    }

    Optional<Long> recipientAccountId(Beneficiary beneficiary) {
        if (beneficiary == null) return Optional.empty();
        Optional<Account> candidate = accounts.findByAccountNumber(beneficiary.getBankAccountNumber());
        // Mockito's unstubbed Optional methods return null; production repositories never do.
        if (candidate == null || candidate.isEmpty()) return Optional.empty();
        Account account = candidate.get();
        boolean nameMatches = normalize(account.getUser().getName()).equals(normalize(beneficiary.getBeneficiaryName()));
        return nameMatches ? Optional.of(account.getAccountId()) : Optional.empty();
    }

    private String normalize(String value) {
        return value == null ? "" : value.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
