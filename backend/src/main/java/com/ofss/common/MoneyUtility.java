package com.ofss.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class MoneyUtility {

    public static final int MONEY_PRECISION = 18;
    public static final int MONEY_SCALE = 2;

    public static final BigDecimal MINIMUM_TRANSACTION_AMOUNT =
            new BigDecimal("1.00");

    private MoneyUtility() {
        // Utility class; instantiation is prohibited.
    }

    public static BigDecimal requireValidTransactionAmount(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("Amount is required");
        }

        if (amount.scale() > MONEY_SCALE) {
            throw new IllegalArgumentException(
                    "Amount must have no more than two decimal places");
        }

        if (amount.compareTo(MINIMUM_TRANSACTION_AMOUNT) < 0) {
            throw new IllegalArgumentException(
                    "Amount must be at least 1.00");
        }

        BigDecimal normalizedAmount =
                amount.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);

        if (normalizedAmount.precision() > MONEY_PRECISION) {
            throw new IllegalArgumentException(
                    "Amount exceeds the maximum supported value");
        }

        return normalizedAmount;
    }
}