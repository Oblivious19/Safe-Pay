package com.ofss.beans;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

import org.hibernate.annotations.Immutable;
import org.junit.jupiter.api.Test;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

class TransactionEntityTest {

    private static final OffsetDateTime INPUT_TIME =
            OffsetDateTime.parse(
                    "2026-09-15T16:45:30.123456789+05:30");

    private static final OffsetDateTime UTC_TIME =
            OffsetDateTime.parse(
                    "2026-09-15T11:15:30.123456Z");

    @Test
    void definesCanonicalTransactionStatesInOrder() {
        assertThat(TransactionState.values()).containsExactly(
                TransactionState.CREATED,
                TransactionState.AUTHORIZED,
                TransactionState.RISK_ASSESSED,
                TransactionState.PROTECTED,
                TransactionState.VERIFICATION_REQUIRED,
                TransactionState.PENDING_RISK_REVIEW,
                TransactionState.RELEASED,
                TransactionState.SETTLED,
                TransactionState.CANCELLED,
                TransactionState.FAILED);
    }

    @Test
    void identifiesTerminalReservationAndCancellationStates() {
        assertThat(TransactionState.SETTLED.isTerminal()).isTrue();
        assertThat(TransactionState.CANCELLED.isTerminal()).isTrue();
        assertThat(TransactionState.FAILED.isTerminal()).isTrue();
        assertThat(TransactionState.RELEASED.isTerminal()).isFalse();

        assertThat(TransactionState.PROTECTED.holdsReservation())
                .isTrue();
        assertThat(TransactionState.VERIFICATION_REQUIRED
                .holdsReservation()).isTrue();
        assertThat(TransactionState.PENDING_RISK_REVIEW
                .holdsReservation()).isTrue();
        assertThat(TransactionState.RELEASED.holdsReservation())
                .isTrue();
        assertThat(TransactionState.CREATED.holdsReservation())
                .isFalse();

        assertThat(TransactionState.CREATED
                .allowsCustomerCancellation()).isTrue();
        assertThat(TransactionState.PROTECTED
                .allowsCustomerCancellation()).isTrue();
        assertThat(TransactionState.RELEASED
                .allowsCustomerCancellation()).isFalse();
    }

    @Test
    void definesOnlyCanonicalV1RiskFactorCode() {
        assertThat(TransactionRiskFactorCode.values())
                .containsExactly(
                        TransactionRiskFactorCode.PAYMENT_AMOUNT);
    }

    @Test
    void mapsTransactionToCanonicalTableAndSequence()
            throws Exception {

        assertThat(TransactionDb.class.getAnnotation(Entity.class))
                .isNotNull();

        Table table = TransactionDb.class.getAnnotation(Table.class);
        assertThat(table.name()).isEqualTo("PAYMENT_TRANSACTION");
        assertThat(table.schema()).isEqualTo("SAFEPAY_OWNER");

        SequenceGenerator sequence = TransactionDb.class
                .getAnnotation(SequenceGenerator.class);

        assertThat(sequence.name())
                .isEqualTo("paymentTransactionSequence");
        assertThat(sequence.sequenceName())
                .isEqualTo(
                        "SAFEPAY_OWNER.SEQ_PAYMENT_TRANSACTION_ID");
        assertThat(sequence.allocationSize()).isEqualTo(1);

        Field id = TransactionDb.class.getDeclaredField(
                "transactionId");

        assertThat(id.getAnnotation(GeneratedValue.class))
                .isNotNull();
    }

    @Test
    void mapsOwnershipRelationshipsAsRequiredAndImmutable()
            throws Exception {

        assertRelationship(
                "customer",
                "CUSTOMER_USER_ID",
                false);
        assertRelationship(
                "sourceAccount",
                "SOURCE_ACCOUNT_ID",
                false);
        assertRelationship(
                "beneficiary",
                "BENEFICIARY_ID",
                false);
    }

