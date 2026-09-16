package com.ofss.services;

import java.time.Instant;
import java.util.Objects;
import com.ofss.beans.AssessmentRiskTier;
import com.ofss.beans.ProtectionState;
import com.ofss.beans.RuleBasedRiskResult;
import com.ofss.excp.InvalidStateTransitionException;

/** Pure domain rules from PHASE1_SPEC section 3. No persistence, HTTP or money movement. */
public final class TransactionProtectionStateMachine {
    /** Trusted database snapshot in future integration, never a client request DTO. */
    public record Snapshot(ProtectionState state, Long ownerId, AssessmentRiskTier tier, Instant expiresAt) {}

    public Snapshot authorize(Snapshot current) {
        requireState(current, ProtectionState.CREATED);
        return move(current, ProtectionState.AUTHORIZED);
    }

    public Snapshot applyRisk(Snapshot current, RuleBasedRiskResult risk, Instant now) {
        requireState(current, ProtectionState.AUTHORIZED);
        require(risk != null && risk.riskTier() != null && now != null, "Risk decision and server time are required");
        int seconds = switch (risk.riskTier()) {
            case LOW, VERY_HIGH -> 0;
            case MEDIUM -> 10;
            case HIGH -> 60;
        };
        require(risk.protectionDurationSeconds() == seconds
                && risk.protectionRequired() == (risk.riskTier() != AssessmentRiskTier.LOW)
                && risk.authenticationRequired() == (risk.riskTier() == AssessmentRiskTier.VERY_HIGH),
                "Inconsistent risk protection decision");
        ProtectionState target = switch (risk.riskTier()) {
            case LOW -> ProtectionState.RELEASED;
            case MEDIUM, HIGH -> ProtectionState.PROTECTED;
            case VERY_HIGH -> ProtectionState.HARD_HOLD;
        };
        return new Snapshot(target, current.ownerId(), risk.riskTier(),
                target == ProtectionState.PROTECTED ? now.plusSeconds(seconds) : null);
    }

    public Snapshot cancel(Snapshot current, Long callerId, Instant now) {
        requireState(current, ProtectionState.PROTECTED);
        require(callerId != null && Objects.equals(current.ownerId(), callerId), "Only the owner can cancel");
        require(now != null && now.isBefore(current.expiresAt()), "Protection window has expired");
        return move(current, ProtectionState.CANCELLED);
    }

    /** Internal timer action, not a customer-triggered release endpoint. */
    public Snapshot release(Snapshot current, Instant now) {
        requireState(current, ProtectionState.PROTECTED);
        require(now != null && !now.isBefore(current.expiresAt()), "Protection window has not expired");
        return move(current, ProtectionState.RELEASED);
    }

    public Snapshot settle(Snapshot current) {
        requireState(current, ProtectionState.RELEASED);
        return move(current, ProtectionState.SETTLED);
    }

    // Package-private trusted server decisions. These do NOT perform verification.
    // A future caller must authenticate/verify and authorize the decision first.
    // No request-supplied approval flag or approval endpoint exists in this task.
    Snapshot approve(Snapshot current) {
        requireState(current, ProtectionState.HARD_HOLD);
        return move(current, ProtectionState.SETTLED);
    }

    Snapshot reject(Snapshot current) {
        requireState(current, ProtectionState.HARD_HOLD);
        return move(current, ProtectionState.REJECTED);
    }

    private void requireState(Snapshot current, ProtectionState expected) {
        require(current != null && current.state() != null && current.ownerId() != null
                && current.ownerId() > 0, "A valid server-owned snapshot is required");
        require(current.state() == expected, "Invalid state for this operation: " + current.state());
        if (expected == ProtectionState.PROTECTED) {
            require((current.tier() == AssessmentRiskTier.MEDIUM || current.tier() == AssessmentRiskTier.HIGH)
                    && current.expiresAt() != null, "Invalid protected snapshot");
        } else if (expected == ProtectionState.HARD_HOLD) {
            require(current.tier() == AssessmentRiskTier.VERY_HIGH && current.expiresAt() == null,
                    "Invalid hard-hold snapshot");
        } else if (expected == ProtectionState.RELEASED) {
            require(current.tier() == AssessmentRiskTier.LOW || current.tier() == AssessmentRiskTier.MEDIUM
                    || current.tier() == AssessmentRiskTier.HIGH, "Invalid released snapshot");
        }
    }

    private Snapshot move(Snapshot current, ProtectionState target) {
        return new Snapshot(target, current.ownerId(), current.tier(), current.expiresAt());
    }

    private void require(boolean valid, String message) {
        if (!valid) throw new InvalidStateTransitionException(message);
    }
}
