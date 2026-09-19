package com.ofss.services;

import java.util.Objects;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ofss.dto.audit.AuditSafeDetails;

@Component
public class AuditDetailSanitizer {

    private final ObjectMapper objectMapper;

    public AuditDetailSanitizer(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(
                objectMapper,
                "objectMapper is required");
    }

    public AuditSafeDetails sanitize(String detailsJson) {
        if (detailsJson == null || detailsJson.isBlank()) {
            return null;
        }

        final JsonNode details;
        try {
            details = objectMapper.readTree(detailsJson);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Stored audit details are not valid JSON",
                    exception);
        }

        if (details == null || !details.isObject()) {
            return null;
        }

        AuditSafeDetails safe = new AuditSafeDetails(
                text(details, "reviewId"),
                integer(details, "reviewRound"),
                text(details, "reviewStatus"),
                text(details, "decisionReason"),
                text(details, "note"));

        return allNull(safe) ? null : safe;
    }

    private static String text(JsonNode root, String fieldName) {
        JsonNode value = root.get(fieldName);
        return value == null || value.isNull() || !value.isValueNode()
                ? null
                : value.asText();
    }

    private static Integer integer(JsonNode root, String fieldName) {
        JsonNode value = root.get(fieldName);
        return value == null || !value.canConvertToInt()
                ? null
                : value.intValue();
    }

    private static boolean allNull(AuditSafeDetails details) {
        return details.reviewId() == null
                && details.reviewRound() == null
                && details.reviewStatus() == null
                && details.decisionReason() == null
                && details.internalNote() == null;
    }
}
