package com.ofss.common.api;

public record FieldValidationError(
        String field,
        String message) {
}