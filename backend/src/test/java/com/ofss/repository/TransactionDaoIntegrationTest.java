package com.ofss.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.ofss.beans.Account;
import com.ofss.beans.Beneficiary;
import com.ofss.beans.TransactionDb;
import com.ofss.beans.TransactionState;
import com.ofss.beans.User;

@SpringBootTest
class TransactionDaoIntegrationTest {

    @Autowired
    private TransactionDao transactionDao;

    @Autowired
    private TransactionRiskFactorDao transactionRiskFactorDao;

    @Autowired
    private UserDao userDao;

    @Autowired
    private AccountDao accountDao;

    @Autowired
    private BeneficiaryDao beneficiaryDao;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Value("${spring.flyway.url}")
    private String ownerUrl;

    @Value("${spring.flyway.user}")
    private String ownerUsername;

    @Value("${spring.flyway.password}")
    private String ownerPassword;

    private Long firstCustomerId;
    private Long secondCustomerId;
    private Long sourceAccountId;
    private Long beneficiaryId;
    private String fixtureToken;

    @BeforeEach
    void createFixtures() throws SQLException {
        fixtureToken = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 20);

        try (Connection connection = ownerConnection()) {
            connection.setAutoCommit(false);

            try {
                firstCustomerId = nextSequenceValue(
                        connection,
                        "SAFEPAY_OWNER.SEQ_APP_USER_ID");

                secondCustomerId = nextSequenceValue(
                        connection,
                        "SAFEPAY_OWNER.SEQ_APP_USER_ID");

                sourceAccountId = nextSequenceValue(
                        connection,
                        "SAFEPAY_OWNER.SEQ_ACCOUNT_ID");

                beneficiaryId = nextSequenceValue(
                        connection,
                        "SAFEPAY_OWNER.SEQ_BENEFICIARY_ID");

                insertUser(connection, firstCustomerId, "first");
                insertUser(connection, secondCustomerId, "second");
                insertAccount(connection);
                insertBeneficiary(connection);

                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    @AfterEach
    void removeFixtures() throws SQLException {
        try (Connection connection = ownerConnection()) {
            connection.setAutoCommit(false);

            try {
                deleteTransactions(connection);
                deleteBeneficiary(connection);
                deleteAccount(connection);
                deleteUser(connection, firstCustomerId);
                deleteUser(connection, secondCustomerId);
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    @Test
    void filteredHistoryHasInclusiveStartExclusiveEndAndStableGlobalPaging() {
        OffsetDateTime from = OffsetDateTime.parse("2026-09-19T10:00:00Z");
        TransactionDb first = saveInstruction("FILTER1", new BigDecimal("100.00"), from);
        TransactionDb second = saveInstruction("FILTER2", new BigDecimal("100.00"), from);
        saveInstruction("FILTER3", new BigDecimal("100.00"), from.plusHours(1));
        var page = transactionDao.searchOwned(firstCustomerId, from, from.plusHours(1),
                TransactionState.CREATED, sourceAccountId, PageRequest.of(0, 1));
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).extracting(TransactionDb::getTransactionId)
                .containsExactly(second.getTransactionId());
        var next = transactionDao.searchOwned(firstCustomerId, from.withOffsetSameInstant(ZoneOffset.ofHoursMinutes(5, 30)),
                from.plusHours(1), TransactionState.CREATED, sourceAccountId, PageRequest.of(1, 1));
        assertThat(next.getTotalElements()).isEqualTo(2);
        assertThat(next.getContent()).extracting(TransactionDb::getTransactionId)
                .containsExactly(first.getTransactionId());
    }

    @Test
    void filteredHistoryNeverEscapesOwnerAndCombinesFiltersWithAnd() {
        OffsetDateTime from = OffsetDateTime.parse("2026-09-19T10:00:00Z");
        saveInstruction("PRIVATE_FILTER", new BigDecimal("100.00"), from);
        var page = PageRequest.of(0, 1);
        assertThat(transactionDao.searchOwned(secondCustomerId, null, null, null, sourceAccountId, page)).isEmpty();
        assertThat(transactionDao.searchOwned(firstCustomerId, null, null, null, Long.MAX_VALUE, page)).isEmpty();
        assertThat(transactionDao.searchOwned(firstCustomerId, null, null, TransactionState.SETTLED, null, page)).isEmpty();
        assertThat(transactionDao.searchOwned(firstCustomerId, from.plusSeconds(1), null, null, null, page)).isEmpty();
        assertThat(transactionDao.searchOwned(firstCustomerId, null, from, null, null, page)).isEmpty();
        assertThat(transactionDao.searchOwned(firstCustomerId, null, null, null, null, page).getTotalElements()).isEqualTo(1);
    }

    @Test
    void accountDirectoryUsesCustomerCurrentBalanceAndTypeFilters() throws SQLException {
        // Mutate only this test's isolated owner-created fixture, never V12 rows.
        try (Connection connection = ownerConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE SAFEPAY_OWNER.ACCOUNT
                        SET RESERVED_AMOUNT = 100, STATUS = 'INACTIVE'
                      WHERE ACCOUNT_ID = ? AND OWNER_USER_ID = ?
                     """)) {
            statement.setLong(1, sourceAccountId);
            statement.setLong(2, firstCustomerId);
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }
        var page = PageRequest.of(0, 1);
        var matches = accountDao.searchCustomerAccounts(firstCustomerId, new BigDecimal("1000000.00"),
                com.ofss.beans.AccountType.SAVINGS, page);
        assertThat(matches.getTotalElements()).isEqualTo(1);
        assertThat(matches.getContent()).extracting(Account::getAccountId).containsExactly(sourceAccountId);
        assertThat(matches.getContent().getFirst().getOwner().getFullName()).isEqualTo("Transaction first");
        assertThat(matches.getContent().getFirst().getAvailableBalance()).isEqualByComparingTo("999900.00");
        assertThat(matches.getContent().getFirst().getStatus()).isEqualTo(com.ofss.beans.AccountStatus.INACTIVE);
        assertThat(accountDao.searchCustomerAccounts(firstCustomerId, new BigDecimal("1000000.01"), null, page)).isEmpty();
        assertThat(accountDao.searchCustomerAccounts(firstCustomerId, null, com.ofss.beans.AccountType.CURRENT, page)).isEmpty();
        assertThat(accountDao.searchCustomerAccounts(secondCustomerId, null, null, page)).isEmpty();
        assertThat(accountDao.findCustomerAccountById(sourceAccountId)).isPresent();
    }

    @Test
    void auditQueriesHaveMatchingCountsAndFetchStoredDetailWithoutOwnerSubstitution() {
        OffsetDateTime from = OffsetDateTime.parse("2026-09-19T10:00:00Z");
        TransactionDb first = saveInstruction("AUDIT1", new BigDecimal("100000.01"), from);
        saveInstruction("AUDIT2", new BigDecimal("100.00"), from.plusMinutes(1));
        var page = transactionDao.searchForAudit(firstCustomerId, TransactionState.CREATED, from,
                from.plusHours(1), PageRequest.of(0, 1));
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(transactionDao.searchForAudit(secondCustomerId, null, null, null, PageRequest.of(0, 1))).isEmpty();
        assertThat(transactionDao.searchForAudit(firstCustomerId, TransactionState.SETTLED, null, null, PageRequest.of(0, 1))).isEmpty();
        assertThat(transactionDao.searchForAudit(firstCustomerId, null, null, from, PageRequest.of(0, 1))).isEmpty();
        var detail = transactionDao.findForAuditById(first.getTransactionId()).orElseThrow();
        assertThat(detail.getCategory()).isEqualTo(com.ofss.beans.PaymentCategory.MEDICAL);
        assertThat(detail.getCustomer().getUserId()).isEqualTo(firstCustomerId);
        assertThat(detail.getBeneficiary().getBeneficiaryId()).isEqualTo(beneficiaryId);
        assertThat(transactionRiskFactorDao.findAllForAuditByTransactionId(first.getTransactionId())).isEmpty();
        assertThat(transactionDao.findForAuditById(Long.MAX_VALUE)).isEmpty();
    }

    @Test
    void oracleCategoryConstraintsRejectInvalidMetadataButAllowHistoricalNull() throws SQLException {
        TransactionDb high = saveInstruction("CATEGORY_HIGH", new BigDecimal("100000.01"), OffsetDateTime.now(ZoneOffset.UTC));
        TransactionDb low = saveInstruction("CATEGORY_LOW", new BigDecimal("100000.00"), OffsetDateTime.now(ZoneOffset.UTC));
        try (Connection connection = ownerConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE SAFEPAY_OWNER.PAYMENT_TRANSACTION SET PAYMENT_CATEGORY = ?, PURPOSE = ?
                      WHERE TRANSACTION_ID = ? AND CUSTOMER_USER_ID = ?
                     """)) {
            connection.setAutoCommit(false);
            statement.setLong(3, high.getTransactionId());
            statement.setLong(4, firstCustomerId);
            for (String[] invalid : new String[][]{{"INVALID", "Reason"}, {"OTHERS", "   "},
                    {"OTHERS", null}, {"OTHERS", "x".repeat(141)}}) {
                statement.setString(1, invalid[0]);
                statement.setString(2, invalid[1]);
                org.assertj.core.api.Assertions.assertThatThrownBy(statement::executeUpdate)
                        .isInstanceOf(SQLException.class).satisfies(error ->
                                assertThat(((SQLException) error).getErrorCode()).isEqualTo(2290));
            }
            statement.setLong(3, low.getTransactionId());
            statement.setString(1, "MEDICAL");
            statement.setString(2, "Reason");
            org.assertj.core.api.Assertions.assertThatThrownBy(statement::executeUpdate).isInstanceOf(SQLException.class);
            statement.setLong(3, high.getTransactionId());
            statement.setString(1, "OTHERS");
            statement.setString(2, "x".repeat(140));
            assertThat(statement.executeUpdate()).isEqualTo(1);
            connection.rollback();
            // Leave only the new test fixture uncategorized, with its original purpose untouched.
            try (PreparedStatement legacy = connection.prepareStatement("""
                    UPDATE SAFEPAY_OWNER.PAYMENT_TRANSACTION SET PAYMENT_CATEGORY = NULL
                     WHERE TRANSACTION_ID = ? AND CUSTOMER_USER_ID = ?
                    """)) {
                legacy.setLong(1, high.getTransactionId());
                legacy.setLong(2, firstCustomerId);
                assertThat(legacy.executeUpdate()).isEqualTo(1);
            }
            connection.commit();
        }
        var legacy = transactionDao.findForAuditById(high.getTransactionId()).orElseThrow();
        assertThat(legacy.getCategory()).isNull();
        assertThat(legacy.getPurpose()).isEqualTo("Integration payment");
    }

    @Test
    void savesAndLoadsOwnedCreatedInstruction() {
        TransactionDb saved = saveInstruction(
                "ONE",
                new BigDecimal("5000.01"),
                OffsetDateTime.now(ZoneOffset.UTC));

        TransactionDb loaded = transactionDao.findOwnedById(
                saved.getTransactionId(),
                firstCustomerId).orElseThrow();

        assertThat(loaded.getTransactionId()).isPositive();
        assertThat(loaded.getTransactionReference())
                .isEqualTo(reference("ONE"));
        assertThat(loaded.getCustomer().getUserId())
                .isEqualTo(firstCustomerId);
        assertThat(loaded.getSourceAccount().getAccountId())
                .isEqualTo(sourceAccountId);
        assertThat(loaded.getBeneficiary().getBeneficiaryId())
                .isEqualTo(beneficiaryId);
        assertThat(loaded.getAmount())
                .isEqualByComparingTo("5000.01");
        assertThat(loaded.getState())
                .isEqualTo(TransactionState.CREATED);
        assertThat(loaded.getReservedAmount())
                .isEqualByComparingTo("0.00");
        assertThat(loaded.getRiskPolicy()).isNull();
        assertThat(transactionRiskFactorDao
                .findAllOwnedForTransaction(
                        saved.getTransactionId(),
                        firstCustomerId))
                .isEmpty();
    }

    @Test
    void hidesTransactionFromAnotherCustomer() {
        TransactionDb saved = saveInstruction(
                "PRIVATE",
                new BigDecimal("1.00"),
                OffsetDateTime.now(ZoneOffset.UTC));

        assertThat(transactionDao.findOwnedById(
                saved.getTransactionId(),
                secondCustomerId)).isEmpty();

        assertThat(transactionDao.findAllOwned(
                secondCustomerId,
                PageRequest.of(0, 10))).isEmpty();

        assertThat(transactionRiskFactorDao
                .findAllOwnedForTransaction(
                        saved.getTransactionId(),
                        secondCustomerId))
                .isEmpty();
    }

    @Test
    void listsOwnedTransactionsNewestFirstWithPaging() {
        OffsetDateTime baseTime = OffsetDateTime.now(ZoneOffset.UTC)
                .minusMinutes(5);

        TransactionDb older = saveInstruction(
                "OLDER",
                new BigDecimal("100.00"),
                baseTime);

        TransactionDb newer = saveInstruction(
                "NEWER",
                new BigDecimal("200.00"),
                baseTime.plusMinutes(1));

        Page<TransactionDb> page = transactionDao.findAllOwned(
                firstCustomerId,
                PageRequest.of(0, 1));

        assertThat(page.getTotalElements()).isEqualTo(2L);
        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(page.getContent())
                .extracting(TransactionDb::getTransactionId)
                .containsExactly(newer.getTransactionId());
        assertThat(page.getContent())
                .extracting(TransactionDb::getTransactionId)
                .doesNotContain(older.getTransactionId());
    }

    @Test
    void locksOnlyTheOwnedTransactionForMutation() {
        TransactionDb saved = saveInstruction(
                "LOCK",
                new BigDecimal("25000.01"),
                OffsetDateTime.now(ZoneOffset.UTC));

        TransactionTemplate template =
                new TransactionTemplate(transactionManager);

        Boolean foundForOwner = template.execute(status ->
                transactionDao.findOwnedByIdForUpdate(
                        saved.getTransactionId(),
                        firstCustomerId).isPresent());

        Boolean hiddenFromOther = template.execute(status ->
                transactionDao.findOwnedByIdForUpdate(
                        saved.getTransactionId(),
                        secondCustomerId).isEmpty());

        assertThat(foundForOwner).isTrue();
        assertThat(hiddenFromOther).isTrue();
    }

    @Test
    void resolvesStableUniqueTransactionReference() {
        TransactionDb saved = saveInstruction(
                "REFERENCE",
                new BigDecimal("100000.01"),
                OffsetDateTime.now(ZoneOffset.UTC));

        assertThat(transactionDao.findByTransactionReference(
                saved.getTransactionReference()))
                .hasValueSatisfying(found ->
                        assertThat(found.getTransactionId())
                                .isEqualTo(saved.getTransactionId()));
    }

    @Test
    void repositoriesExposeNoDeleteOperations() {
        Set<String> prohibitedMethods = Set.of(
                "delete",
                "deleteById",
                "deleteAll",
                "deleteAllById");

        for (Class<?> repositoryType : List.of(
                TransactionDao.class,
                TransactionRiskFactorDao.class)) {

            Set<String> exposedMethods = Arrays
                    .stream(repositoryType.getMethods())
                    .map(method -> method.getName())
                    .collect(Collectors.toSet());

            assertThat(exposedMethods)
                    .doesNotContainAnyElementsOf(prohibitedMethods);
        }
    }

    private TransactionDb saveInstruction(
            String suffix,
            BigDecimal amount,
            OffsetDateTime createdAt) {

        TransactionTemplate template =
                new TransactionTemplate(transactionManager);

        return template.execute(status -> {
            User customer = userDao.findById(firstCustomerId)
                    .orElseThrow();

            Account account = accountDao.findById(sourceAccountId)
                    .orElseThrow();

            Beneficiary beneficiary = beneficiaryDao
                    .findByBeneficiaryIdAndOwner_UserId(
                            beneficiaryId,
                            firstCustomerId)
                    .orElseThrow();

            return transactionDao.save(
                    TransactionDb.createPaymentInstruction(
                            reference(suffix),
                            customer,
                            account,
                            beneficiary,
                            amount,
                            "Integration payment",
                            "REF-" + suffix,
                            createdAt, com.ofss.beans.PaymentCategory.appliesTo(amount)
                                    ? com.ofss.beans.PaymentCategory.MEDICAL : null));
        });
    }

    private String reference(String suffix) {
        return "TXN-" + fixtureToken + "-" + suffix;
    }

    private Connection ownerConnection() throws SQLException {
        return DriverManager.getConnection(
                ownerUrl,
                ownerUsername,
                ownerPassword);
    }

    private static Long nextSequenceValue(
            Connection connection,
            String qualifiedSequenceName) throws SQLException {

        String sql = "SELECT "
                + qualifiedSequenceName
                + ".NEXTVAL FROM DUAL";

        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {

            if (!resultSet.next()) {
                throw new SQLException("Sequence returned no value");
            }

            return resultSet.getLong(1);
        }
    }

    private void insertUser(
            Connection connection,
            Long userId,
            String label) throws SQLException {

        try (PreparedStatement statement = connection.prepareStatement(
                """
                INSERT INTO SAFEPAY_OWNER.APP_USER
                    (USER_ID, FULL_NAME, EMAIL, PASSWORD_HASH)
                VALUES (?, ?, ?, ?)
                """)) {

            statement.setLong(1, userId);
            statement.setString(2, "Transaction " + label);
            statement.setString(
                    3,
                    "tx-" + label + "-" + fixtureToken
                            + "@example.invalid");
            statement.setString(
                    4,
                    "integration-test-password-hash");
            statement.executeUpdate();
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
            statement.setLong(2, firstCustomerId);
            statement.setString(3, "TX" + fixtureToken);
            statement.setString(4, "SafePay Transaction Bank");
            statement.setBigDecimal(
                    5,
                    new BigDecimal("1000000.00"));
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
            statement.setLong(2, firstCustomerId);
            statement.setString(3, "Integration Beneficiary");
            statement.setString(
                    4,
                    "tx." + fixtureToken + "@safepay");
            statement.executeUpdate();
        }
    }

    private void deleteTransactions(Connection connection)
            throws SQLException {

        executeDelete(
                connection,
                """
                DELETE FROM SAFEPAY_OWNER.PAYMENT_TRANSACTION
                WHERE CUSTOMER_USER_ID IN (?, ?)
                """,
                firstCustomerId,
                secondCustomerId);
    }

    private void deleteBeneficiary(Connection connection)
            throws SQLException {

        executeDelete(
                connection,
                """
                DELETE FROM SAFEPAY_OWNER.BENEFICIARY
                WHERE BENEFICIARY_ID = ?
                """,
                beneficiaryId);
    }

    private void deleteAccount(Connection connection)
            throws SQLException {

        executeDelete(
                connection,
                """
                DELETE FROM SAFEPAY_OWNER.ACCOUNT
                WHERE ACCOUNT_ID = ?
                """,
                sourceAccountId);
    }

    private static void deleteUser(
            Connection connection,
            Long userId) throws SQLException {

        executeDelete(
                connection,
                """
                DELETE FROM SAFEPAY_OWNER.APP_USER
                WHERE USER_ID = ?
                """,
                userId);
    }

    private static void executeDelete(
            Connection connection,
            String sql,
            Long... ids) throws SQLException {

        if (Arrays.stream(ids).anyMatch(id -> id == null)) {
            return;
        }

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            for (int index = 0; index < ids.length; index++) {
                statement.setLong(index + 1, ids[index]);
            }

            statement.executeUpdate();
        }
    }
}
