package com.ofss.beans;

import java.time.OffsetDateTime;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Immutable
@Table(
        name = "APP_ROLE",
        schema = "SAFEPAY_OWNER")
public class Role {

    @Id
    @Column(
            name = "ROLE_ID",
            nullable = false,
            updatable = false)
    private Long roleId;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "ROLE_CODE",
            nullable = false,
            length = 30,
            updatable = false)
    private RoleName roleCode;

    @Column(
            name = "DESCRIPTION",
            nullable = false,
            length = 200,
            updatable = false)
    private String description;

    @Column(
            name = "CREATED_AT",
            nullable = false,
            updatable = false)
    private OffsetDateTime createdAt;

    protected Role() {
        // Required by JPA for database materialization.
    }

    public Long getRoleId() {
        return roleId;
    }

    public RoleName getRoleCode() {
        return roleCode;
    }

    public String getDescription() {
        return description;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}