    @Test
    void mapsOptimisticVersionAndOracleMoneyPrecision()
            throws Exception {

        Field version = TransactionDb.class.getDeclaredField(
                "versionNo");

        assertThat(version.getAnnotation(Version.class)).isNotNull();

        Column versionColumn = version.getAnnotation(Column.class);
        assertThat(versionColumn.name()).isEqualTo("VERSION_NO");
        assertThat(versionColumn.precision()).isEqualTo(10);
        assertThat(versionColumn.scale()).isZero();

        assertMoneyColumn("amount", "AMOUNT");
        assertMoneyColumn("reservedAmount", "RESERVED_AMOUNT");
    }

    @Test
    void mapsTextualPolicyVersionAndCompleteLifecycle()
            throws Exception {

        Column policyVersion = TransactionDb.class
                .getDeclaredField("policyVersion")
                .getAnnotation(Column.class);

        assertThat(policyVersion.name())
                .isEqualTo("POLICY_VERSION");
        assertThat(policyVersion.length()).isEqualTo(50);

        assertColumn("riskAssessedAt", "RISK_ASSESSED_AT");
        assertColumn("authorizedAt", "AUTHORIZED_AT");
        assertColumn("protectedUntil", "PROTECTED_UNTIL");
        assertColumn(
                "verificationCompletedAt",
                "VERIFICATION_COMPLETED_AT");
        assertColumn("releasedAt", "RELEASED_AT");
        assertColumn("settledAt", "SETTLED_AT");
        assertColumn("cancelledAt", "CANCELLED_AT");
        assertColumn("failedAt", "FAILED_AT");
    }

    @Test
    void mapsCompleteNullableRiskSnapshotAndTextLimits()
            throws Exception {

        assertMutableOptionalRelationship(
                "riskPolicy",
                "RISK_POLICY_ID");
        assertMutableOptionalRelationship(
                "riskPolicyBand",
                "RISK_POLICY_BAND_ID");
        assertMutableOptionalRelationship(
                "protectionPolicy",
                "PROTECTION_POLICY_ID");

        Column reference = TransactionDb.class
                .getDeclaredField("transactionReference")
                .getAnnotation(Column.class);
        Column purpose = TransactionDb.class
                .getDeclaredField("purpose")
                .getAnnotation(Column.class);
        Column customerReference = TransactionDb.class
                .getDeclaredField("customerReference")
                .getAnnotation(Column.class);
        Column score = TransactionDb.class
                .getDeclaredField("riskScore")
                .getAnnotation(Column.class);
        Column bandCode = TransactionDb.class
                .getDeclaredField("matchedBandCode")
                .getAnnotation(Column.class);
        Column explanation = TransactionDb.class
                .getDeclaredField("riskExplanation")
                .getAnnotation(Column.class);

        assertThat(reference.length()).isEqualTo(64);
        assertThat(purpose.length()).isEqualTo(280);
        assertThat(customerReference.length()).isEqualTo(100);
        assertThat(score.precision()).isEqualTo(5);
        assertThat(score.scale()).isEqualTo(2);
        assertThat(bandCode.length()).isEqualTo(50);
        assertThat(explanation.length()).isEqualTo(1000);
    }

    @Test
    void createsOnlyAnUnassessedUnreservedInstruction() {
        RelatedEntities entities = relatedEntities(7L);

        TransactionDb transaction =
                TransactionDb.createPaymentInstruction(
                        "  TXN-2026-0001  ",
                        entities.customer(),
                        entities.account(),
                        entities.beneficiary(),
                        new BigDecimal("5000"),
                        "  Vendor payment  ",
                        "  INV-100  ",
                        INPUT_TIME);

        assertThat(transaction.getTransactionReference())
                .isEqualTo("TXN-2026-0001");
        assertThat(transaction.getAmount())
                .isEqualByComparingTo("5000.00");
        assertThat(transaction.getCurrencyCode())
                .isEqualTo(CurrencyCode.INR);
        assertThat(transaction.getPurpose())
                .isEqualTo("Vendor payment");
        assertThat(transaction.getCustomerReference())
                .isEqualTo("INV-100");
        assertThat(transaction.getState())
                .isEqualTo(TransactionState.CREATED);
        assertThat(transaction.getReservedAmount())
                .isEqualByComparingTo("0.00");
        assertThat(transaction.getRiskTier()).isNull();
        assertThat(transaction.getRiskPolicy()).isNull();
        assertThat(transaction.getRiskPolicyBand()).isNull();
        assertThat(transaction.getProtectionPolicy()).isNull();
        assertThat(transaction.getRiskAssessedAt()).isNull();
        assertThat(transaction.getCreatedAt()).isEqualTo(UTC_TIME);
        assertThat(transaction.getUpdatedAt()).isEqualTo(UTC_TIME);
    }

