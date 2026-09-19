package com.ofss.services;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ofss.beans.IdempotencyOperation;

@Service
public class RequestFingerprintService {

    private final ObjectMapper objectMapper;

    public RequestFingerprintService(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(
                objectMapper,
                "objectMapper is required")
                .copy();
    }

    public String fingerprint(
            IdempotencyOperation operationCode,
            String requestTarget,
            Object logicalRequest) {

        Objects.requireNonNull(
                operationCode,
                "operationCode is required");

        String normalizedTarget = requireTarget(requestTarget);

        try {
            ObjectNode scope = objectMapper.createObjectNode();

            scope.put("operationCode", operationCode.name());
            scope.put("requestTarget", normalizedTarget);
            scope.set(
                    "logicalRequest",
                    logicalRequest == null
                            ? NullNode.getInstance()
                            : objectMapper.valueToTree(
                                    logicalRequest));

            byte[] canonicalRequest = objectMapper
                    .writeValueAsBytes(canonicalize(scope));

            return HexFormat.of().formatHex(
                    sha256().digest(canonicalRequest));
        } catch (JsonProcessingException
                | IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "logicalRequest cannot be fingerprinted",
                    exception);
        }
    }

    private JsonNode canonicalize(JsonNode node) {
        if (node.isObject()) {
            ObjectNode canonicalObject =
                    objectMapper.createObjectNode();

            List<String> fieldNames = new ArrayList<>();
            node.fieldNames().forEachRemaining(
                    fieldNames::add);
            fieldNames.sort(String::compareTo);

            for (String fieldName : fieldNames) {
                canonicalObject.set(
                        fieldName,
                        canonicalize(node.get(fieldName)));
            }

            return canonicalObject;
        }

        if (node.isArray()) {
            ArrayNode canonicalArray =
                    objectMapper.createArrayNode();

            for (JsonNode element : node) {
                canonicalArray.add(canonicalize(element));
            }

            return canonicalArray;
        }

        return node.deepCopy();
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable",
                    exception);
        }
    }

    private static String requireTarget(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "requestTarget is required");
        }

        return value.trim();
    }
}
