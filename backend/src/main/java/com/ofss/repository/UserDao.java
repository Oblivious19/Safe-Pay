package com.ofss.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import com.ofss.beans.User;
import com.ofss.beans.RoleName;
import com.ofss.beans.UserStatus;
import com.ofss.beans.SafePayPinResetStatus;

public interface UserDao extends Repository<User, Long> {

    <S extends User> S save(S user);

    Optional<User> findById(Long userId);

    @Query(value = """
            select appUser from User appUser
             where (:q is null or lower(appUser.fullName) like :q escape '!'
                    or lower(appUser.email) like :q escape '!'
                    or appUser.mobileNumber like :q escape '!')
               and (:status is null or appUser.status = :status)
               and (:role is null or exists (
                    select assignment from UserRole assignment
                     where assignment.user = appUser and assignment.role.roleCode = :role))
             order by appUser.userId asc
            """, countQuery = """
            select count(appUser) from User appUser
             where (:q is null or lower(appUser.fullName) like :q escape '!'
                    or lower(appUser.email) like :q escape '!'
                    or appUser.mobileNumber like :q escape '!')
               and (:status is null or appUser.status = :status)
               and (:role is null or exists (
                    select assignment from UserRole assignment
                     where assignment.user = appUser and assignment.role.roleCode = :role))
            """)
    Page<User> searchUsers(@Param("q") String q,
            @Param("role") RoleName role, @Param("status") UserStatus status,
            Pageable pageable);

    Optional<User> findByEmail(String email);

    Optional<User> findByMobileNumber(String mobileNumber);

    boolean existsByEmail(String email);

    boolean existsByMobileNumber(String mobileNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select appUser
              from User appUser
             where appUser.email = :loginIdentifier
                or appUser.mobileNumber = :loginIdentifier
            """)
    Optional<User> findByLoginIdentifierForUpdate(
            @Param("loginIdentifier") String loginIdentifier);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select appUser
              from User appUser
             where appUser.userId = :userId
            """)
    Optional<User> findByIdForUpdate(@Param("userId") Long userId);

    java.util.List<User> findBySafePayPinResetStatus(SafePayPinResetStatus status);

    @Query(
            value = "SELECT SYSTIMESTAMP FROM DUAL",
            nativeQuery = true)
    java.time.OffsetDateTime currentDatabaseTime();
}
