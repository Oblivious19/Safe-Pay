package com.ofss.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import com.ofss.beans.BeneficiaryStatus;

@DataJpaTest
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE)
class BeneficiaryDaoIntegrationTest {

    private static final Long UNKNOWN_BENEFICIARY_ID =
            Long.MAX_VALUE - 2;

    private static final Long UNKNOWN_OWNER_ID =
            Long.MAX_VALUE - 3;

    @Autowired
    private BeneficiaryDao beneficiaryDao;

    @Test
    void executesOwnershipAwareReadQueries() {
        assertThat(beneficiaryDao
                .findAllByOwner_UserIdOrderByBeneficiaryIdAsc(
                        UNKNOWN_OWNER_ID))
                .isEmpty();

        assertThat(beneficiaryDao
                .findByBeneficiaryIdAndOwner_UserId(
                        UNKNOWN_BENEFICIARY_ID,
                        UNKNOWN_OWNER_ID))
                .isEmpty();

        assertThat(beneficiaryDao
                .findByBeneficiaryIdAndOwner_UserIdAndStatus(
                        UNKNOWN_BENEFICIARY_ID,
                        UNKNOWN_OWNER_ID,
                        BeneficiaryStatus.ACTIVE))
                .isEmpty();
    }

    @Test
    void executesDuplicateDetectionQueries() {
        assertThat(beneficiaryDao
                .existsByOwner_UserIdAndBankAccountNumberAndIfscCode(
                        UNKNOWN_OWNER_ID,
                        "__SAFEPAY_UNKNOWN_ACCOUNT__",
                        "ZZZZ0000000"))
                .isFalse();

        assertThat(beneficiaryDao
                .existsByOwner_UserIdAndUpiId(
                        UNKNOWN_OWNER_ID,
                        "__unknown__@safepay.invalid"))
                .isFalse();
    }

    @Test
    void executesOwnedLockingQuery() {
        /*
         * @DataJpaTest runs this method inside a transaction,
         * which is required for PESSIMISTIC_WRITE.
         */
        assertThat(beneficiaryDao.findOwnedByIdForUpdate(
                UNKNOWN_BENEFICIARY_ID,
                UNKNOWN_OWNER_ID))
                .isEmpty();
    }

    @Test
    void exposesCreationButNoDeletionMethods() {
        Set<String> exposedMethodNames = Arrays
                .stream(BeneficiaryDao.class.getMethods())
                .map(Method::getName)
                .collect(java.util.stream.Collectors.toSet());

        assertThat(exposedMethodNames)
                .contains("save");

        Set<String> prohibitedMethods = Set.of(
                "delete",
                "deleteById",
                "deleteAll",
                "deleteAllById");

        assertThat(exposedMethodNames)
                .doesNotContainAnyElementsOf(
                        prohibitedMethods);
    }
}