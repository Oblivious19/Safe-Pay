package com.ofss.beans;

import java.math.BigDecimal;

/** Customer-declared metadata only; never changes risk or bypasses approval. */
public enum PaymentCategory {
    MEDICAL, LOAN, FRIENDS_FAMILY, INVESTMENTS, OTHERS;

    private static final BigDecimal THRESHOLD = new BigDecimal("100000.00");

    public static boolean appliesTo(BigDecimal amount) {
        return amount != null && amount.compareTo(THRESHOLD) > 0;
    }

    public static void requireForNewPayment(BigDecimal amount, PaymentCategory category, String purpose) {
        // Amount validity is checked by the existing pre-risk validator.
        if (appliesTo(amount)) {
            if (category == null) throw new IllegalArgumentException("Choose a category for payments above INR 100000.00");
            if (category == OTHERS && (purpose == null || purpose.isBlank() || purpose.trim().length() > 140)) {
                throw new IllegalArgumentException("Others requires a purpose of 1 to 140 trimmed characters");
            }
        } else if (category != null) {
            throw new IllegalArgumentException("Category is allowed only above INR 100000.00");
        }
    }
}