    @Test
    void normalizesBlankOptionalInstructionTextToNull() {
        RelatedEntities entities = relatedEntities(7L);

        TransactionDb transaction =
                TransactionDb.createPaymentInstruction(
                        "TXN-2026-0002",
                        entities.customer(),
                        entities.account(),
                        entities.beneficiary(),
                        new BigDecimal("1.00"),
                        "   ",
                        null,
                        INPUT_TIME);

        assertThat(transaction.getPurpose()).isNull();
        assertThat(transaction.getCustomerReference()).isNull();
    }

    @Test
    void rejectsInvalidInstructionAmounts() {
        RelatedEntities entities = relatedEntities(7L);

        for (String value : new String[] {
                "0.99",
                "5000.001",
                "10000000000000000.00"
        }) {
            assertThatThrownBy(() ->
                    TransactionDb.createPaymentInstruction(
                            "TXN-INVALID",
                            entities.customer(),
                            entities.account(),
                            entities.beneficiary(),
                            new BigDecimal(value),
                            null,
                            null,
                            INPUT_TIME))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void rejectsSourceAccountOwnedByAnotherCustomer() {
        RelatedEntities customerEntities = relatedEntities(7L);
        RelatedEntities otherEntities = relatedEntities(8L);

        assertThatThrownBy(() ->
                TransactionDb.createPaymentInstruction(
                        "TXN-WRONG-ACCOUNT",
                        customerEntities.customer(),
                        otherEntities.account(),
                        customerEntities.beneficiary(),
                        new BigDecimal("1.00"),
                        null,
                        null,
                        INPUT_TIME))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "sourceAccount must belong to customer");
    }

    @Test
    void rejectsBeneficiaryOwnedByAnotherCustomer() {
        RelatedEntities customerEntities = relatedEntities(7L);
        RelatedEntities otherEntities = relatedEntities(8L);

        assertThatThrownBy(() ->
                TransactionDb.createPaymentInstruction(
                        "TXN-WRONG-BENEFICIARY",
                        customerEntities.customer(),
                        customerEntities.account(),
                        otherEntities.beneficiary(),
                        new BigDecimal("1.00"),
                        null,
                        null,
                        INPUT_TIME))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "beneficiary must belong to customer");
    }

