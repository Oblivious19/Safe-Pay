package com.ofss.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.test.context.transaction.BeforeTransaction;
import org.springframework.transaction.annotation.Transactional;

import com.ofss.beans.AuditLog;
import com.ofss.beans.RiskReview;
import com.ofss.beans.RiskReviewStatus;
import com.ofss.beans.TransactionState;
import com.ofss.dto.riskreview.RiskReviewDetailResponse;
import com.ofss.dto.riskreview.RiskReviewNoteResponse;
import com.ofss.dto.transaction.AuthorizeTransactionRequest;
import com.ofss.dto.transaction.CreateTransactionRequest;
import com.ofss.dto.transaction.TransactionResponse;
import com.ofss.excp.InvalidStateTransitionException;
import com.ofss.repository.AccountDao;
import com.ofss.repository.AuditLogDao;
import com.ofss.repository.RiskReviewDao;
import com.ofss.repository.TransactionDao;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;

@SpringBootTest(properties = {
        "safepay.protection-scheduler.enabled=false",
        "safepay.settlement-processor.enabled=false"
})
@Transactional
@Import(OtpServiceOracleIntegrationTest.OtpTestConfiguration.class)
class RiskReviewServiceOracleIntegrationTest {

    private static final String TEST_OTP = "123456";

    @Autowired private TransactionService transactionService;
    @Autowired private OtpService otpService;
    @Autowired private RiskReviewService riskReviewService;
    @Autowired private RiskReviewDao riskReviewDao;
    @Autowired private TransactionDao transactionDao;
    @Autowired private AccountDao accountDao;
    @Autowired private AuditLogDao auditLogDao;
    @Autowired private EntityManager entityManager;

    @Value("${spring.flyway.url}")
    private String ownerUrl;

    @Value("${spring.flyway.user}")
    private String ownerUsername;

    @Value("${spring.flyway.password}")
    private String ownerPassword;

    private Long customerId;
    private Long firstOfficerId;
    private Long secondOfficerId;
    private Long sourceAccountId;
    private Long beneficiaryId;
    private String fixtureToken;

    @BeforeTransaction
    void createOwnerFixtures() throws SQLException {
        fixtureToken = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 20);

