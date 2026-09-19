package com.ofss.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.Repository;

import jakarta.persistence.LockModeType;

class OtpChallengeRepositoryContractTest {

    private static final Set<String> DESTRUCTIVE_METHODS = Set.of(
            "delete",
            "deleteById",
            "deleteAll",
            "deleteAllById",
            "deleteAllInBatch",
            "deleteAllByIdInBatch");

    @Test
    void usesMinimalRepositoryAndExposesNoDeleteOperation() {
        assertThat(OtpChallengeDao.class.getInterfaces())
                .containsExactly(Repository.class);

        Set<String> methodNames = Arrays.stream(
                        OtpChallengeDao.class.getMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());

        assertThat(methodNames)
                .doesNotContainAnyElementsOf(DESTRUCTIVE_METHODS);
    }

    @Test
    void allMutationReadsUsePessimisticWriteLocks()
            throws Exception {

        Method byId = OtpChallengeDao.class.getMethod(
                "findOwnedByIdForUpdate",
                Long.class,
                Long.class,
                Long.class);
        Method pending = OtpChallengeDao.class.getMethod(
                "findPendingOwnedForUpdate",
                Long.class,
                Long.class);
        Method latest = OtpChallengeDao.class.getMethod(
                "findLatestOwnedForUpdate",
                Long.class,
                Long.class);

        assertThat(byId.getAnnotation(Lock.class).value())
                .isEqualTo(LockModeType.PESSIMISTIC_WRITE);
        assertThat(pending.getAnnotation(Lock.class).value())
                .isEqualTo(LockModeType.PESSIMISTIC_WRITE);
        assertThat(latest.getAnnotation(Lock.class).value())
                .isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    void exposesOwnershipQualifiedReadAndCycleCountingOnly() {
        Set<String> methodNames = Arrays.stream(
                        OtpChallengeDao.class.getDeclaredMethods())
                .map(Method::getName)
                .filter(name -> !name.startsWith("lambda$"))
                .collect(Collectors.toSet());

        assertThat(methodNames).containsExactlyInAnyOrder(
                "save",
                "saveAndFlush",
                "findOwnedByIdForUpdate",
                "findPendingOwnedForUpdate",
                "findLatestOwnedForUpdate",
                "findFirstByTransaction_TransactionIdAndCustomer_UserIdOrderByCreatedAtDescOtpChallengeIdDesc",
                "findAllByTransaction_TransactionIdAndCustomer_UserIdOrderByCreatedAtAscOtpChallengeIdAsc",
                "countByTransaction_TransactionIdAndCustomer_UserIdAndCreatedAtGreaterThanEqual");
    }
}
