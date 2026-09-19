package com.ofss.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.data.repository.Repository;

class AuditRepositoryContractTest {

    @Test
    void auditRepositorySupportsOnlyExplicitPagedReadQueries() {
        assertThat(java.util.Arrays.stream(
                        AuditLogDao.class.getMethods())
                .map(Method::getName))
                .contains(
                        "search",
                        "findCustomerVisibleTimeline",
                        "findAllByTransaction_TransactionId",
                        "findAllByTransaction_TransactionIdOrderByOccurredAtAsc");
    }

    @Test
    void auditRepositoryDoesNotExposeMutationOrDeleteApis() {
        assertThat(java.util.Arrays.stream(
                        AuditLogDao.class.getMethods())
                .map(Method::getName))
                .doesNotContain(
                        "delete",
                        "deleteById",
                        "deleteAll",
                        "saveAll");
    }

    @Test
    void notificationRepositoryRetainsAppendOnlyContentBoundary() {
        assertThat(Repository.class)
                .isAssignableFrom(AppNotificationDao.class);
        assertThat(java.util.Arrays.stream(
                        AppNotificationDao.class.getMethods())
                .map(Method::getName))
                .doesNotContain(
                        "delete",
                        "deleteById",
                        "deleteAll",
                        "saveAll");
    }

    @Test
    void accessRepositoriesExposeOnlyRequiredExistenceChecks() {
        assertThat(java.util.Arrays.stream(
                        RiskReviewDao.class.getMethods())
                .map(Method::getName))
                .contains("existsByTransaction_TransactionId");
        assertThat(java.util.Arrays.stream(
                        TransactionDao.class.getMethods())
                .map(Method::getName))
                .contains("existsByTransactionId");
    }
}
