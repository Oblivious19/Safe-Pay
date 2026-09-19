package com.ofss.services;

import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.ofss.beans.TransactionDb;
import com.ofss.beans.TransactionState;
import com.ofss.excp.InvalidStateTransitionException;

@Service
public class TransactionStateServiceImpl
        implements TransactionStateService {

    private static final Map<TransactionState, Set<TransactionState>>
            ALLOWED_TRANSITIONS = createTransitionMatrix();

    @Override
    public boolean canTransition(
            TransactionState currentState,
            TransactionState targetState) {

        Objects.requireNonNull(currentState, "currentState is required");
        Objects.requireNonNull(targetState, "targetState is required");

        return ALLOWED_TRANSITIONS
                .getOrDefault(currentState, Set.of())
                .contains(targetState);
    }

    @Override
    public void transition(
            TransactionDb transaction,
            TransactionState targetState,
            OffsetDateTime occurredAt) {

        transition(transaction, targetState, null, occurredAt);
    }

    @Override
    public void transition(
            TransactionDb transaction,
            TransactionState targetState,
            String terminalReasonCode,
            OffsetDateTime occurredAt) {

        Objects.requireNonNull(transaction, "transaction is required");
        Objects.requireNonNull(targetState, "targetState is required");
        Objects.requireNonNull(occurredAt, "occurredAt is required");

        TransactionState currentState = Objects.requireNonNull(
                transaction.getState(),
                "transaction state is required");

        if (!canTransition(currentState, targetState)) {
            throw new InvalidStateTransitionException(
                    currentState,
                    targetState);
        }

        validateProtectedDeadline(
                transaction,
                currentState,
                targetState,
                occurredAt);

        transaction.applyValidatedTransition(
                targetState,
                terminalReasonCode,
                occurredAt);
    }

    private static void validateProtectedDeadline(
            TransactionDb transaction,
            TransactionState currentState,
            TransactionState targetState,
            OffsetDateTime occurredAt) {

        if (currentState != TransactionState.PROTECTED) {
            return;
        }

        OffsetDateTime deadline = transaction.getProtectedUntil();

        boolean invalidCancellation =
                targetState == TransactionState.CANCELLED
                        && (deadline == null
                                || !occurredAt.isBefore(deadline));

        boolean prematureRelease =
                targetState == TransactionState.RELEASED
                        && (deadline == null
                                || occurredAt.isBefore(deadline));

        if (invalidCancellation || prematureRelease) {
            throw new InvalidStateTransitionException(
                    currentState,
                    targetState);
        }
    }

    private static Map<TransactionState, Set<TransactionState>>
            createTransitionMatrix() {

        EnumMap<TransactionState, Set<TransactionState>> matrix =
                new EnumMap<>(TransactionState.class);

        matrix.put(
                TransactionState.CREATED,
                EnumSet.of(
                        TransactionState.AUTHORIZED,
                        TransactionState.CANCELLED));
        matrix.put(
                TransactionState.AUTHORIZED,
                EnumSet.of(
                        TransactionState.RISK_ASSESSED,
                        TransactionState.FAILED));
        matrix.put(
                TransactionState.RISK_ASSESSED,
                EnumSet.of(
                        TransactionState.PROTECTED,
                        TransactionState.VERIFICATION_REQUIRED,
                        TransactionState.RELEASED));
        matrix.put(
                TransactionState.PROTECTED,
                EnumSet.of(
                        TransactionState.CANCELLED,
                        TransactionState.RELEASED));
        matrix.put(
                TransactionState.VERIFICATION_REQUIRED,
                EnumSet.of(
                        TransactionState.PENDING_RISK_REVIEW,
                        TransactionState.CANCELLED));
        matrix.put(
                TransactionState.PENDING_RISK_REVIEW,
                EnumSet.of(
                        TransactionState.RELEASED,
                        TransactionState.CANCELLED,
                        TransactionState.VERIFICATION_REQUIRED));
        matrix.put(
                TransactionState.RELEASED,
                EnumSet.of(
                        TransactionState.SETTLED,
                        TransactionState.FAILED));

        for (TransactionState state : TransactionState.values()) {
            matrix.putIfAbsent(state, Set.of());
        }

        return Map.copyOf(matrix);
    }
}
