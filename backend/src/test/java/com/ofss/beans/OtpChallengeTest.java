package com.ofss.beans;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ofss.services.OtpPolicyProperties;

class OtpChallengeTest {

    private static final OffsetDateTime ISSUED_AT =
            OffsetDateTime.of(
                    2026,
                    9,
                    16,
                    10,
                    15,
                    0,
                    0,
                    ZoneOffset.UTC);

    private TransactionDb transaction;
    private User customer;
    private OtpPolicyProperties policy;

    @BeforeEach
    void setUp() {
        customer = mock(User.class);
        when(customer.getUserId()).thenReturn(51L);

        transaction = mock(TransactionDb.class);
        when(transaction.getTransactionId()).thenReturn(101L);
        when(transaction.getCustomer()).thenReturn(customer);
        when(transaction.getState()).thenReturn(
                TransactionState.VERIFICATION_REQUIRED);
        when(transaction.getRiskTier()).thenReturn(
                RiskTier.VERY_HIGH);

        policy = new OtpPolicyProperties(
                Duration.ofMinutes(5),
                3,
                Duration.ofSeconds(30),
                3,
                6);
    }

    @Test
    void issuesExactPendingV5V10Challenge() {
        OtpChallenge challenge = issue();

        assertThat(challenge.getTransaction()).isSameAs(transaction);
        assertThat(challenge.getCustomer()).isSameAs(customer);
        assertThat(challenge.getChallengePurpose())
                .isEqualTo(OtpChallengePurpose.VERY_HIGH_PAYMENT);
        assertThat(challenge.getDeliveryChannel())
                .isEqualTo(OtpDeliveryChannel.SIMULATED);
        assertThat(challenge.getOtpHash()).isEqualTo("encoded-hash");
        assertThat(challenge.getStatus())
                .isEqualTo(OtpChallengeStatus.PENDING);
        assertThat(challenge.getAttemptCount()).isZero();
        assertThat(challenge.getMaxAttempts()).isEqualTo(3);
        assertThat(challenge.getCreatedAt()).isEqualTo(ISSUED_AT);
        assertThat(challenge.getUpdatedAt()).isEqualTo(ISSUED_AT);
        assertThat(challenge.getExpiresAt())
                .isEqualTo(ISSUED_AT.plusMinutes(5));
        assertThat(challenge.getVerifiedAt()).isNull();
        assertThat(challenge.getInvalidatedAt()).isNull();
    }

    @Test
    void locksExactlyOnThirdFailedAttempt() {
        OtpChallenge challenge = issue();

        assertThat(challenge.recordFailedAttempt(
                ISSUED_AT.plusSeconds(1)))
                .isEqualTo(OtpChallengeStatus.PENDING);
        assertThat(challenge.recordFailedAttempt(
                ISSUED_AT.plusSeconds(2)))
                .isEqualTo(OtpChallengeStatus.PENDING);
        assertThat(challenge.recordFailedAttempt(
                ISSUED_AT.plusSeconds(3)))
                .isEqualTo(OtpChallengeStatus.LOCKED);

        assertThat(challenge.getAttemptCount()).isEqualTo(3);
        assertThat(challenge.getInvalidatedAt())
                .isEqualTo(ISSUED_AT.plusSeconds(3));
        assertThat(challenge.getVerifiedAt()).isNull();
    }

    @Test
    void verifiesOnceBeforeExpiry() {
        OtpChallenge challenge = issue();
        OffsetDateTime verifiedAt = ISSUED_AT.plusMinutes(4);

        challenge.markVerified(verifiedAt);

        assertThat(challenge.getStatus())
                .isEqualTo(OtpChallengeStatus.VERIFIED);
        assertThat(challenge.getVerifiedAt()).isEqualTo(verifiedAt);
        assertThat(challenge.getInvalidatedAt()).isNull();
        assertThat(challenge.getUpdatedAt()).isEqualTo(verifiedAt);
    }

    @Test
    void expiresOnlyAtOrAfterServerExpiry() {
        OtpChallenge challenge = issue();

        assertThatThrownBy(() -> challenge.expire(
                ISSUED_AT.plusMinutes(4)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("expiredAt cannot be before expiresAt");

        challenge.expire(ISSUED_AT.plusMinutes(5));

        assertThat(challenge.getStatus())
                .isEqualTo(OtpChallengeStatus.EXPIRED);
        assertThat(challenge.getInvalidatedAt())
                .isEqualTo(ISSUED_AT.plusMinutes(5));
    }

    @Test
    void cancellationInvalidatesPriorChallengeForResend() {
        OtpChallenge challenge = issue();
        OffsetDateTime cancelledAt = ISSUED_AT.plusSeconds(30);

        challenge.cancel(cancelledAt);

        assertThat(challenge.getStatus())
                .isEqualTo(OtpChallengeStatus.CANCELLED);
        assertThat(challenge.getInvalidatedAt())
                .isEqualTo(cancelledAt);
    }

    @Test
    void rejectsAttemptsAndVerificationAtExpiryBoundary() {
        OtpChallenge challenge = issue();

        assertThat(challenge.isExpiredAt(
                ISSUED_AT.plusMinutes(5))).isTrue();
        assertThatThrownBy(() -> challenge.recordFailedAttempt(
                ISSUED_AT.plusMinutes(5)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("The OTP challenge has expired");
        assertThatThrownBy(() -> challenge.markVerified(
                ISSUED_AT.plusMinutes(5)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("The OTP challenge has expired");
    }

    @Test
    void terminalChallengeCannotBeReusedOrMutated() {
        OtpChallenge challenge = issue();
        challenge.markVerified(ISSUED_AT.plusSeconds(1));

        assertThatThrownBy(() -> challenge.markVerified(
                ISSUED_AT.plusSeconds(2)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "A terminal OTP challenge cannot change");
        assertThatThrownBy(() -> challenge.cancel(
                ISSUED_AT.plusSeconds(2)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "A terminal OTP challenge cannot change");
    }

    @Test
    void rejectsBackdatedLifecycleMutation() {
        OtpChallenge challenge = issue();
        challenge.recordFailedAttempt(ISSUED_AT.plusSeconds(2));

        assertThatThrownBy(() -> challenge.recordFailedAttempt(
                ISSUED_AT.plusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "OTP challenge time cannot move backwards");
    }

    @Test
    void rejectsNonVeryHighOrWrongStateTransactions() {
        when(transaction.getRiskTier()).thenReturn(RiskTier.HIGH);

        assertThatThrownBy(this::issue)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "OTP challenge requires a VERY_HIGH transaction in VERIFICATION_REQUIRED");

        when(transaction.getRiskTier()).thenReturn(RiskTier.VERY_HIGH);
        when(transaction.getState()).thenReturn(
                TransactionState.PENDING_RISK_REVIEW);

        assertThatThrownBy(this::issue)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "OTP challenge requires a VERY_HIGH transaction in VERIFICATION_REQUIRED");
    }

    @Test
    void rejectsUnpersistedTransactionOrCustomer() {
        when(transaction.getTransactionId()).thenReturn(null);

        assertThatThrownBy(this::issue)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "transaction must already be persisted");

        when(transaction.getTransactionId()).thenReturn(101L);
        when(customer.getUserId()).thenReturn(null);

        assertThatThrownBy(this::issue)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "transaction customer must already be persisted");
    }

    private OtpChallenge issue() {
        return OtpChallenge.issue(
                transaction,
                "encoded-hash",
                policy,
                ISSUED_AT);
    }
}
