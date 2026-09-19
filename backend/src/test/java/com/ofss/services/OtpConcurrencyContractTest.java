package com.ofss.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.transaction.annotation.Transactional;

import com.ofss.beans.Account;
import com.ofss.beans.OtpChallenge;
import com.ofss.beans.TransactionDb;
import com.ofss.repository.AccountDao;
import com.ofss.repository.OtpChallengeDao;
import com.ofss.repository.TransactionDao;

import jakarta.persistence.LockModeType;
import jakarta.persistence.Version;

class OtpConcurrencyContractTest {

    @Test
    void everyOtpMutationOwnsOneReadWriteTransaction()
            throws Exception {

        Transactional classContract = OtpServiceImpl.class
                .getAnnotation(Transactional.class);

        assertThat(classContract).isNotNull();
        assertThat(classContract.readOnly()).isTrue();

        assertReadWriteTransaction("issue", Long.class, Long.class);
        assertReadWriteTransaction("resend", Long.class, Long.class);
        assertReadWriteTransaction(
                "verify",
                Long.class,
                Long.class,
                Long.class,
                String.class);
    }

    @Test
    void transactionChallengeAndAccountMutationReadsAreSerialized()
            throws Exception {

        assertPessimisticWrite(TransactionDao.class.getMethod(
                "findOwnedByIdForUpdate",
                Long.class,
                Long.class));
        assertPessimisticWrite(OtpChallengeDao.class.getMethod(
                "findOwnedByIdForUpdate",
                Long.class,
                Long.class,
                Long.class));
        assertPessimisticWrite(OtpChallengeDao.class.getMethod(
                "findLatestOwnedForUpdate",
                Long.class,
                Long.class));
        assertPessimisticWrite(AccountDao.class.getMethod(
                "findByIdForUpdate",
                Long.class));
    }

    @Test
    void allThreeMutableAggregateRootsRetainOptimisticVersions()
            throws Exception {

        assertThat(versionField(TransactionDb.class)
                .getAnnotation(Version.class)).isNotNull();
        assertThat(versionField(OtpChallenge.class)
                .getAnnotation(Version.class)).isNotNull();
        assertThat(versionField(Account.class)
                .getAnnotation(Version.class)).isNotNull();
    }

    private static void assertReadWriteTransaction(
            String methodName,
            Class<?>... parameterTypes) throws Exception {

        Method method = OtpServiceImpl.class.getMethod(
                methodName,
                parameterTypes);
        Transactional transaction = method.getAnnotation(
                Transactional.class);

        assertThat(transaction).isNotNull();
        assertThat(transaction.readOnly()).isFalse();
    }

    private static void assertPessimisticWrite(Method method) {
        Lock lock = method.getAnnotation(Lock.class);

        assertThat(lock).isNotNull();
        assertThat(lock.value())
                .isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }

    private static Field versionField(Class<?> entityType)
            throws Exception {

        return java.util.Arrays.stream(entityType.getDeclaredFields())
                .filter(field -> field.isAnnotationPresent(
                        Version.class))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        entityType.getSimpleName()
                                + " must declare @Version"));
    }
}