    @Test
    void categoryFactoryEnforcesThresholdAndDoesNotInventLegacyMetadata() {
        var entities = relatedEntities(11L);
        assertThatThrownBy(() -> TransactionDb.createPaymentInstruction("CATEGORY-MISSING",
                entities.customer(), entities.account(), entities.beneficiary(), new BigDecimal("100000.01"),
                "Original purpose", null, INPUT_TIME)).isInstanceOf(IllegalArgumentException.class);
        var payment = TransactionDb.createPaymentInstruction("CATEGORY-OTHERS", entities.customer(),
                entities.account(), entities.beneficiary(), new BigDecimal("100000.01"), "  Reason  ",
                null, INPUT_TIME, PaymentCategory.OTHERS);
        assertThat(payment.getCategory()).isEqualTo(PaymentCategory.OTHERS);
        assertThat(payment.getPurpose()).isEqualTo("Reason");
        assertThatThrownBy(() -> TransactionDb.createPaymentInstruction("CATEGORY-LOW", entities.customer(),
                entities.account(), entities.beneficiary(), new BigDecimal("100000.00"), null, null,
                INPUT_TIME, PaymentCategory.MEDICAL)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TransactionDb.createPaymentInstruction("CATEGORY-NOTE", entities.customer(),
                entities.account(), entities.beneficiary(), new BigDecimal("100000.01"), "   ", null,
                INPUT_TIME, PaymentCategory.OTHERS)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mapsRiskEvidenceAsImmutableAppendOnlyEntity()
            throws Exception {

        assertThat(TransactionRiskFactor.class
                .getAnnotation(Entity.class)).isNotNull();
        assertThat(TransactionRiskFactor.class
                .getAnnotation(Immutable.class)).isNotNull();

        Table table = TransactionRiskFactor.class
                .getAnnotation(Table.class);

        assertThat(table.name())
                .isEqualTo("TRANSACTION_RISK_FACTOR");
        assertThat(table.schema()).isEqualTo("SAFEPAY_OWNER");

        SequenceGenerator sequence = TransactionRiskFactor.class
                .getAnnotation(SequenceGenerator.class);

        assertThat(sequence.sequenceName())
                .isEqualTo(
                        "SAFEPAY_OWNER.SEQ_TX_RISK_FACTOR_ID");
        assertThat(sequence.allocationSize()).isEqualTo(1);

        assertRelationship(
                TransactionRiskFactor.class,
                "transaction",
                "TRANSACTION_ID",
                false);
        assertRelationship(
                TransactionRiskFactor.class,
                "riskPolicyBand",
                "RISK_POLICY_BAND_ID",
                false);
    }

    @Test
    void createsCoherentPaymentAmountEvidence()
            throws Exception {

        TransactionDb transaction = mock(TransactionDb.class);
        RiskPolicyBand band = mock(RiskPolicyBand.class);

        when(transaction.getTransactionId()).thenReturn(101L);
        when(transaction.getRiskPolicyBand()).thenReturn(band);
        when(transaction.getAmount())
                .thenReturn(new BigDecimal("25000.01"));
        when(transaction.getRiskTier()).thenReturn(RiskTier.HIGH);
        when(transaction.getRiskExplanation())
                .thenReturn("  Amount matched HIGH band.  ");
        when(transaction.getRiskAssessedAt())
                .thenReturn(INPUT_TIME);
        when(band.getRiskPolicyBandId()).thenReturn(202L);
        when(band.getRiskTier()).thenReturn(RiskTier.HIGH);

        TransactionRiskFactor factor =
                TransactionRiskFactor
                        .createPaymentAmountEvidence(
                                transaction,
                                band);

        assertThat(factor.getTransaction())
                .isSameAs(transaction);
        assertThat(factor.getRiskPolicyBand()).isSameAs(band);
        assertThat(factor.getFactorCode())
                .isEqualTo(
                        TransactionRiskFactorCode.PAYMENT_AMOUNT);
        assertThat(factor.getRawValue()).isEqualTo("25000.01");
        assertThat(factor.getResultingTier())
                .isEqualTo(RiskTier.HIGH);
        assertThat(factor.getExplanation())
                .isEqualTo("Amount matched HIGH band.");
        assertThat(factor.getEvaluatedAt()).isEqualTo(UTC_TIME);
    }

    @Test
    void rejectsRiskEvidenceThatDoesNotMatchSnapshot() {
        TransactionDb transaction = mock(TransactionDb.class);
        RiskPolicyBand transactionBand = mock(RiskPolicyBand.class);
        RiskPolicyBand differentBand = mock(RiskPolicyBand.class);

        when(transaction.getTransactionId()).thenReturn(101L);
        when(transaction.getRiskPolicyBand())
                .thenReturn(transactionBand);
        when(transaction.getRiskTier())
                .thenReturn(RiskTier.MEDIUM);
        when(transactionBand.getRiskPolicyBandId())
                .thenReturn(202L);
        when(differentBand.getRiskPolicyBandId())
                .thenReturn(303L);
        when(differentBand.getRiskTier())
                .thenReturn(RiskTier.MEDIUM);

        assertThatThrownBy(() -> TransactionRiskFactor
                .createPaymentAmountEvidence(
                        transaction,
                        differentBand))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "riskPolicyBand must match transaction snapshot");
    }

    @Test
    void rejectsRiskEvidenceWithDifferentResultingTier() {
        TransactionDb transaction = mock(TransactionDb.class);
        RiskPolicyBand band = mock(RiskPolicyBand.class);

        when(transaction.getTransactionId()).thenReturn(101L);
        when(transaction.getRiskTier()).thenReturn(RiskTier.HIGH);
        when(band.getRiskPolicyBandId()).thenReturn(202L);
        when(band.getRiskTier()).thenReturn(RiskTier.MEDIUM);

        assertThatThrownBy(() -> TransactionRiskFactor
                .createPaymentAmountEvidence(
                        transaction,
                        band))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "transaction riskTier must match riskPolicyBand");
    }

