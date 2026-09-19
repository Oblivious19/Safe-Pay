package com.ofss.beans;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

@Entity
@Table(
        name = "USER_ROLE",
        schema = "SAFEPAY_OWNER")
public class UserRole {

    @EmbeddedId
    private UserRoleId id;

    @MapsId("userId")
    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false)
    @JoinColumn(
            name = "USER_ID",
            nullable = false,
            updatable = false)
    private User user;

    @MapsId("roleId")
    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false)
    @JoinColumn(
            name = "ROLE_ID",
            nullable = false,
            updatable = false)
    private Role role;

    @Column(
            name = "ASSIGNED_AT",
            nullable = false,
            updatable = false)
    private OffsetDateTime assignedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "ASSIGNED_BY_USER_ID",
            updatable = false)
    private User assignedByUser;

    protected UserRole() {
        // Required by JPA.
    }

    public static UserRole assign(
            User user,
            Role role,
            User assignedByUser,
            OffsetDateTime assignedAt) {

        Objects.requireNonNull(user, "user is required");
        Objects.requireNonNull(role, "role is required");

        if (user.getUserId() == null) {
            throw new IllegalArgumentException(
                    "user must already be persisted");
        }

        if (role.getRoleId() == null) {
            throw new IllegalArgumentException(
                    "role must already exist");
        }

        OffsetDateTime timestamp = Objects
                .requireNonNull(
                        assignedAt,
                        "assignedAt is required")
                .withOffsetSameInstant(ZoneOffset.UTC);

        UserRole assignment = new UserRole();

        assignment.id = new UserRoleId(
                user.getUserId(),
                role.getRoleId());

        assignment.user = user;
        assignment.role = role;
        assignment.assignedByUser = assignedByUser;
        assignment.assignedAt = timestamp;

        return assignment;
    }

    public UserRoleId getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Role getRole() {
        return role;
    }

    public OffsetDateTime getAssignedAt() {
        return assignedAt;
    }

    public User getAssignedByUser() {
        return assignedByUser;
    }
}