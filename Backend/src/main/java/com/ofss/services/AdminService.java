package com.ofss.services;

import java.time.LocalDateTime;
import java.util.List;
import com.ofss.beans.*;
import com.ofss.beans.AdminDtos.*;
import com.ofss.excp.InvalidAdminStatusTransitionException;
import com.ofss.excp.ResourceNotFoundExcp;
import com.ofss.repository.AccountDao;
import com.ofss.repository.UserDao;
import com.ofss.repository.AuditLogDao;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {
    private final UserDao users;
    private final AccountDao accounts;
    private final AuditLogDao auditLogs;

    public AdminService(UserDao users, AccountDao accounts, AuditLogDao auditLogs) {
        this.users = users;
        this.accounts = accounts;
        this.auditLogs = auditLogs;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getUsers() {
        return users.findAll(Sort.by("userId")).stream().map(UserResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public UserResponse getUser(Long id) {
        return UserResponse.from(users.findById(id)
                .orElseThrow(() -> new ResourceNotFoundExcp("User not found")));
    }

    @Transactional
    public UserResponse changeUserStatus(Long id, UserStatus target, Long actorId) {
        if (actorId == null) throw new IllegalArgumentException("Authenticated admin is required");
        User user = users.findById(id).orElseThrow(() -> new ResourceNotFoundExcp("User not found"));
        UserStatus current = user.getStatus();
        validateUserTransition(current, target);
        if (current == target) return UserResponse.from(user);
        LocalDateTime now = LocalDateTime.now();
        UserResponse response = new UserResponse(user.getUserId(), user.getName(), user.getEmail(), user.getPhone(),
                user.getRole().getRoleName(), target, user.getCreatedAt(), now);
        // Targeted status/lockout update only; reject a changed source status.
        if (users.changeAdminStatus(id, current, target, now) != 1) {
            throw new InvalidAdminStatusTransitionException("User status changed concurrently; reload and retry");
        }
        // Same transaction as the status update: an audit failure rolls back the change.
        // USER_ID identifies the acting admin; ACTION identifies the affected user.
        AuditLog audit = new AuditLog();
        audit.setUserId(actorId);
        audit.setAction("USER_STATUS:" + id);
        audit.setOldState(current.name());
        audit.setNewState(target.name());
        audit.setCreatedAt(now);
        auditLogs.save(audit);
        return response;
    }

    private void validateUserTransition(UserStatus current, UserStatus target) {
        if (current == null || target == null) {
            throw new InvalidAdminStatusTransitionException("A valid current and target user status is required");
        }
        boolean allowed = current == target || switch (current) {
            case ACTIVE -> true;
            case LOCKED -> target == UserStatus.ACTIVE || target == UserStatus.SUSPENDED || target == UserStatus.INACTIVE;
            case SUSPENDED -> target == UserStatus.ACTIVE || target == UserStatus.INACTIVE;
            case INACTIVE -> target == UserStatus.ACTIVE || target == UserStatus.SUSPENDED;
        };
        if (!allowed) throw new InvalidAdminStatusTransitionException("Cannot change user status from " + current + " to " + target);
    }

    @Transactional
    public AccountResponse changeAccountStatus(Long id, AccountStatus target) {
        Account account = accounts.findById(id).orElseThrow(() -> new ResourceNotFoundExcp("Account not found"));
        AccountStatus current = account.getStatus();
        if ((current != AccountStatus.ACTIVE && current != AccountStatus.BLOCKED)
                || (target != AccountStatus.ACTIVE && target != AccountStatus.BLOCKED)) {
            throw new InvalidAdminStatusTransitionException("Only ACTIVE and BLOCKED account states may be changed here");
        }
        LocalDateTime updatedAt = current == target ? account.getUpdatedAt() : LocalDateTime.now();
        AccountResponse response = new AccountResponse(account.getAccountId(), account.getUserId(),
                account.getAccountNumber(), account.getAccountType(), target, updatedAt);
        // Never save a detached account: a status edit must not overwrite a concurrent balance change.
        if (current != target && accounts.changeAdminStatus(id, current, target, updatedAt) != 1) {
            throw new InvalidAdminStatusTransitionException("Account status changed concurrently; reload and retry");
        }
        return response;
    }
}
