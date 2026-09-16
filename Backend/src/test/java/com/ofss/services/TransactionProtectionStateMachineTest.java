package com.ofss.services;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.EnumSource;
import com.ofss.beans.*;
import com.ofss.excp.InvalidStateTransitionException;
import com.ofss.services.TransactionProtectionStateMachine.Snapshot;

class TransactionProtectionStateMachineTest {
    private final TransactionProtectionStateMachine machine = new TransactionProtectionStateMachine();
    private static final Instant NOW = Instant.parse("2026-09-13T12:00:00Z");

    private Snapshot snapshot(ProtectionState state) {
        return new Snapshot(state, 103L,
                state == ProtectionState.HARD_HOLD ? AssessmentRiskTier.VERY_HIGH : AssessmentRiskTier.MEDIUM,
                state == ProtectionState.HARD_HOLD ? null : NOW);
    }

    private RuleBasedRiskResult risk(AssessmentRiskTier tier) {
        int seconds = tier == AssessmentRiskTier.MEDIUM ? 10 : tier == AssessmentRiskTier.HIGH ? 60 : 0;
        return new RuleBasedRiskResult(tier, tier != AssessmentRiskTier.LOW, seconds, "Test reason",
                tier == AssessmentRiskTier.VERY_HIGH);
    }

    @ParameterizedTest
    @EnumSource(AssessmentRiskTier.class)
    void decisionCreatesCorrectProtectionWithoutMutatingSnapshot(AssessmentRiskTier tier) {
        var created = new Snapshot(ProtectionState.CREATED, 103L, null, null);
        var authorized = machine.authorize(created);
        var result = machine.applyRisk(authorized, risk(tier), NOW);
        assertEquals(ProtectionState.CREATED, created.state());
        assertEquals(ProtectionState.AUTHORIZED, authorized.state());
        assertEquals(tier, result.tier());
        assertEquals(103L, result.ownerId());
        switch (tier) {
            case LOW -> {
                assertEquals(ProtectionState.RELEASED, result.state());
                assertNull(result.expiresAt());
                assertEquals(ProtectionState.SETTLED, machine.settle(result).state());
            }
            case MEDIUM, HIGH -> {
                assertEquals(ProtectionState.PROTECTED, result.state());
                assertEquals(NOW.plusSeconds(tier == AssessmentRiskTier.MEDIUM ? 10 : 60), result.expiresAt());
                assertEquals(ProtectionState.SETTLED, machine.settle(machine.release(result, result.expiresAt())).state());
            }
            case VERY_HIGH -> {
                assertEquals(ProtectionState.HARD_HOLD, result.state());
                assertNull(result.expiresAt());
                assertEquals(ProtectionState.SETTLED, machine.approve(result).state());
                assertEquals(ProtectionState.REJECTED, machine.reject(result).state());
            }
        }
    }

    static Stream<Arguments> edges() {
        return Stream.of(ProtectionState.values()).flatMap(state -> Stream.of(
                "authorize", "risk", "cancel", "release", "settle", "approve", "reject")
                .map(operation -> Arguments.of(state, operation)));
    }

    @ParameterizedTest
    @MethodSource("edges")
    void exhaustiveSourceStateOperationMatrix(ProtectionState state, String operation) {
        ProtectionState source = switch (operation) {
            case "authorize" -> ProtectionState.CREATED;
            case "risk" -> ProtectionState.AUTHORIZED;
            case "cancel", "release" -> ProtectionState.PROTECTED;
            case "settle" -> ProtectionState.RELEASED;
            default -> ProtectionState.HARD_HOLD;
        };
        java.util.function.Supplier<Snapshot> action = () -> switch (operation) {
            case "authorize" -> machine.authorize(snapshot(state));
            case "risk" -> machine.applyRisk(snapshot(state), risk(AssessmentRiskTier.LOW), NOW);
            case "cancel" -> machine.cancel(snapshot(state), 103L, NOW.minusNanos(1));
            case "release" -> machine.release(snapshot(state), NOW);
            case "settle" -> machine.settle(snapshot(state));
            case "approve" -> machine.approve(snapshot(state));
            default -> machine.reject(snapshot(state));
        };
        if (state == source) assertDoesNotThrow(action::get);
        else assertThrows(InvalidStateTransitionException.class, action::get);
    }

