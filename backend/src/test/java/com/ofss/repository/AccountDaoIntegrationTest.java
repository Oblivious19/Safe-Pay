package com.ofss.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE)
class AccountDaoIntegrationTest {

    private static final Long UNKNOWN_ACCOUNT_ID =
            Long.MAX_VALUE;

    private static final Long UNKNOWN_OWNER_ID =
            Long.MAX_VALUE - 1;

    @Autowired
    private AccountDao accountDao;

    @Test
    void executesCustomerDirectoryQueriesWithNullAndTypedFilters() {
        var page = org.springframework.data.domain.PageRequest.of(0, 1);
        assertThat(accountDao.searchCustomerAccounts(UNKNOWN_OWNER_ID, null, null, page)).isEmpty();
        assertThat(accountDao.searchCustomerAccounts(UNKNOWN_OWNER_ID, java.math.BigDecimal.ZERO,
                com.ofss.beans.AccountType.CURRENT, page)).isEmpty();
        assertThat(accountDao.findCustomerAccountById(UNKNOWN_ACCOUNT_ID)).isEmpty();
        var accounts = accountDao.searchCustomerAccounts(null, null, null, page);
        assertThat(accounts.getContent()).allSatisfy(account -> {
            assertThat(account.getOwner()).isNotNull();
            assertThat(account.getAccountType().isCustomerOwnedType()).isTrue();
        });
    }

    @Test
    void internalShowcaseAccountsAreNotExposedAsCustomerDetails() {
        for (String number : java.util.List.of("SAFEPAY_OUTBOUND_CLEARING", "SAFEPAY_OPENING_BALANCE_CONTROL")) {
            var internal = accountDao.findByAccountNumber(number).orElseThrow();
            assertThat(accountDao.findCustomerAccountById(internal.getAccountId())).isEmpty();
        }
    }

    @Test
    void executesOwnerAccountListQuery() {
        assertThat(accountDao
                .findAllByOwner_UserIdOrderByAccountIdAsc(
                        UNKNOWN_OWNER_ID))
                .isEmpty();

        assertThat(accountDao
                .existsByAccountIdAndOwner_UserId(
                        UNKNOWN_ACCOUNT_ID,
                        UNKNOWN_OWNER_ID))
                .isFalse();
    }

    @Test
    void ownershipAwareLookupHidesUnknownAccount() {
        assertThat(accountDao
                .findByAccountIdAndOwner_UserId(
                        UNKNOWN_ACCOUNT_ID,
                        UNKNOWN_OWNER_ID))
                .isEmpty();
    }

    @Test
    void executesUniqueAndLockingQueries() {
        assertThat(accountDao.findByAccountNumber(
                "__SAFEPAY_UNKNOWN_ACCOUNT__"))
                .isEmpty();

        /*
         * @DataJpaTest runs this method inside a transaction,
         * which is required for PESSIMISTIC_WRITE.
         */
        assertThat(accountDao.findByIdForUpdate(
                UNKNOWN_ACCOUNT_ID))
                .isEmpty();
    }

    @Test
    void exposesNoAccountCreationOrDeletionMethods() {
        Set<String> prohibitedMethods = Set.of(
                "save",
                "saveAll",
                "delete",
                "deleteById",
                "deleteAll",
                "deleteAllById");

        Set<String> exposedMethodNames = Arrays
                .stream(AccountDao.class.getMethods())
                .map(Method::getName)
                .collect(java.util.stream.Collectors.toSet());

        assertThat(exposedMethodNames)
                .doesNotContainAnyElementsOf(
                        prohibitedMethods);
    }
}
