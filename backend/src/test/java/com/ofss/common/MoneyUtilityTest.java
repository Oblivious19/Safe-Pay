package com.ofss.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MoneyUtilityTest {

    @Test
    void normalizesValidAmountsToTwoDecimalPlaces() {
        assertEquals(
                new BigDecimal("1.00"),
                MoneyUtility.requireValidTransactionAmount(
                        new BigDecimal("1")));

        assertEquals(
                new BigDecimal("5000.01"),
                MoneyUtility.requireValidTransactionAmount(
                        new BigDecimal("5000.01")));

        assertEquals(
                new BigDecimal("9999999999999999.99"),
                MoneyUtility.requireValidTransactionAmount(
                        new BigDecimal("9999999999999999.99")));
    }

    @Test
    void rejectsMissingAmount() {
        assertThrows(
                IllegalArgumentException.class,
                () -> MoneyUtility.requireValidTransactionAmount(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "-1.00",
            "0.00",
            "0.99",
            "1.000",
            "5000.001",
            "9999999999999999.999",
            "10000000000000000.00"
    })
    void rejectsInvalidAmounts(String value) {
        BigDecimal amount = new BigDecimal(value);

        assertThrows(
                IllegalArgumentException.class,
                () -> MoneyUtility.requireValidTransactionAmount(amount));
    }
}