package com.ofss.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.Repository;

import jakarta.persistence.LockModeType;

class LedgerRepositoryContractTest {

    private static final Set<String> DESTRUCTIVE_METHODS = Set.of(
            "delete",
            "deleteById",
            "deleteAll",
            "deleteAllById",
            "deleteAllInBatch",
            "deleteAllByIdInBatch");

    @Test
    void allRepositoriesUseMinimalRepositoryBoundary() {
        for (Class<?> repositoryType : repositories()) {
            assertThat(Repository.class)
                    .isAssignableFrom(repositoryType);
            assertThat(repositoryType.getInterfaces())
                    .containsExactly(Repository.class);
        }
    }

    @Test
    void ledgerRepositoriesExposeNoDeleteOperations() {
        for (Class<?> repositoryType : repositories()) {
            Set<String> methodNames = Arrays
                    .stream(repositoryType.getMethods())
                    .map(Method::getName)
                    .collect(Collectors.toSet());

            assertThat(methodNames)
                    .doesNotContainAnyElementsOf(DESTRUCTIVE_METHODS);
        }
    }

    @Test
    void ledgerEntryRepositoryExposesInsertAndReadOnlyHistory() {
        Set<String> methodNames = Arrays
                .stream(LedgerEntryDao.class.getMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());

        assertThat(methodNames).containsExactlyInAnyOrder(
                "save",
                "saveAll",
                "findAllByPosting_PostingIdOrderByLineNumberAsc",
                "findAllByTransaction_TransactionIdOrderByLineNumberAsc");
    }

    @Test
    void mutableHeadersAndExceptionsRequirePessimisticLockReads()
            throws Exception {

        Method postingLock = LedgerPostingDao.class.getMethod(
                "findByIdForUpdate",
                Long.class);

        Method exceptionLock =
                TransactionExceptionDao.class.getMethod(
                        "findByIdForUpdate",
                        Long.class);

        assertThat(postingLock.getAnnotation(Lock.class).value())
                .isEqualTo(LockModeType.PESSIMISTIC_WRITE);
        assertThat(exceptionLock.getAnnotation(Lock.class).value())
                .isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }

    private static List<Class<?>> repositories() {
        return List.of(
                LedgerPostingDao.class,
                LedgerEntryDao.class,
                TransactionExceptionDao.class);
    }
}
