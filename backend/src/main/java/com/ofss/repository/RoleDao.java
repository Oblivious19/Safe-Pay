package com.ofss.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.Repository;

import com.ofss.beans.Role;
import com.ofss.beans.RoleName;

public interface RoleDao extends Repository<Role, Long> {

    Optional<Role> findByRoleCode(RoleName roleCode);

    boolean existsByRoleCode(RoleName roleCode);

    List<Role> findAllByOrderByRoleCodeAsc();
}