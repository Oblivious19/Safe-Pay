package com.ofss.repository;

import java.util.Optional;
import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import com.ofss.beans.User;
import com.ofss.beans.UserStatus;

public interface UserDao extends JpaRepository<User, Long> {
    @Override
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"role"})
    java.util.List<User> findAll(org.springframework.data.domain.Sort sort);
    Optional<User> findByEmail(String email);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.email = :email")
    Optional<User> findForLoginByEmail(@Param("email") String email);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.phone = :phone")
    Optional<User> findForLoginByPhone(@Param("phone") String phone);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.userId = :id")
    Optional<User> findForVerificationById(@Param("id") Long id);
    boolean existsByPhone(String phone);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update User u set u.status = :target, u.updatedAt = :time, u.lockedUntil = null, "
            + "u.failedLoginAttempts = case when :target = com.ofss.beans.UserStatus.ACTIVE then 0 else u.failedLoginAttempts end "
            + "where u.userId = :id and u.status = :expected")
    int changeAdminStatus(@Param("id") Long id, @Param("expected") UserStatus expected,
            @Param("target") UserStatus target, @Param("time") LocalDateTime time);
}
