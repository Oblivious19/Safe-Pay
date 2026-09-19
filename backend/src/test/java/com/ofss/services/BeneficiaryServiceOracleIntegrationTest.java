package com.ofss.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.ofss.beans.BeneficiaryPaymentMethod;
import com.ofss.beans.BeneficiaryStatus;
import com.ofss.dto.beneficiary.BeneficiaryResponse;
import com.ofss.dto.beneficiary.CreateBeneficiaryRequest;
import com.ofss.dto.beneficiary.UpdateBeneficiaryStatusRequest;
import com.ofss.excp.BusinessRuleException;
import com.ofss.excp.DuplicateResourceExcp;
import com.ofss.excp.ResourceNotFoundExcp;

@SpringBootTest
class BeneficiaryServiceOracleIntegrationTest {

    @Autowired
    private BeneficiaryService beneficiaryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${spring.flyway.url}")
    private String ownerUrl;

    @Value("${spring.flyway.user}")
    private String ownerUsername;

    @Value("${spring.flyway.password}")
    private String ownerPassword;

    private Long firstOwnerId;
    private Long secondOwnerId;
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
                firstOwnerId = nextSequenceValue(
                        connection,
                        "SAFEPAY_OWNER.SEQ_APP_USER_ID");

                secondOwnerId = nextSequenceValue(
                        connection,
                        "SAFEPAY_OWNER.SEQ_APP_USER_ID");

                insertFixtureUser(
                        connection,
                        firstOwnerId,
                        "first");

