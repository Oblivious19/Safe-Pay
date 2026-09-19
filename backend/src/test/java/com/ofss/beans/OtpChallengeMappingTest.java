package com.ofss.beans;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;

import org.junit.jupiter.api.Test;

import jakarta.persistence.Column;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

class OtpChallengeMappingTest {

    @Test
    void mapsCanonicalTableAndSequence() {
        Table table = OtpChallenge.class.getAnnotation(Table.class);
        SequenceGenerator sequence = OtpChallenge.class.getAnnotation(
                SequenceGenerator.class);

        assertThat(table.name()).isEqualTo("PAYMENT_OTP_CHALLENGE");
        assertThat(table.schema()).isEqualTo("SAFEPAY_OWNER");
        assertThat(sequence.sequenceName()).isEqualTo(
                "SAFEPAY_OWNER.SEQ_PAYMENT_OTP_CHALLENGE_ID");
        assertThat(sequence.allocationSize()).isEqualTo(1);
    }

    @Test
    void mapsCompositeOwnershipColumnsAsImmutableRelationships()
            throws Exception {

        JoinColumn transaction = field("transaction")
                .getAnnotation(JoinColumn.class);
        JoinColumn customer = field("customer")
                .getAnnotation(JoinColumn.class);

        assertThat(transaction.name()).isEqualTo("TRANSACTION_ID");
        assertThat(transaction.nullable()).isFalse();
        assertThat(transaction.updatable()).isFalse();
        assertThat(customer.name()).isEqualTo("CUSTOMER_USER_ID");
        assertThat(customer.nullable()).isFalse();
        assertThat(customer.updatable()).isFalse();
    }

    @Test
    void mapsHashExpiryAttemptsAndOptimisticVersionExactly()
            throws Exception {

        Column hash = field("otpHash").getAnnotation(Column.class);
        Column attempts = field("attemptCount")
                .getAnnotation(Column.class);
        Column expiry = field("expiresAt")
                .getAnnotation(Column.class);

        assertThat(hash.name()).isEqualTo("OTP_HASH");
        assertThat(hash.length()).isEqualTo(255);
        assertThat(attempts.name()).isEqualTo("ATTEMPT_COUNT");
        assertThat(attempts.precision()).isEqualTo(3);
        assertThat(expiry.name()).isEqualTo("EXPIRES_AT");
        assertThat(expiry.nullable()).isFalse();
        assertThat(field("versionNo").getAnnotation(Version.class))
                .isNotNull();
    }

    private static Field field(String name) throws Exception {
        return OtpChallenge.class.getDeclaredField(name);
    }
}