        try (Connection connection = ownerConnection()) {
            connection.setAutoCommit(false);

            try {
                customerId = nextSequenceValue(
                        connection,
                        "SAFEPAY_OWNER.SEQ_APP_USER_ID");
                firstOfficerId = nextSequenceValue(
                        connection,
                        "SAFEPAY_OWNER.SEQ_APP_USER_ID");
                secondOfficerId = nextSequenceValue(
                        connection,
                        "SAFEPAY_OWNER.SEQ_APP_USER_ID");
                sourceAccountId = nextSequenceValue(
                        connection,
                        "SAFEPAY_OWNER.SEQ_ACCOUNT_ID");
                beneficiaryId = nextSequenceValue(
                        connection,
                        "SAFEPAY_OWNER.SEQ_BENEFICIARY_ID");

                insertUser(
                        connection,
                        customerId,
                        "Risk Review Customer",
                        "risk-review-" + fixtureToken
                                + "@example.invalid");
                insertUser(
                        connection,
                        firstOfficerId,
                        "First Risk Officer",
                        "risk-officer-1-" + fixtureToken
                                + "@example.invalid");
                insertUser(
                        connection,
                        secondOfficerId,
                        "Second Risk Officer",
                        "risk-officer-2-" + fixtureToken
                                + "@example.invalid");
                assignRiskOfficer(connection, firstOfficerId);
                assignRiskOfficer(connection, secondOfficerId);
                insertAccount(connection);
                insertBeneficiary(connection);
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    @AfterTransaction
    void removeOwnerFixtures() throws SQLException {
        try (Connection connection = ownerConnection()) {
            connection.setAutoCommit(false);

            try {
                executeDelete(
                        connection,
                        "DELETE FROM SAFEPAY_OWNER.BENEFICIARY "
                                + "WHERE BENEFICIARY_ID = ?",
                        beneficiaryId);
                executeDelete(
                        connection,
                        "DELETE FROM SAFEPAY_OWNER.ACCOUNT "
                                + "WHERE ACCOUNT_ID = ?",
                        sourceAccountId);
                executeDelete(
                        connection,
                        "DELETE FROM SAFEPAY_OWNER.USER_ROLE "
                                + "WHERE USER_ID = ?",
                        firstOfficerId);
                executeDelete(
                        connection,
                        "DELETE FROM SAFEPAY_OWNER.USER_ROLE "
                                + "WHERE USER_ID = ?",
                        secondOfficerId);
                executeDelete(
                        connection,
                        "DELETE FROM SAFEPAY_OWNER.APP_USER "
                                + "WHERE USER_ID = ?",
                        customerId);
                executeDelete(
                        connection,
                        "DELETE FROM SAFEPAY_OWNER.APP_USER "
                                + "WHERE USER_ID = ?",
                        firstOfficerId);
                executeDelete(
                        connection,
                        "DELETE FROM SAFEPAY_OWNER.APP_USER "
                                + "WHERE USER_ID = ?",
                        secondOfficerId);
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    @Test
    void approvalProducesOneWinnerAndCompleteAuditEvidence() {
        ReviewFixture fixture = readyReview();

        RiskReviewDetailResponse approved = riskReviewService.approve(
                firstOfficerId,
                fixture.reviewId(),
                "Verified beneficiary and payment context",
                correlation("approve"),
                idempotency("approve"));

        assertThat(approved.review().status())
                .isEqualTo(RiskReviewStatus.APPROVED);
        assertThat(approved.review().transactionState())
                .isEqualTo(TransactionState.RELEASED);
        assertThat(approved.decidedByUserId())
                .isEqualTo(firstOfficerId.toString());

        assertThatThrownBy(() -> riskReviewService.reject(
                secondOfficerId,
                fixture.reviewId(),
                "Competing decision",
                correlation("loser"),
                idempotency("loser")))
                .isInstanceOf(InvalidStateTransitionException.class);

        List<AuditLog> events = auditLogDao
                .findAllByTransaction_TransactionIdOrderByOccurredAtAsc(
                        fixture.transactionId());

        assertThat(events)
                .filteredOn(event -> "RISK_REVIEW_APPROVED"
                        .equals(event.getActionCode()))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getActorUser().getUserId())
                            .isEqualTo(firstOfficerId);
                    assertThat(event.getActorRoleCode())
                            .isEqualTo("RISK_OFFICER");
                    assertThat(event.getEntityId())
                            .isEqualTo(fixture.reviewId());
                    assertThat(event.getPreviousState())
                            .isEqualTo("PENDING_RISK_REVIEW");
                    assertThat(event.getNewState())
                            .isEqualTo("RELEASED");
                    assertThat(event.getCorrelationId())
                            .isEqualTo(correlation("approve"));
                    assertThat(event.getIdempotencyKey())
                            .isEqualTo(idempotency("approve"));
                });
    }

    @Test
    void rejectionCancelsPaymentReleasesReservationAndAuditsReason() {
        ReviewFixture fixture = readyReview();

        RiskReviewDetailResponse rejected = riskReviewService.reject(
                firstOfficerId,
                fixture.reviewId(),
                "Beneficiary evidence is inconsistent",
                correlation("reject"),
                idempotency("reject"));

        assertThat(rejected.review().status())
                .isEqualTo(RiskReviewStatus.REJECTED);
        assertThat(rejected.review().transactionState())
                .isEqualTo(TransactionState.CANCELLED);
        assertThat(transactionDao
                .findByIdForUpdate(fixture.transactionId())
                .orElseThrow()
                .getReservedAmount())
                .isEqualByComparingTo("0.00");
        assertThat(accountDao.findById(sourceAccountId)
                .orElseThrow()
                .getReservedAmount())
                .isEqualByComparingTo("0.00");

        assertThat(auditLogDao
                .findAllByTransaction_TransactionIdOrderByOccurredAtAsc(
                        fixture.transactionId()))
                .filteredOn(event -> "RISK_REVIEW_REJECTED"
                        .equals(event.getActionCode()))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getReasonCode())
                            .isEqualTo("REJECTED");
                    assertThat(event.getDetailsJson())
                            .contains("Beneficiary evidence is inconsistent");
                });
    }

    @Test
    void reverificationPreservesFirstRoundAndCreatesRoundTwoAfterNewOtp() {
        ReviewFixture first = readyReview();

        riskReviewService.requestReverification(
                firstOfficerId,
                first.reviewId(),
                "Customer must confirm the payment again",
                correlation("reverify"),
                idempotency("reverify"));

        assertThat(transactionDao
                .findByIdForUpdate(first.transactionId())
                .orElseThrow()
                .getState())
                .isEqualTo(TransactionState.VERIFICATION_REQUIRED);

        var issued = otpService.issue(
                customerId,
                first.transactionId());
        otpService.verify(
                customerId,
                first.transactionId(),
                Long.valueOf(issued.response().challengeId()),
                TEST_OTP);

        RiskReview prior = riskReviewDao
                .findByApprovalId(first.reviewId())
                .orElseThrow();
        RiskReview latest = riskReviewDao
                .findFirstByTransaction_TransactionIdOrderByReviewRoundDesc(
                        first.transactionId())
                .orElseThrow();

        assertThat(prior.getStatus())
                .isEqualTo(RiskReviewStatus.REVERIFICATION_REQUESTED);
        assertThat(prior.getReviewRound()).isEqualTo(1);
        assertThat(latest.getApprovalId())
                .isNotEqualTo(prior.getApprovalId());
        assertThat(latest.getReviewRound()).isEqualTo(2);
        assertThat(latest.getStatus())
                .isEqualTo(RiskReviewStatus.PENDING);
    }

    @Test
    void internalNoteIsAppendOnlyAuditEvidenceAndDoesNotDecideReview() {
        ReviewFixture fixture = readyReview();

        RiskReviewNoteResponse response = riskReviewService.addNote(
                firstOfficerId,
                fixture.reviewId(),
                "Callback completed; awaiting independent verification.",
                correlation("note"),
                idempotency("note"));

        RiskReview unchanged = riskReviewDao
                .findByApprovalId(fixture.reviewId())
                .orElseThrow();
        assertThat(unchanged.getStatus())
                .isEqualTo(RiskReviewStatus.PENDING);
        assertThat(unchanged.getDecisionReason()).isNull();
        assertThat(response.eventReference())
                .startsWith("RISK-REVIEW-NOTE-");

        assertThat(auditLogDao
                .findAllByTransaction_TransactionIdOrderByOccurredAtAsc(
                        fixture.transactionId()))
                .filteredOn(event -> "RISK_REVIEW_NOTE_ADDED"
                        .equals(event.getActionCode()))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getPreviousState())
                            .isEqualTo("PENDING_RISK_REVIEW");
                    assertThat(event.getNewState())
                            .isEqualTo("PENDING_RISK_REVIEW");
                    assertThat(event.getDetailsJson())
                            .contains("awaiting independent verification");
                });
    }

    @Test
    void oracleTriggerRejectsMutationOfCompletedReview() {
        ReviewFixture fixture = readyReview();
        riskReviewService.approve(
                firstOfficerId,
                fixture.reviewId(),
                null,
                correlation("trigger"),
                idempotency("trigger"));
        entityManager.flush();
        entityManager.clear();

        assertThatThrownBy(() -> entityManager
                .createNativeQuery("""
                        UPDATE SAFEPAY_OWNER.RISK_REVIEW
                           SET DECISION_REASON = 'ILLEGAL MUTATION'
                         WHERE APPROVAL_ID = :reviewId
                        """)
                .setParameter("reviewId", fixture.reviewId())
                .executeUpdate())
                .isInstanceOf(PersistenceException.class)
                .hasStackTraceContaining("ORA-20045");
    }

    @Test
    void categoryPriorityIsGlobalAcrossPagesAndFifoIncludesLegacyRows() {
        // Increase only this test's isolated fixture capacity for six pending reservations.
        entityManager.createNativeQuery("UPDATE SAFEPAY_OWNER.ACCOUNT SET CURRENT_BALANCE = 1000000 WHERE ACCOUNT_ID = :id")
                .setParameter("id", sourceAccountId).executeUpdate();
        entityManager.clear();
        ReviewFixture legacy = readyReview();
        entityManager.flush();
        entityManager.createNativeQuery("UPDATE SAFEPAY_OWNER.PAYMENT_TRANSACTION SET PAYMENT_CATEGORY = NULL WHERE TRANSACTION_ID = :id")
                .setParameter("id", legacy.transactionId()).executeUpdate();
        entityManager.clear();
        java.util.List<Long> fixtureIds = new java.util.ArrayList<>();
        fixtureIds.add(legacy.reviewId());
        for (var category : java.util.List.of(com.ofss.beans.PaymentCategory.OTHERS,
                com.ofss.beans.PaymentCategory.INVESTMENTS, com.ofss.beans.PaymentCategory.FRIENDS_FAMILY,
                com.ofss.beans.PaymentCategory.LOAN, com.ofss.beans.PaymentCategory.MEDICAL)) {
            fixtureIds.add(readyReview(category).reviewId());
        }
        java.util.List<RiskReview> ordered = new java.util.ArrayList<>();
        org.springframework.data.domain.Page<RiskReview> page;
        int index = 0;
        do {
            page = riskReviewDao.searchPending(null, true, org.springframework.data.domain.PageRequest.of(index++, 2));
            ordered.addAll(page.getContent());
        } while (!page.isLast());
        assertThat(ordered).hasSize((int) page.getTotalElements());
        assertThat(ordered.stream().map(review -> review.getTransaction().getCategory())
                .map(category -> category == null ? 6 : category.ordinal() + 1).toList()).isSorted();
        assertThat(ordered.stream().map(RiskReview::getApprovalId).filter(fixtureIds::contains).toList())
                .containsExactly(fixtureIds.get(5), fixtureIds.get(4), fixtureIds.get(3), fixtureIds.get(2), fixtureIds.get(1), fixtureIds.get(0));
        java.util.List<Long> oldestIds = new java.util.ArrayList<>();
        index = 0;
        do {
            page = riskReviewDao.searchPending(null, false, org.springframework.data.domain.PageRequest.of(index++, 2));
            oldestIds.addAll(page.getContent().stream().map(RiskReview::getApprovalId).filter(fixtureIds::contains).toList());
        } while (!page.isLast());
        assertThat(oldestIds)
                .containsExactlyElementsOf(fixtureIds);
        var medical = riskReviewDao.searchPending(com.ofss.beans.PaymentCategory.MEDICAL, true,
                org.springframework.data.domain.PageRequest.of(0, 1));
        assertThat(medical.getTotalElements()).isPositive();
        assertThat(medical.getContent()).allSatisfy(review ->
                assertThat(review.getTransaction().getCategory()).isEqualTo(com.ofss.beans.PaymentCategory.MEDICAL));
    }

    @Test
    void legacyNullCategoryAndOriginalPurposeRemainReviewable() {
        ReviewFixture fixture = readyReview();
        entityManager.flush();
        entityManager.createNativeQuery("UPDATE SAFEPAY_OWNER.PAYMENT_TRANSACTION SET PAYMENT_CATEGORY = NULL WHERE TRANSACTION_ID = :id")
                .setParameter("id", fixture.transactionId()).executeUpdate();
        entityManager.clear();
        var approved = riskReviewService.approve(firstOfficerId, fixture.reviewId(), null,
                correlation("legacy"), idempotency("legacy"));
        assertThat(approved.review().category()).isNull();
        assertThat(approved.review().purpose()).isEqualTo("Risk Review Oracle integration payment");
        assertThat(approved.review().transactionState()).isEqualTo(TransactionState.RELEASED);
    }

    private ReviewFixture readyReview() {
        return readyReview(com.ofss.beans.PaymentCategory.MEDICAL);
    }

    private ReviewFixture readyReview(com.ofss.beans.PaymentCategory category) {
        TransactionResponse created =
                transactionService.createTransaction(
                        customerId,
                        new CreateTransactionRequest(
                                sourceAccountId,
                                beneficiaryId,
                                new BigDecimal("100000.01"),
                                "Risk Review Oracle integration payment",
                                "RR-ORACLE-" + UUID.randomUUID()
                                        .toString()
                                        .substring(0, 8), category));

        TransactionResponse authorized =
                transactionService.authorizeTransaction(
                        customerId,
                        Long.valueOf(created.transactionId()),
                        new AuthorizeTransactionRequest(true));

        assertThat(authorized.state())
                .isEqualTo(TransactionState.VERIFICATION_REQUIRED);

        var issued = otpService.issue(
                customerId,
                Long.valueOf(authorized.transactionId()));
        otpService.verify(
                customerId,
                Long.valueOf(authorized.transactionId()),
                Long.valueOf(issued.response().challengeId()),
                TEST_OTP);

        RiskReview review = riskReviewDao
                .findFirstByTransaction_TransactionIdOrderByReviewRoundDesc(
                        Long.valueOf(authorized.transactionId()))
                .orElseThrow();

        return new ReviewFixture(
                Long.valueOf(authorized.transactionId()),
                review.getApprovalId());
    }

    private String correlation(String suffix) {
        return "rr-correlation-" + suffix + "-" + fixtureToken;
    }

    private String idempotency(String suffix) {
        return "rr-idempotency-" + suffix + "-" + fixtureToken;
    }

    private Connection ownerConnection() throws SQLException {
        return DriverManager.getConnection(
                ownerUrl,
                ownerUsername,
                ownerPassword);
    }

    private static Long nextSequenceValue(
            Connection connection,
            String sequenceName) throws SQLException {

        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT " + sequenceName + ".NEXTVAL FROM DUAL")) {

            if (!resultSet.next()) {
                throw new SQLException("Sequence returned no value");
            }

            return resultSet.getLong(1);
        }
    }

    private static void insertUser(
            Connection connection,
            Long userId,
            String name,
            String email) throws SQLException {

        try (PreparedStatement statement = connection.prepareStatement(
                """
                INSERT INTO SAFEPAY_OWNER.APP_USER
                    (USER_ID, FULL_NAME, EMAIL, PASSWORD_HASH)
                VALUES (?, ?, ?, ?)
                """)) {

            statement.setLong(1, userId);
            statement.setString(2, name);
            statement.setString(3, email);
            statement.setString(4, "integration-test-password-hash");
            statement.executeUpdate();
        }
    }

    private static void assignRiskOfficer(
            Connection connection,
            Long userId) throws SQLException {

        try (PreparedStatement statement = connection.prepareStatement(
                """
                INSERT INTO SAFEPAY_OWNER.USER_ROLE
                    (USER_ID, ROLE_ID)
                SELECT ?, ROLE_ID
                  FROM SAFEPAY_OWNER.APP_ROLE
                 WHERE ROLE_CODE = 'RISK_OFFICER'
                """)) {

            statement.setLong(1, userId);
            if (statement.executeUpdate() != 1) {
                throw new SQLException(
                        "Canonical RISK_OFFICER role was not found");
            }
        }
    }

    private void insertAccount(Connection connection)
            throws SQLException {

        try (PreparedStatement statement = connection.prepareStatement(
                """
                INSERT INTO SAFEPAY_OWNER.ACCOUNT
                    (ACCOUNT_ID, OWNER_USER_ID, ACCOUNT_NUMBER,
                     ACCOUNT_TYPE, BANK_NAME, IFSC_CODE,
                     CURRENT_BALANCE)
                VALUES (?, ?, ?, 'SAVINGS', ?, 'HDFC0000001', ?)
                """)) {

            statement.setLong(1, sourceAccountId);
            statement.setLong(2, customerId);
            statement.setString(3, "RR" + fixtureToken);
            statement.setString(4, "SafePay Risk Review Bank");
            statement.setBigDecimal(
                    5,
                    new BigDecimal("500000.00"));
            statement.executeUpdate();
        }
    }

    private void insertBeneficiary(Connection connection)
            throws SQLException {

        try (PreparedStatement statement = connection.prepareStatement(
                """
                INSERT INTO SAFEPAY_OWNER.BENEFICIARY
                    (BENEFICIARY_ID, OWNER_USER_ID,
                     BENEFICIARY_NAME, PAYMENT_METHOD, UPI_ID)
                VALUES (?, ?, ?, 'UPI', ?)
                """)) {

            statement.setLong(1, beneficiaryId);
            statement.setLong(2, customerId);
            statement.setString(3, "Risk Review Beneficiary");
            statement.setString(
                    4,
                    "risk.review." + fixtureToken + "@safepay");
            statement.executeUpdate();
        }
    }

    private static void executeDelete(
            Connection connection,
            String sql,
            Long id) throws SQLException {

        if (id == null) {
            return;
        }

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            statement.executeUpdate();
        }
    }

    private record ReviewFixture(
            Long transactionId,
            Long reviewId) {
    }
}
