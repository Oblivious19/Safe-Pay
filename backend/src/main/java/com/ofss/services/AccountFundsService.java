package com.ofss.services;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public interface AccountFundsService {

    /*
     * Deferred Spring Security integration:
     * ownerUserId comes from the authenticated principal.
     * A public request must never choose this value.
     */
    void reserveOwnedFunds(
            Long ownerUserId,
            Long sourceAccountId,
            BigDecimal amount,
            OffsetDateTime reservedAt);

    void releaseReservedFunds(
            Long sourceAccountId,
            BigDecimal amount,
            OffsetDateTime releasedAt);

    void settleReservedFunds(
            Long sourceAccountId,
            Long clearingAccountId,
            BigDecimal amount,
            OffsetDateTime settledAt);
}
