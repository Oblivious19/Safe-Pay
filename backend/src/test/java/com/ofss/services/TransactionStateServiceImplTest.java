package com.ofss.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ofss.beans.Account;
import com.ofss.beans.Beneficiary;
import com.ofss.beans.CurrencyCode;
import com.ofss.beans.TransactionDb;
import com.ofss.beans.TransactionState;
import com.ofss.beans.User;
import com.ofss.excp.InvalidStateTransitionException;

class TransactionStateServiceImplTest {

    private static final OffsetDateTime CREATED_AT =
            OffsetDateTime.parse("2026-09-15T10:00:00Z");

    private TransactionStateService service;

    @BeforeEach
    void setUp() {
        service = new TransactionStateServiceImpl();
    }

    @Test
    void exposesExactlyTheApprovedTransitionMatrix() {
        Map<TransactionState, Set<TransactionState>> expected = Map.of(
                TransactionState.CREATED,
                Set.of(
                        TransactionState.AUTHORIZED,
                        TransactionState.CANCELLED),
                TransactionState.AUTHORIZED,
                Set.of(
                        TransactionState.RISK_ASSESSED,
                        TransactionState.FAILED),
                TransactionState.RISK_ASSESSED,
                Set.of(
                        TransactionState.PROTECTED,
                        TransactionState.VERIFICATION_REQUIRED,
                        TransactionState.RELEASED),
                TransactionState.PROTECTED,
                Set.of(
                        TransactionState.CANCELLED,
                        TransactionState.RELEASED),
                TransactionState.VERIFICATION_REQUIRED,
                Set.of(
                        TransactionState.PENDING_RISK_REVIEW,
                        TransactionState.CANCELLED),
                TransactionState.PENDING_RISK_REVIEW,
                Set.of(
                        TransactionState.RELEASED,
                        TransactionState.CANCELLED,
                        TransactionState.VERIFICATION_REQUIRED),
                TransactionState.RELEASED,
                Set.of(
                        TransactionState.SETTLED,
                        TransactionState.FAILED));

        for (TransactionState current : TransactionState.values()) {
            for (TransactionState target : TransactionState.values()) {
                assertThat(service.canTransition(current, target))
                        .as("%s -> %s", current, target)
                        .isEqualTo(expected
                                .getOrDefault(current, Set.of())
                                .contains(target));
            }
        }
    }

    @Test
    void rejectsAForbiddenTransitionWithoutChangingState() {
        TransactionDb transaction = newTransaction();

        assertThatThrownBy(() -> service.transition(
                transaction,
                TransactionState.SETTLED,
                CREATED_AT.plusSeconds(1)))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessage(
                        "Transaction cannot move from CREATED to SETTLED");

        assertThat(transaction.getState())
                .isEqualTo(TransactionState.CREATED);
    }

    @Test
    void terminalStatesHaveNoOutgoingTransitions() {
        for (TransactionState terminal : Set.of(
                TransactionState.SETTLED,
                TransactionState.CANCELLED,
                TransactionState.FAILED)) {

            for (TransactionState target : TransactionState.values()) {
                assertThat(service.canTransition(terminal, target))
                        .isFalse();
            }
        }
    }

    @Test
    void recordsAuthorizationTimeInUtcAtOracleMicrosecondPrecision() {
        TransactionDb transaction = newTransaction();

        service.transition(
                transaction,
                TransactionState.AUTHORIZED,
                OffsetDateTime.parse(
                        "2026-09-15T15:30:01.123456789+05:30"));

        assertThat(transaction.getState())
                .isEqualTo(TransactionState.AUTHORIZED);
        assertThat(transaction.getAuthorizedAt())
                .isEqualTo(
                        OffsetDateTime.parse(
                                "2026-09-15T10:00:01.123456Z"));
        assertThat(transaction.getUpdatedAt())
                .isEqualTo(transaction.getAuthorizedAt());
    }

    @Test
    void normalizesCancellationReasonAndRecordsTerminalTime() {
        TransactionDb transaction = newTransaction();

        service.transition(
                transaction,
                TransactionState.CANCELLED,
                "  customer_cancelled  ",
                CREATED_AT.plusSeconds(2));

        assertThat(transaction.getState())
                .isEqualTo(TransactionState.CANCELLED);
        assertThat(transaction.getTerminalReasonCode())
                .isEqualTo("CUSTOMER_CANCELLED");
        assertThat(transaction.getCancelledAt())
                .isEqualTo(CREATED_AT.plusSeconds(2));
    }

    @Test
    void rejectsReasonCodeOnNonTerminalTransition() {
        TransactionDb transaction = newTransaction();

        assertThatThrownBy(() -> service.transition(
                transaction,
                TransactionState.AUTHORIZED,
                "NOT_ALLOWED",
                CREATED_AT.plusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "A reason code is permitted only for failure or cancellation");
    }

    @Test
    void requiresReasonCodeForFailureOrCancellation() {
        TransactionDb transaction = newTransaction();

        assertThatThrownBy(() -> service.transition(
                transaction,
                TransactionState.CANCELLED,
                CREATED_AT.plusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("terminalReasonCode is required");
    }

    private static TransactionDb newTransaction() {
        User customer = mock(User.class);
        Account account = mock(Account.class);
        Beneficiary beneficiary = mock(Beneficiary.class);

        when(customer.getUserId()).thenReturn(7L);
        when(account.getAccountId()).thenReturn(70L);
        when(account.getOwner()).thenReturn(customer);
        when(account.isCustomerOwnedAccount()).thenReturn(true);
        when(account.getCurrencyCode()).thenReturn(CurrencyCode.INR);
        when(beneficiary.getBeneficiaryId()).thenReturn(700L);
        when(beneficiary.getOwner()).thenReturn(customer);

        return TransactionDb.createPaymentInstruction(
                "SP-STATE-TEST",
                customer,
                account,
                beneficiary,
                new BigDecimal("100.00"),
                null,
                null,
                CREATED_AT);
    }
}
