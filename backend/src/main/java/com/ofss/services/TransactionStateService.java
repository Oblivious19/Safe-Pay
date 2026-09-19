package com.ofss.services;

import java.time.OffsetDateTime;

import com.ofss.beans.TransactionDb;
import com.ofss.beans.TransactionState;

public interface TransactionStateService {

    boolean canTransition(
            TransactionState currentState,
            TransactionState targetState);

    void transition(
            TransactionDb transaction,
            TransactionState targetState,
            OffsetDateTime occurredAt);

    void transition(
            TransactionDb transaction,
            TransactionState targetState,
            String terminalReasonCode,
            OffsetDateTime occurredAt);
}
