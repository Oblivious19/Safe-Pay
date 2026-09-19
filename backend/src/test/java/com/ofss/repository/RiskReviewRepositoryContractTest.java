package com.ofss.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.Repository;

import jakarta.persistence.LockModeType;

class RiskReviewRepositoryContractTest {

    private static final Set<String> DESTRUCTIVE_METHODS = Set.of(
            "delete",
            "deleteById",
            "deleteAll",
            "deleteAllById");

    @Test
    void exposesMinimalAppendAndReadContractWithoutDeletes() {
        assertThat(RiskReviewDao.class.getInterfaces())
                .containsExactly(Repository.class);

        Set<String> methodNames = Arrays.stream(
                        RiskReviewDao.class.getMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());

        assertThat(methodNames)
                .doesNotContainAnyElementsOf(DESTRUCTIVE_METHODS);
    }

    @Test
    void mutationReadsUsePessimisticWriteLocks()
            throws Exception {

        assertWriteLock("findByIdForUpdate", Long.class);
        assertWriteLock(
                "findPendingByTransactionIdForUpdate",
                Long.class);
        assertWriteLock(
                "findFirstByTransaction_TransactionIdOrderByReviewRoundDesc",
                Long.class);
    }

    @Test
    void queueAndDetailEagerlyLoadSafeProjectionInputs()
            throws Exception {

        Method queue = RiskReviewDao.class.getMethod(
                "findAllByStatusOrderByRequestedAtAscApprovalIdAsc",
                com.ofss.beans.RiskReviewStatus.class,
                org.springframework.data.domain.Pageable.class);
        Method detail = RiskReviewDao.class.getMethod(
                "findByApprovalId",
                Long.class);

        assertThat(queue.getAnnotation(EntityGraph.class).attributePaths())
                .contains(
                        "transaction.customer",
                        "transaction.sourceAccount",
                        "transaction.beneficiary");
        assertThat(detail.getAnnotation(EntityGraph.class).attributePaths())
                .contains("assignedRiskOfficer", "decidedByUser");
    }

    private static void assertWriteLock(
            String methodName,
            Class<?>... parameterTypes) throws Exception {

        Method method = RiskReviewDao.class.getMethod(
                methodName,
                parameterTypes);
        assertThat(method.getAnnotation(Lock.class).value())
                .isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }
}