    @Test
    void ownershipAndExactExpiryBoundaries() {
        var protectedTx = snapshot(ProtectionState.PROTECTED);
        assertEquals(ProtectionState.CANCELLED, machine.cancel(protectedTx, 103L, NOW.minusNanos(1)).state());
        assertThrows(InvalidStateTransitionException.class, () -> machine.cancel(protectedTx, 104L, NOW.minusSeconds(1)));
        assertThrows(InvalidStateTransitionException.class, () -> machine.cancel(protectedTx, null, NOW.minusSeconds(1)));
        assertThrows(InvalidStateTransitionException.class, () -> machine.cancel(protectedTx, 103L, NOW));
        assertThrows(InvalidStateTransitionException.class, () -> machine.cancel(protectedTx, 103L, NOW.plusNanos(1)));
        assertThrows(InvalidStateTransitionException.class, () -> machine.release(protectedTx, NOW.minusNanos(1)));
        assertEquals(ProtectionState.RELEASED, machine.release(protectedTx, NOW).state());
        assertEquals(ProtectionState.RELEASED, machine.release(protectedTx, NOW.plusNanos(1)).state());
    }

    @ParameterizedTest
    @EnumSource(AssessmentRiskTier.class)
    void inconsistentRiskFlagsFailClosed(AssessmentRiskTier tier) {
        var valid = risk(tier);
        var authorized = snapshot(ProtectionState.AUTHORIZED);
        assertThrows(InvalidStateTransitionException.class, () -> machine.applyRisk(authorized,
                new RuleBasedRiskResult(tier, valid.protectionRequired(), 99, "X", valid.authenticationRequired()), NOW));
        assertThrows(InvalidStateTransitionException.class, () -> machine.applyRisk(authorized,
                new RuleBasedRiskResult(tier, !valid.protectionRequired(), valid.protectionDurationSeconds(), "X", valid.authenticationRequired()), NOW));
        assertThrows(InvalidStateTransitionException.class, () -> machine.applyRisk(authorized,
                new RuleBasedRiskResult(tier, valid.protectionRequired(), valid.protectionDurationSeconds(), "X", !valid.authenticationRequired()), NOW));
    }

    @Test
    void missingOrContradictorySnapshotCannotReleaseMoney() {
        assertThrows(InvalidStateTransitionException.class, () -> machine.authorize(null));
        assertThrows(InvalidStateTransitionException.class, () -> machine.authorize(new Snapshot(ProtectionState.CREATED, null, null, null)));
        assertThrows(InvalidStateTransitionException.class, () -> machine.applyRisk(snapshot(ProtectionState.AUTHORIZED), null, NOW));
        assertThrows(InvalidStateTransitionException.class, () -> machine.release(new Snapshot(ProtectionState.PROTECTED, 103L, AssessmentRiskTier.MEDIUM, null), NOW));
        assertThrows(InvalidStateTransitionException.class, () -> machine.release(new Snapshot(ProtectionState.PROTECTED, 103L, AssessmentRiskTier.VERY_HIGH, NOW), NOW));
        assertThrows(InvalidStateTransitionException.class, () -> machine.settle(new Snapshot(ProtectionState.RELEASED, 103L, AssessmentRiskTier.VERY_HIGH, null)));
        assertThrows(InvalidStateTransitionException.class, () -> machine.approve(new Snapshot(ProtectionState.HARD_HOLD, 103L, AssessmentRiskTier.VERY_HIGH, NOW)));
    }
}
