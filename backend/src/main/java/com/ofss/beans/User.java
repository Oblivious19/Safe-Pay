package com.ofss.beans;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(
        name = "APP_USER",
        schema = "SAFEPAY_OWNER")
@SequenceGenerator(
        name = "appUserSequence",
        sequenceName = "SAFEPAY_OWNER.SEQ_APP_USER_ID",
        allocationSize = 1)
public class User {

    @Id
    @GeneratedValue(
            strategy = GenerationType.SEQUENCE,
            generator = "appUserSequence")
    @Column(
            name = "USER_ID",
            nullable = false,
            updatable = false)
    private Long userId;

    @Column(
            name = "FULL_NAME",
            nullable = false,
            length = 120)
    private String fullName;

    @Column(
            name = "EMAIL",
            length = 254)
    private String email;

    @Column(
            name = "MOBILE_NUMBER",
            length = 16)
    private String mobileNumber;

    @Column(
            name = "PASSWORD_HASH",
            nullable = false,
            length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "STATUS",
            nullable = false,
            length = 20)
    private UserStatus status;

    @Column(
            name = "FAILED_LOGIN_COUNT",
            nullable = false,
            precision = 5,
            scale = 0)
    private int failedLoginCount;

    @Column(name = "LOCKED_UNTIL")
    private OffsetDateTime lockedUntil;

    @Column(name = "LAST_SUCCESSFUL_LOGIN_AT")
    private OffsetDateTime lastSuccessfulLoginAt;

    @Column(name = "LAST_FAILED_LOGIN_AT")
    private OffsetDateTime lastFailedLoginAt;

    @Column(
            name = "PASSWORD_CHANGED_AT",
            nullable = false)
    private OffsetDateTime passwordChangedAt;

    @Column(
            name = "SECURITY_VERSION",
            nullable = false,
            precision = 19,
            scale = 0)
    private long securityVersion;

    @Version
    @Column(
            name = "VERSION_NO",
            nullable = false,
            precision = 19,
            scale = 0)
    private long versionNo;

    @Column(
            name = "CREATED_AT",
            nullable = false,
            updatable = false)
    private OffsetDateTime createdAt;

    @Column(
            name = "UPDATED_AT",
            nullable = false)
    private OffsetDateTime updatedAt;

    protected User() {
        // Required by JPA.
    }

    public static User createActiveUser(
            String fullName,
            String email,
            String mobileNumber,
            String passwordHash,
            OffsetDateTime createdAt) {

        User user = new User();

        user.fullName = requireText(
                fullName,
                "fullName",
                2,
                120);

        user.email = normalizeEmail(email);
        user.mobileNumber = normalizeMobile(mobileNumber);

        if (user.email == null && user.mobileNumber == null) {
            throw new IllegalArgumentException(
                    "Either email or mobileNumber is required");
        }

        user.passwordHash = requirePasswordHash(passwordHash);
        user.status = UserStatus.ACTIVE;
        user.failedLoginCount = 0;
        user.securityVersion = 0;

        OffsetDateTime timestamp = requireUtcTimestamp(
                createdAt,
                "createdAt");

        user.passwordChangedAt = timestamp;
        user.createdAt = timestamp;
        user.updatedAt = timestamp;

        return user;
    }

    public boolean releaseExpiredTemporaryLock(
            OffsetDateTime databaseTime) {

        OffsetDateTime timestamp = requireUtcTimestamp(
                databaseTime,
                "databaseTime");

        if (status != UserStatus.LOCKED
                || lockedUntil == null
                || timestamp.isBefore(lockedUntil)) {
            return false;
        }

        status = UserStatus.ACTIVE;
        failedLoginCount = 0;
        lockedUntil = null;
        updatedAt = timestamp;
        return true;
    }

    public boolean recordFailedLogin(
            OffsetDateTime databaseTime,
            int failureThreshold,
            Duration lockDuration) {

        OffsetDateTime timestamp = requireUtcTimestamp(
                databaseTime,
                "databaseTime");

        if (failureThreshold < 1) {
            throw new IllegalArgumentException(
                    "failureThreshold must be positive");
        }

        Objects.requireNonNull(lockDuration, "lockDuration is required");
        if (lockDuration.isZero() || lockDuration.isNegative()) {
            throw new IllegalArgumentException(
                    "lockDuration must be positive");
        }

        lastFailedLoginAt = timestamp;
        updatedAt = timestamp;

        if (status == UserStatus.DISABLED) {
            return false;
        }

        if (status == UserStatus.LOCKED
                && lockedUntil != null
                && timestamp.isBefore(lockedUntil)) {
            return false;
        }

        if (status == UserStatus.LOCKED) {
            status = UserStatus.ACTIVE;
            lockedUntil = null;
            failedLoginCount = 0;
        }

        failedLoginCount++;

        if (failedLoginCount < failureThreshold) {
            return false;
        }

        status = UserStatus.LOCKED;
        lockedUntil = timestamp.plus(lockDuration);
        securityVersion++;
        return true;
    }

