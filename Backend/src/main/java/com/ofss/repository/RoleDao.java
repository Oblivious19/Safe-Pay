package com.ofss.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.ofss.beans.Role;

public interface RoleDao extends JpaRepository<Role, Long> {
    Optional<Role> findByRoleName(String roleName);
}
