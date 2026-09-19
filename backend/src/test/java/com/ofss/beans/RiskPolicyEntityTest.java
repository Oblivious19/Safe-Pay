package com.ofss.beans;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;

import org.hibernate.annotations.Immutable;
import org.junit.jupiter.api.Test;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

class RiskPolicyEntityTest {

    @Test
    void definesCanonicalRiskTiersInAscendingOrder() {
        assertThat(RiskTier.values()).containsExactly(
                RiskTier.LOW,
                RiskTier.MEDIUM,
                RiskTier.HIGH,
                RiskTier.VERY_HIGH);
    }

    @Test
    void definesCanonicalPolicyStatuses() {
        assertThat(RiskPolicyStatus.values()).containsExactly(
                RiskPolicyStatus.DRAFT,
                RiskPolicyStatus.ACTIVE,
                RiskPolicyStatus.RETIRED);
    }

    @Test
    void permitsOnlyAmountOnlyAlgorithmInV1() {
        assertThat(RiskAlgorithmType.values())
                .containsExactly(
                        RiskAlgorithmType.AMOUNT_ONLY);
    }

    @Test
    void definesCanonicalProtectionReleaseModes() {
        assertThat(ProtectionReleaseMode.values())
                .containsExactly(
                        ProtectionReleaseMode.IMMEDIATE,
                        ProtectionReleaseMode.AFTER_TIMER,
                        ProtectionReleaseMode.AFTER_REVIEW);
    }

    @Test
    void mapsRiskPolicyAsImmutableDatabaseOwnedEntity()
            throws Exception {

        assertImmutableEntity(
                RiskPolicy.class,
                "RISK_POLICY");

        Field id = RiskPolicy.class.getDeclaredField(
                "riskPolicyId");

        assertThat(id.getAnnotation(Id.class)).isNotNull();
        assertThat(id.getAnnotation(GeneratedValue.class))
                .isNull();

        Column version = RiskPolicy.class
                .getDeclaredField("policyVersion")
                .getAnnotation(Column.class);

        assertThat(version.name())
                .isEqualTo("POLICY_VERSION");
        assertThat(version.length()).isEqualTo(50);
        assertThat(version.updatable()).isFalse();
    }

    @Test
    void mapsProtectionPolicyAsImmutableDatabaseOwnedEntity()
            throws Exception {

        assertImmutableEntity(
                ProtectionPolicy.class,
                "PROTECTION_POLICY");

        Field id = ProtectionPolicy.class.getDeclaredField(
                "protectionPolicyId");

        assertThat(id.getAnnotation(Id.class)).isNotNull();
        assertThat(id.getAnnotation(GeneratedValue.class))
                .isNull();

        Column seconds = ProtectionPolicy.class
                .getDeclaredField("protectionSeconds")
                .getAnnotation(Column.class);

        assertThat(seconds.precision()).isEqualTo(10);
        assertThat(seconds.scale()).isZero();
    }

    @Test
    void mapsRiskBandToOnePolicyAndProtectionAction()
            throws Exception {

        assertImmutableEntity(
                RiskPolicyBand.class,
                "RISK_POLICY_BAND");

        assertLazyRequiredRelationship(
                RiskPolicyBand.class.getDeclaredField(
                        "riskPolicy"),
                "RISK_POLICY_ID");

        assertLazyRequiredRelationship(
                RiskPolicyBand.class.getDeclaredField(
                        "protectionPolicy"),
                "PROTECTION_POLICY_ID");

        Column bandCode = RiskPolicyBand.class
                .getDeclaredField("bandCode")
                .getAnnotation(Column.class);

        assertThat(bandCode.length()).isEqualTo(50);

        Column minimum = RiskPolicyBand.class
                .getDeclaredField("minimumAmount")
                .getAnnotation(Column.class);

        assertThat(minimum.precision()).isEqualTo(18);
        assertThat(minimum.scale()).isEqualTo(2);
    }

    @Test
    void convertsCanonicalOracleFlagsWithoutExposingMutation()
            throws Exception {

        ProtectionPolicy policy = new ProtectionPolicy();

        setField(policy, "customerCanCancel", "Y");
        setField(policy, "autoRelease", "N");
        setField(policy, "otpRequired", "Y");
        setField(policy, "riskReviewRequired", "N");

        assertThat(policy.canCustomerCancel()).isTrue();
        assertThat(policy.isAutoRelease()).isFalse();
        assertThat(policy.isOtpRequired()).isTrue();
        assertThat(policy.isRiskReviewRequired()).isFalse();

        for (Class<?> entityType : Arrays.asList(
                RiskPolicy.class,
                ProtectionPolicy.class,
                RiskPolicyBand.class)) {

            assertThat(Arrays.stream(entityType.getMethods())
                    .filter(method -> method.getDeclaringClass()
                            == entityType)
                    .map(Method::getName))
                    .noneMatch(name -> name.startsWith("set"));
        }
    }

    private static void assertImmutableEntity(
            Class<?> entityType,
            String tableName) {

        assertThat(entityType.getAnnotation(Entity.class))
                .isNotNull();
        assertThat(entityType.getAnnotation(Immutable.class))
                .isNotNull();

        Table table = entityType.getAnnotation(Table.class);

        assertThat(table).isNotNull();
        assertThat(table.name()).isEqualTo(tableName);
        assertThat(table.schema()).isEqualTo("SAFEPAY_OWNER");
    }

    private static void assertLazyRequiredRelationship(
            Field field,
            String columnName) {

        ManyToOne relationship = field.getAnnotation(
                ManyToOne.class);

        assertThat(relationship).isNotNull();
        assertThat(relationship.fetch())
                .isEqualTo(FetchType.LAZY);
        assertThat(relationship.optional()).isFalse();

        JoinColumn joinColumn = field.getAnnotation(
                JoinColumn.class);

        assertThat(joinColumn).isNotNull();
        assertThat(joinColumn.name()).isEqualTo(columnName);
        assertThat(joinColumn.nullable()).isFalse();
        assertThat(joinColumn.updatable()).isFalse();
    }

    private static void setField(
            Object target,
            String fieldName,
            Object value)
            throws Exception {

        Field field = target.getClass()
                .getDeclaredField(fieldName);

        field.setAccessible(true);
        field.set(target, value);
    }
}
