package com.ofss.beans;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BooleanToYNConverterTest {

    private final BooleanToYNConverter converter = new BooleanToYNConverter();

    @Test
    void mapsBooleanValuesToOracleYNValues() {
        assertEquals("Y", converter.convertToDatabaseColumn(true));
        assertEquals("N", converter.convertToDatabaseColumn(false));
    }

    @Test
    void mapsOracleYNValuesToBooleanValues() {
        assertTrue(converter.convertToEntityAttribute("Y"));
        assertFalse(converter.convertToEntityAttribute("N"));
    }
}