    private static RelatedEntities relatedEntities(Long ownerId) {
        User customer = mock(User.class);
        Account account = mock(Account.class);
        Beneficiary beneficiary = mock(Beneficiary.class);

        when(customer.getUserId()).thenReturn(ownerId);

        when(account.getAccountId())
                .thenReturn(ownerId * 10);
        when(account.isCustomerOwnedAccount()).thenReturn(true);
        when(account.getOwner()).thenReturn(customer);
        when(account.getCurrencyCode()).thenReturn(CurrencyCode.INR);

        when(beneficiary.getBeneficiaryId())
                .thenReturn(ownerId * 100);
        when(beneficiary.getOwner()).thenReturn(customer);

        return new RelatedEntities(
                customer,
                account,
                beneficiary);
    }

    private static void assertRelationship(
            String fieldName,
            String columnName,
            boolean optional) throws Exception {

        assertRelationship(
                TransactionDb.class,
                fieldName,
                columnName,
                optional);
    }

    private static void assertRelationship(
            Class<?> entityType,
            String fieldName,
            String columnName,
            boolean optional) throws Exception {

        Field field = entityType.getDeclaredField(fieldName);
        ManyToOne relationship = field.getAnnotation(ManyToOne.class);
        JoinColumn joinColumn = field.getAnnotation(JoinColumn.class);

        assertThat(relationship).isNotNull();
        assertThat(relationship.fetch()).isEqualTo(FetchType.LAZY);
        assertThat(relationship.optional()).isEqualTo(optional);
        assertThat(joinColumn.name()).isEqualTo(columnName);
        assertThat(joinColumn.nullable()).isEqualTo(optional);
        assertThat(joinColumn.updatable()).isFalse();
    }

    private static void assertMoneyColumn(
            String fieldName,
            String columnName) throws Exception {

        Column column = TransactionDb.class
                .getDeclaredField(fieldName)
                .getAnnotation(Column.class);

        assertThat(column.name()).isEqualTo(columnName);
        assertThat(column.precision()).isEqualTo(18);
        assertThat(column.scale()).isEqualTo(2);
    }

    private static void assertMutableOptionalRelationship(
            String fieldName,
            String columnName) throws Exception {

        Field field = TransactionDb.class.getDeclaredField(fieldName);
        ManyToOne relationship = field.getAnnotation(ManyToOne.class);
        JoinColumn joinColumn = field.getAnnotation(JoinColumn.class);

        assertThat(relationship).isNotNull();
        assertThat(relationship.fetch()).isEqualTo(FetchType.LAZY);
        assertThat(relationship.optional()).isTrue();
        assertThat(joinColumn.name()).isEqualTo(columnName);
        assertThat(joinColumn.nullable()).isTrue();
        assertThat(joinColumn.updatable()).isTrue();
    }

    private static void assertColumn(
            String fieldName,
            String columnName) throws Exception {

        Column column = TransactionDb.class
                .getDeclaredField(fieldName)
                .getAnnotation(Column.class);

        assertThat(column.name()).isEqualTo(columnName);
    }

    private record RelatedEntities(
            User customer,
            Account account,
            Beneficiary beneficiary) {
    }
}
