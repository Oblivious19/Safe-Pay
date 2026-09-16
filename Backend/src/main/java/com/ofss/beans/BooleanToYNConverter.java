package com.ofss.beans;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Maps the Oracle CHAR(1) Y/N representation to the Java Boolean domain type. */
@Converter
public class BooleanToYNConverter implements AttributeConverter<Boolean, String> {

    @Override
    public String convertToDatabaseColumn(Boolean value) {
        return Boolean.TRUE.equals(value) ? "Y" : "N";
    }

    @Override
    public Boolean convertToEntityAttribute(String value) {
        if ("Y".equals(value)) {
            return true;
        }
        if ("N".equals(value)) {
            return false;
        }
        throw new IllegalArgumentException("authentication_required must be Y or N");
    }
}
