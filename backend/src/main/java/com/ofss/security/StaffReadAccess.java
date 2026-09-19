package com.ofss.security;

import java.util.Objects;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ofss.beans.RoleName;
import com.ofss.beans.UserStatus;
import com.ofss.repository.UserDao;
import com.ofss.repository.UserRoleDao;

@Component
@Transactional(readOnly = true)
public class StaffReadAccess {

    private final UserDao userDao;
    private final UserRoleDao userRoleDao;

    public StaffReadAccess(UserDao userDao, UserRoleDao userRoleDao) {
        this.userDao = Objects.requireNonNull(userDao, "userDao is required");
        this.userRoleDao = Objects.requireNonNull(userRoleDao, "userRoleDao is required");
    }

    public void requireActiveRole(Long actorUserId, RoleName requiredRole) {
        Objects.requireNonNull(requiredRole, "requiredRole is required");
        if (actorUserId == null || actorUserId <= 0
                || userDao.findById(actorUserId)
                        .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                        .isEmpty()
                || !userRoleDao.existsByUser_UserIdAndRole_RoleCode(
                        actorUserId, requiredRole)) {
            throw new AccessDeniedException("An active " + requiredRole + " is required");
        }
    }
}