    public void recordSuccessfulLogin(OffsetDateTime databaseTime) {
        OffsetDateTime timestamp = requireUtcTimestamp(
                databaseTime,
                "databaseTime");

        if (status != UserStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Only an active user can complete login");
        }

        failedLoginCount = 0;
        lockedUntil = null;
        lastSuccessfulLoginAt = timestamp;
        updatedAt = timestamp;
    }

    public void recordSecurityCompromise(OffsetDateTime databaseTime) {
        OffsetDateTime timestamp = requireUtcTimestamp(
                databaseTime,
                "databaseTime");
        securityVersion++;
        updatedAt = timestamp;
    }

    public boolean applyAdministrativeStatus(
            UserStatus requestedStatus,
            OffsetDateTime databaseTime) {
        UserStatus targetStatus = Objects.requireNonNull(
                requestedStatus,
                "requestedStatus is required");
        OffsetDateTime timestamp = requireUtcTimestamp(
                databaseTime,
                "databaseTime");

        boolean alreadyCanonical = status == targetStatus
                && (targetStatus != UserStatus.LOCKED
                        || lockedUntil == null)
                && (targetStatus != UserStatus.ACTIVE
                        || (failedLoginCount == 0 && lockedUntil == null));
        if (alreadyCanonical) {
            return false;
        }

        status = targetStatus;
        failedLoginCount = 0;
        lockedUntil = null;
        securityVersion++;
        updatedAt = timestamp;
        return true;
    }

    public void recordSecurityPolicyChange(
            OffsetDateTime databaseTime) {
        recordSecurityCompromise(databaseTime);
    }

    private static String requireText(
            String value,
            String fieldName,
            int minimumLength,
            int maximumLength) {

        if (value == null) {
            throw new IllegalArgumentException(
                    fieldName + " is required");
        }

        String normalizedValue = value.trim();

        if (normalizedValue.length() < minimumLength
                || normalizedValue.length() > maximumLength) {
            throw new IllegalArgumentException(
                    fieldName + " has an invalid length");
        }

        return normalizedValue;
    }

    private static String normalizeEmail(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalizedValue =
                value.trim().toLowerCase(Locale.ROOT);

        if (normalizedValue.length() > 254) {
            throw new IllegalArgumentException(
                    "email has an invalid length");
        }

        return normalizedValue;
    }

    private static String normalizeMobile(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalizedValue = value.trim();

        if (normalizedValue.length() > 16) {
            throw new IllegalArgumentException(
                    "mobileNumber has an invalid length");
        }

        return normalizedValue;
    }

    private static String requirePasswordHash(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "passwordHash is required");
        }

        if (value.length() > 255) {
            throw new IllegalArgumentException(
                    "passwordHash has an invalid length");
        }

        return value;
    }

    private static OffsetDateTime requireUtcTimestamp(
            OffsetDateTime value,
            String fieldName) {

        return Objects.requireNonNull(
                value,
                fieldName + " is required")
                .withOffsetSameInstant(ZoneOffset.UTC);
    }

    public Long getUserId() {
        return userId;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public String getMobileNumber() {
        return mobileNumber;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public UserStatus getStatus() {
        return status;
    }

    public int getFailedLoginCount() {
        return failedLoginCount;
    }

    public OffsetDateTime getLockedUntil() {
        return lockedUntil;
    }

    public OffsetDateTime getLastSuccessfulLoginAt() {
        return lastSuccessfulLoginAt;
    }

    public OffsetDateTime getLastFailedLoginAt() {
        return lastFailedLoginAt;
    }

    public OffsetDateTime getPasswordChangedAt() {
        return passwordChangedAt;
    }

    public long getSecurityVersion() {
        return securityVersion;
    }

    public long getVersionNo() {
        return versionNo;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
