package com.ofss.beans;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;

import org.junit.jupiter.api.Test;

import jakarta.persistence.Column;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

class RiskReviewMappingTest {

    @Test
    void mapsCanonicalPhysicalTableAndExistingSequence() {
        Table table = RiskReview.class.getAnnotation(Table.class);
        SequenceGenerator sequence = RiskReview.class.getAnnotation(
                SequenceGenerator.class);

        assertThat(table.name()).isEqualTo("RISK_REVIEW");
        assertThat(table.schema()).isEqualTo("SAFEPAY_OWNER");
        assertThat(table.uniqueConstraints()).singleElement()
                .satisfies(constraint -> {
                    assertThat(constraint.name())
                            .isEqualTo("UK_RISK_REVIEW_TX_ROUND");
                    assertThat(constraint.columnNames())
                            .containsExactly(
                                    "TRANSACTION_ID",
                                    "REVIEW_ROUND");
                });
        assertThat(sequence.sequenceName())
                .isEqualTo("SAFEPAY_OWNER.SEQ_RISK_REVIEW_ID");
        assertThat(sequence.allocationSize()).isEqualTo(1);
    }

    @Test
    void retainsStableApprovalIdAndV5ActorColumns()
            throws Exception {

        assertColumn("approvalId", "APPROVAL_ID", false);
        assertJoinColumn("transaction", "TRANSACTION_ID", false);
        assertColumn("reviewRound", "REVIEW_ROUND", false);
        assertJoinColumn("customer", "CUSTOMER_USER_ID", false);
        assertJoinColumn(
                "assignedRiskOfficer",
                "ASSIGNED_RISK_OFFICER_ID",
                true);
        assertJoinColumn("decidedByUser", "DECIDED_BY_USER_ID", true);
    }

    @Test
    void mapsOptimisticVersionAndBoundedDecisionReason()
            throws Exception {

        Field version = RiskReview.class.getDeclaredField("versionNo");
        assertThat(version.getAnnotation(Version.class)).isNotNull();
        assertThat(version.getAnnotation(Column.class).name())
                .isEqualTo("VERSION_NO");

        Column reason = RiskReview.class
                .getDeclaredField("decisionReason")
                .getAnnotation(Column.class);
        assertThat(reason.name()).isEqualTo("DECISION_REASON");
        assertThat(reason.length()).isEqualTo(1000);
    }

    private static void assertColumn(
            String fieldName,
            String columnName,
            boolean updatable) throws Exception {

        Column column = RiskReview.class
                .getDeclaredField(fieldName)
                .getAnnotation(Column.class);
        assertThat(column.name()).isEqualTo(columnName);
        assertThat(column.updatable()).isEqualTo(updatable);
    }

    private static void assertJoinColumn(
            String fieldName,
            String columnName,
            boolean updatable) throws Exception {

        JoinColumn column = RiskReview.class
                .getDeclaredField(fieldName)
                .getAnnotation(JoinColumn.class);
        assertThat(column.name()).isEqualTo(columnName);
        assertThat(column.updatable()).isEqualTo(updatable);
    }
}
