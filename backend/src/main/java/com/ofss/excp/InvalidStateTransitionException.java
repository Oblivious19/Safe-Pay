package com.ofss.excp;

import java.util.Objects;

import com.ofss.beans.TransactionState;

public class InvalidStateTransitionException
        extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final TransactionState currentState;
    private final TransactionState targetState;

    public InvalidStateTransitionException(
            TransactionState currentState,
            TransactionState targetState) {

        super("Transaction cannot move from "
                + Objects.requireNonNull(
                        currentState,
                        "currentState is required")
                + " to "
                + Objects.requireNonNull(
                        targetState,
                        "targetState is required"));

        this.currentState = currentState;
        this.targetState = targetState;
    }

    public TransactionState getCurrentState() {
        return currentState;
    }

    public TransactionState getTargetState() {
        return targetState;
    }
}
