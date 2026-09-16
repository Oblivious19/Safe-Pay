package com.ofss.services;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.ofss.excp.TransactionValidationException;
class VerificationServiceTest {
    @Test void customerPasswordCanNeverReleaseAHold() {
        var error = assertThrows(TransactionValidationException.class,
                () -> new VerificationService().verify(21L, 7L, "correct", "key"));
        assertEquals(403, error.getStatus());
        assertTrue(error.getMessage().contains("administrator approval"));
    }
}
