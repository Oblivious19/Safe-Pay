package com.ofss.repository;

import java.util.Optional;
import com.ofss.beans.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProfileRepository extends JpaRepository<User, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.userId = :id")
    Optional<User> lockProfile(@Param("id") Long id);
    boolean existsByEmailAndUserIdNot(String email, Long id);
    boolean existsByPhoneAndUserIdNot(String phone, Long id);
}
