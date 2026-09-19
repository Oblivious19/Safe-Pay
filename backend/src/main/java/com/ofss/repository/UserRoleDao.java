package com.ofss.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.ofss.beans.RoleName;
import com.ofss.beans.UserRole;
import com.ofss.beans.UserRoleId;
import com.ofss.beans.UserStatus;

public interface UserRoleDao
        extends Repository<UserRole, UserRoleId> {

    <S extends UserRole> S save(S assignment);

    Optional<UserRole> findById(UserRoleId id);

    boolean existsById(UserRoleId id);

    void deleteById(UserRoleId id);

    List<UserRole> findAllByUser_UserIdOrderByAssignedAtAsc(
            Long userId);

    @Query("""
            select assignment from UserRole assignment
              join fetch assignment.role
             where assignment.user.userId in :userIds
             order by assignment.user.userId asc, assignment.assignedAt asc
            """)
    List<UserRole> findAllForUsers(@Param("userIds") List<Long> userIds);

    boolean existsByUser_UserIdAndRole_RoleCode(
            Long userId,
            RoleName roleCode);

    @Query("""
            select coalesce(
                        assignment.user.email,
                        assignment.user.mobileNumber)
              from UserRole assignment
             where assignment.role.roleCode = :roleCode
               and assignment.user.status = :userStatus
             order by assignment.user.userId asc
            """)
    List<String> findPrincipalNamesByRoleAndStatus(
            @Param("roleCode") RoleName roleCode,
            @Param("userStatus") UserStatus userStatus);
}
