package com.ofss.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ofss.dto.audit.AuditSafeDetails;

class AuditDetailSanitizerTest {

    private final AuditDetailSanitizer sanitizer =
            new AuditDetailSanitizer(new ObjectMapper());

    @Test
    void returnsOnlyExplicitlyApprovedReviewFields() {
        AuditSafeDetails details = sanitizer.sanitize("""
                {
                  "reviewId":"501",
                  "reviewRound":2,
                  "reviewStatus":"APPROVED",
                  "decisionReason":"SAFE_REVIEW_REASON",
                  "note":"Internal evidence",
                  "otp":"123456",
                  "passwordHash":"secret",
                  "idempotencyKey":"raw-key"
                }
                """);

        assertThat(details.reviewId()).isEqualTo("501");
        assertThat(details.reviewRound()).isEqualTo(2);
        assertThat(details.reviewStatus()).isEqualTo("APPROVED");
        assertThat(details.decisionReason())
                .isEqualTo("SAFE_REVIEW_REASON");
        assertThat(details.internalNote())
                .isEqualTo("Internal evidence");
        assertThat(AuditSafeDetails.class.getRecordComponents())
                .extracting(component -> component.getName())
                .doesNotContain(
                        "otp",
                        "passwordHash",
                        "idempotencyKey");
    }

    @Test
    void ignoresUnknownOnlyPayloads() {
        assertThat(sanitizer.sanitize(
                "{\"providerTrace\":\"unsafe\"}"))
                .isNull();
    }

    @Test
    void treatsMissingDetailsAsAbsent() {
        assertThat(sanitizer.sanitize(null)).isNull();
        assertThat(sanitizer.sanitize("   ")).isNull();
    }

    @Test
    void failsClosedForMalformedStoredJson() {
        assertThatThrownBy(() -> sanitizer.sanitize("{broken"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "Stored audit details are not valid JSON");
    }
}
