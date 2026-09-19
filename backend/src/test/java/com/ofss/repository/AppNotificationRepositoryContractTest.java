package com.ofss.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import jakarta.persistence.LockModeType;

class AppNotificationRepositoryContractTest {

    @Test
    void repositoryRemainsRestrictedWithoutDeleteSurface() {
        assertThat(Repository.class)
                .isAssignableFrom(AppNotificationDao.class);
        assertThat(AppNotificationDao.class.getMethods())
                .extracting(Method::getName)
                .doesNotContain(
                        "delete",
                        "deleteById",
                        "deleteAll",
                        "deleteAllById");
    }

    @Test
    void ownedReadMutationUsesPessimisticLock() throws Exception {
        Method method = AppNotificationDao.class.getMethod(
                "findOwnedByIdForUpdate",
                Long.class,
                Long.class);

        assertThat(method.getAnnotation(Lock.class).value())
                .isEqualTo(LockModeType.PESSIMISTIC_WRITE);
        assertThat(method.getAnnotation(Query.class).value())
                .contains(
                        "notification.recipient.userId = :recipientUserId");
    }

    @Test
    void dispatcherLocksOneNotificationBeforePublishing()
            throws Exception {
        Method method = AppNotificationDao.class.getMethod(
                "findByIdForDispatch",
                Long.class);

        assertThat(method.getAnnotation(Lock.class).value())
                .isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    void dueQueryIsBoundedOldestFirstAndDatabaseTimeExists()
            throws Exception {
        Method due = java.util.Arrays.stream(
                AppNotificationDao.class.getMethods())
                .filter(method -> method.getName()
                        .equals("findDueNotificationIds"))
                .findFirst()
                .orElseThrow();
        assertThat(due.getAnnotation(Query.class).value())
                .contains(
                        "notification.nextAttemptAt <= :databaseTime",
                        "notification.createdAt asc",
                        "notification.notificationId asc");

        Method clock = AppNotificationDao.class.getMethod(
                "currentDatabaseTime");
        assertThat(clock.getAnnotation(Query.class).nativeQuery())
                .isTrue();
    }
}
