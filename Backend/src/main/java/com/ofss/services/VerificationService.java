package com.ofss.services;

import com.ofss.beans.VerifiedTransactionResponse;
import com.ofss.excp.TransactionValidationException;
import org.springframework.stereotype.Service;

/** Compatibility endpoint: customer password verification can no longer release funds. */
@Service
public class VerificationService {
    public VerifiedTransactionResponse verify(Long id, Long callerId, String password, String key) {
        throw new TransactionValidationException(403, "This payment requires administrator approval");
    }
}
