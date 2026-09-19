package com.ofss.beans;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class UserRoleId implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(
            name = "USER_ID",
            nullable = false)
    private Long userId;

    @Column(
            name = "ROLE_ID",
            nullable = false)
    private Long roleId;

    protected UserRoleId() {
        // Required by JPA.
    }

    public UserRoleId(Long userId, Long roleId) {
        this.userId = Objects.requireNonNull(
                userId,
                "userId is required");

        this.roleId = Objects.requireNonNull(
                roleId,
                "roleId is required");
    }

    public Long getUserId() {
        return userId;
    }

    public Long getRoleId() {
        return roleId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof UserRoleId otherId)) {
            return false;
        }

        return Objects.equals(userId, otherId.userId)
                && Objects.equals(roleId, otherId.roleId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, roleId);
    }
}