                insertFixtureUser(
                        connection,
                        secondOwnerId,
                        "second");

                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    @AfterEach
    void removeFixtures() throws SQLException {
        if (firstOwnerId == null && secondOwnerId == null) {
            return;
        }

        try (Connection connection = ownerConnection()) {
            connection.setAutoCommit(false);

            try {
                deleteBeneficiaries(connection, firstOwnerId);
                deleteBeneficiaries(connection, secondOwnerId);
                deleteUser(connection, firstOwnerId);
                deleteUser(connection, secondOwnerId);
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    @Test
    void createsRetrievesAndListsBankBeneficiary() {
        BeneficiaryResponse created = beneficiaryService
                .createOwnedBeneficiary(
                        firstOwnerId,
                        bankRequest());

        assertThat(created.beneficiaryId()).isNotBlank();
        assertThat(created.maskedDestinationIdentifier())
                .isEqualTo("********9012");
        assertThat(created.status())
                .isEqualTo(BeneficiaryStatus.ACTIVE);

        BeneficiaryResponse retrieved = beneficiaryService
                .getOwnedBeneficiary(
                        firstOwnerId,
                        Long.valueOf(created.beneficiaryId()));

        assertThat(retrieved).isEqualTo(created);
        assertThat(beneficiaryService
                .listOwnedBeneficiaries(firstOwnerId))
                .containsExactly(created);

        Integer storedRows = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM SAFEPAY_OWNER.BENEFICIARY
                WHERE BENEFICIARY_ID = ?
                  AND OWNER_USER_ID = ?
                  AND BANK_ACCOUNT_NUMBER = ?
                  AND IFSC_CODE = ?
                  AND STATUS = 'ACTIVE'
                """,
                Integer.class,
                Long.valueOf(created.beneficiaryId()),
                firstOwnerId,
                "123456789012",
                "ABCD0123456");

        assertThat(storedRows).isEqualTo(1);
    }

    @Test
    void createsNormalizedUpiBeneficiaryWithoutLeakingIdentifier() {
        BeneficiaryResponse created = beneficiaryService
                .createOwnedBeneficiary(
                        firstOwnerId,
                        upiRequest());

        String normalizedUpi = upiIdentifier();

        assertThat(created.paymentMethod())
                .isEqualTo(BeneficiaryPaymentMethod.UPI);
        assertThat(created.maskedDestinationIdentifier())
                .isNotEqualTo(normalizedUpi)
                .endsWith("@safepay");

        String storedUpi = jdbcTemplate.queryForObject(
                """
                SELECT UPI_ID
                FROM SAFEPAY_OWNER.BENEFICIARY
                WHERE BENEFICIARY_ID = ?
                """,
                String.class,
                Long.valueOf(created.beneficiaryId()));

        assertThat(storedUpi).isEqualTo(normalizedUpi);
    }

    @Test
    void rejectsDuplicateOwnedUpiBeneficiary() {
        beneficiaryService.createOwnedBeneficiary(
                firstOwnerId,
                upiRequest());

        assertThatThrownBy(() -> beneficiaryService
                .createOwnedBeneficiary(
                        firstOwnerId,
                        upiRequest()))
                .isInstanceOf(DuplicateResourceExcp.class)
                .satisfies(exception -> {
                    DuplicateResourceExcp conflict =
                            (DuplicateResourceExcp) exception;

                    assertThat(conflict.getErrorCode())
                            .isEqualTo(
                                    "BENEFICIARY_ALREADY_EXISTS");
                });

        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM SAFEPAY_OWNER.BENEFICIARY
                WHERE OWNER_USER_ID = ?
                  AND UPI_ID = ?
                """,
                Integer.class,
                firstOwnerId,
                upiIdentifier());

        assertThat(count).isEqualTo(1);
    }

    @Test
    void hidesBeneficiaryFromDifferentOwner() {
        BeneficiaryResponse created = beneficiaryService
                .createOwnedBeneficiary(
                        firstOwnerId,
                        bankRequest());

        Long beneficiaryId = Long.valueOf(
                created.beneficiaryId());

        assertThatThrownBy(() -> beneficiaryService
                .getOwnedBeneficiary(
                        secondOwnerId,
                        beneficiaryId))
                .isInstanceOf(ResourceNotFoundExcp.class)
                .satisfies(exception -> {
                    ResourceNotFoundExcp notFound =
                            (ResourceNotFoundExcp) exception;

                    assertThat(notFound.getErrorCode())
                            .isEqualTo("BENEFICIARY_NOT_FOUND");
                });

        assertThat(beneficiaryService
                .listOwnedBeneficiaries(secondOwnerId))
                .isEmpty();
    }

    @Test
    void disablesAndReEnablesPaymentEligibility() {
        BeneficiaryResponse created = beneficiaryService
                .createOwnedBeneficiary(
                        firstOwnerId,
                        upiRequest());

        Long beneficiaryId = Long.valueOf(
                created.beneficiaryId());

        BeneficiaryResponse disabled = beneficiaryService
                .updateOwnedBeneficiaryStatus(
                        firstOwnerId,
                        beneficiaryId,
                        new UpdateBeneficiaryStatusRequest(
                                BeneficiaryStatus.DISABLED));

        assertThat(disabled.status())
                .isEqualTo(BeneficiaryStatus.DISABLED);

        assertThatThrownBy(() -> beneficiaryService
                .getRequiredActiveOwnedBeneficiary(
                        firstOwnerId,
                        beneficiaryId))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(exception -> {
                    BusinessRuleException ruleFailure =
                            (BusinessRuleException) exception;

                    assertThat(ruleFailure.getErrorCode())
                            .isEqualTo("BENEFICIARY_DISABLED");
                });

        BeneficiaryResponse enabled = beneficiaryService
                .updateOwnedBeneficiaryStatus(
                        firstOwnerId,
                        beneficiaryId,
                        new UpdateBeneficiaryStatusRequest(
                                BeneficiaryStatus.ACTIVE));

        assertThat(enabled.status())
                .isEqualTo(BeneficiaryStatus.ACTIVE);

        assertThat(beneficiaryService
                .getRequiredActiveOwnedBeneficiary(
                        firstOwnerId,
                        beneficiaryId)
                .getBeneficiaryId())
                .isEqualTo(beneficiaryId);
    }

    private CreateBeneficiaryRequest bankRequest() {
        return new CreateBeneficiaryRequest(
                "Integration Supplier",
                "Oracle Bank",
                BeneficiaryPaymentMethod.BANK_ACCOUNT,
                "SafePay Integration Bank",
                "123456789012",
                "ABCD0123456",
                null,
                "Supplier",
                "Integration verification");
    }

    private CreateBeneficiaryRequest upiRequest() {
        return new CreateBeneficiaryRequest(
                "Integration Merchant",
                null,
                BeneficiaryPaymentMethod.UPI,
                null,
                null,
                null,
                "  " + upiIdentifier().toUpperCase() + "  ",
                null,
                null);
    }

    private String upiIdentifier() {
        return "merchant." + fixtureToken + "@safepay";
    }

    private Connection ownerConnection()
            throws SQLException {

        return DriverManager.getConnection(
                ownerUrl,
                ownerUsername,
                ownerPassword);
    }

    private static Long nextSequenceValue(
            Connection connection,
            String qualifiedSequenceName)
            throws SQLException {

        String sql = "SELECT "
                + qualifiedSequenceName
                + ".NEXTVAL FROM DUAL";

        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {

            if (!resultSet.next()) {
                throw new SQLException(
                        "Sequence returned no value");
            }

            return resultSet.getLong(1);
        }
    }

    private void insertFixtureUser(
            Connection connection,
            Long userId,
            String label)
            throws SQLException {

        try (PreparedStatement statement =
                     connection.prepareStatement(
                             """
                             INSERT INTO SAFEPAY_OWNER.APP_USER
                             (
                                 USER_ID,
                                 FULL_NAME,
                                 EMAIL,
                                 PASSWORD_HASH
                             )
                             VALUES (?, ?, ?, ?)
                             """)) {

            statement.setLong(1, userId);
            statement.setString(
                    2,
                    "SafePay Beneficiary " + label);
            statement.setString(
                    3,
                    "ben-" + label + "-" + fixtureToken
                            + "@example.invalid");
            statement.setString(
                    4,
                    "integration-test-password-hash");
            statement.executeUpdate();
        }
    }

    private static void deleteBeneficiaries(
            Connection connection,
            Long ownerUserId)
            throws SQLException {

        if (ownerUserId == null) {
            return;
        }

        try (PreparedStatement statement =
                     connection.prepareStatement(
                             """
                             DELETE FROM SAFEPAY_OWNER.BENEFICIARY
                             WHERE OWNER_USER_ID = ?
                             """)) {

            statement.setLong(1, ownerUserId);
            statement.executeUpdate();
        }
    }

    private static void deleteUser(
            Connection connection,
            Long userId)
            throws SQLException {

        if (userId == null) {
            return;
        }

        try (PreparedStatement statement =
                     connection.prepareStatement(
                             """
                             DELETE FROM SAFEPAY_OWNER.APP_USER
                             WHERE USER_ID = ?
                             """)) {

            statement.setLong(1, userId);
            statement.executeUpdate();
        }
    }
}
