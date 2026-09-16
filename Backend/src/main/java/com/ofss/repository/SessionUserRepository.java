package com.ofss.repository;

import java.util.Optional;
import com.ofss.beans.User;
import com.ofss.beans.UserStatus;
import org.springframework.data.repository.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** One small query checks current access without loading credentials or lazy role entities. */
public interface SessionUserRepository extends Repository<User, Long> {
    @Query("select u.status as status, r.roleName as roleName from User u join u.role r where u.userId = :id")
    Optional<Access> findAccess(@Param("id") Long id);

    interface Access {
        UserStatus getStatus();
        String getRoleName();
    }
}
