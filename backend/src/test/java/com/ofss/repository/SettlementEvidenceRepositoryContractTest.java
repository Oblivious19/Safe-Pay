package com.ofss.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.springframework.data.repository.Repository;

class SettlementEvidenceRepositoryContractTest {

    @Test
    void immutableAuditRepositoryExposesAppendAndReadWithoutDelete() {
        assertThat(Repository.class).isAssignableFrom(AuditLogDao.class);
        assertThat(methodNames(AuditLogDao.class)).contains("save");
        assertThat(methodNames(AuditLogDao.class))
                .noneMatch(name -> name.startsWith("delete"));
    }

    @Test
    void notificationRepositoryHasNoDestructiveApi() {
        assertThat(Repository.class).isAssignableFrom(AppNotificationDao.class);
        assertThat(methodNames(AppNotificationDao.class)).contains("save");
        assertThat(methodNames(AppNotificationDao.class))
                .noneMatch(name -> name.startsWith("delete"));
    }

    @Test
    void transactionRepositoryExposesBoundedDatabaseDueQuery() {
        assertThat(methodNames(TransactionDao.class))
                .contains("findDueSettlementTransactionIds");
    }

    private static java.util.List<String> methodNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods())
                .map(Method::getName)
                .toList();
    }
}
