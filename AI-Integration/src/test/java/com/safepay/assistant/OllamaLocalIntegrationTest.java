package com.safepay.assistant;

import static com.safepay.assistant.Models.*;
import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Opt-in smoke test; uses invented evidence and never connects to SafePay or Oracle. */
@EnabledIfEnvironmentVariable(named="SAFEPAY_OLLAMA_TEST", matches="true")
class OllamaLocalIntegrationTest {
    @Test void installedLocalModelReturnsValidatedReviewFromSyntheticEvidence() {
        var reviewer = new OllamaReviewer("http://127.0.0.1:11434",
            System.getenv().getOrDefault("OLLAMA_MODEL", "qwen2.5:1.5b"), 90,
            new ObjectMapper().findAndRegisterModules());
        assertEquals(true, reviewer.status().get("ready"), "Configured local model must be installed");
        var facts = List.of(
            new Fact("CURRENT", "Current payment", "Payment amount INR 120000.00. Saved tier: VERY_HIGH. Current state: HARD_HOLD. Administrator approval is required.", List.of(1L)),
            new Fact("HISTORY", "History", "No settled payments in the 30 days preceding this payment. Insufficient history for an amount comparison.", List.of()),
            new Fact("BENEFICIARY", "Recipient history", "No earlier settled payment to this recipient was found. This does not by itself indicate fraud.", List.of()),
            new Fact("VELOCITY", "Recent requests", "One payment request in the five minutes up to this payment, including this request.", List.of(1L)));
        var evidence = new Evidence(1L, "SYNTHETIC-ONLY", 0L, 1L, 1L, "120000.00",
            "HARD_HOLD", "VERY_HIGH", "Synthetic example", "Synthetic recipient", "0000", "Synthetic purpose",
            LocalDateTime.of(2026,9,16,12,0), Instant.now(), "Synthetic only", facts, List.of());
        var result = reviewer.review(evidence);
        assertFalse(result.focusEvidenceIds().isEmpty());
        assertTrue(facts.stream().map(Fact::id).toList().containsAll(result.focusEvidenceIds()));
        assertTrue(OllamaReviewer.CHECKS.keySet().containsAll(result.suggestedChecks()));
        assertTrue(result.suggestedChecks().contains("CHECK_HISTORY_LIMITATIONS"), "Model should surface missing history");
        System.out.println("Local Ollama synthetic review: " + result);
    }
}