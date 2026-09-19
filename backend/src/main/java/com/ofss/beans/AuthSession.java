package com.ofss.beans;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(
        name = "AUTH_SESSION",
        schema = "SAFEPAY_OWNER")
@SequenceGenerator(
        name = "authSessionSequence",
        sequenceName = "SAFEPAY_OWNER.SEQ_AUTH_SESSION_ID",
        allocationSize = 1)
public class AuthSession {

    @Id
    @GeneratedValue(
            strategy = GenerationType.SEQUENCE,
            generator = "authSessionSequence")
    @Column(
            name = "SESSION_ID",
            nullable = false,
            updatable = false)
    private Long sessionId;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false)
    @JoinColumn(
            name = "USER_ID",
            nullable = false,
            updatable = false)
    private User user;

    @Column(
            name = "TOKEN_FAMILY_KEY",
            nullable = false,
            length = 36,
            updatable = false)
    private String tokenFamilyKey;

    @Column(
            name = "REFRESH_TOKEN_HASH",
            nullable = false,
            length = 255,
            updatable = false)
    private String refreshTokenHash;

    @Column(
            name = "CREATED_AT",
            nullable = false,
            updatable = false)
    private OffsetDateTime createdAt;

    @Column(
            name = "EXPIRES_AT",
            nullable = false,
            updatable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "LAST_USED_AT")
    private OffsetDateTime lastUsedAt;

    @Column(name = "REVOKED_AT")
    private OffsetDateTime revokedAt;

    @Column(
            name = "REVOCATION_REASON",
            length = 60)
    private String revocationReason;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "REPLACED_BY_SESSION_ID",
            unique = true)
    private AuthSession replacedBySession;

    @Version
    @Column(
            name = "VERSION_NO",
            nullable = false,
            precision = 19,
            scale = 0)
    private long versionNo;

    protected AuthSession() {
        // Required by JPA.
    }

    public static AuthSession issue(
            User user,
            String tokenFamilyKey,
            String refreshTokenHash,
            OffsetDateTime createdAt,
            OffsetDateTime expiresAt) {

        Objects.requireNonNull(user, "user is required");

        if (user.getUserId() == null) {
            throw new IllegalArgumentException(
                    "user must already be persisted");
        }

        String normalizedFamilyKey = requireText(
                tokenFamilyKey,
                "tokenFamilyKey",
                36);

        String normalizedTokenHash = requireText(
                refreshTokenHash,
                "refreshTokenHash",
                255);

        OffsetDateTime normalizedCreatedAt =
                requireUtcTimestamp(
                        createdAt,
                        "createdAt");

        OffsetDateTime normalizedExpiresAt =
                requireUtcTimestamp(
                        expiresAt,
                        "expiresAt");

        if (!normalizedExpiresAt.isAfter(
                normalizedCreatedAt)) {
            throw new IllegalArgumentException(
                    "expiresAt must be after createdAt");
        }

        AuthSession session = new AuthSession();

        session.user = user;
        session.tokenFamilyKey = normalizedFamilyKey;
        session.refreshTokenHash = normalizedTokenHash;
        session.createdAt = normalizedCreatedAt;
        session.expiresAt = normalizedExpiresAt;

        return session;
    }

    public void markUsedAt(OffsetDateTime usedAt) {
        OffsetDateTime timestamp = requireUtcTimestamp(
                usedAt,
                "usedAt");

        if (!isActiveAt(timestamp)) {
            throw new IllegalStateException(
                    "Only an active session can be used");
        }

        if (lastUsedAt != null
                && timestamp.isBefore(lastUsedAt)) {
            throw new IllegalArgumentException(
                    "usedAt cannot be earlier than lastUsedAt");
        }

        lastUsedAt = timestamp;
    }

    public void revoke(
            String reason,
            OffsetDateTime revokedAt) {

        if (this.revokedAt != null) {
            throw new IllegalStateException(
                    "Session is already revoked");
        }

        String normalizedReason = requireText(
                reason,
                "revocationReason",
                60).toUpperCase(Locale.ROOT);

        OffsetDateTime timestamp = requireUtcTimestamp(
                revokedAt,
                "revokedAt");

        if (timestamp.isBefore(createdAt)) {
            throw new IllegalArgumentException(
                    "revokedAt cannot be before createdAt");
        }

        this.revokedAt = timestamp;
        this.revocationReason = normalizedReason;
    }

    public void rotateTo(
            AuthSession replacement,
            String reason,
            OffsetDateTime revokedAt) {

        Objects.requireNonNull(
                replacement,
                "replacement session is required");

        if (replacement.getSessionId() == null) {
            throw new IllegalArgumentException(
                    "replacement session must already be persisted");
        }

        if (Objects.equals(
                sessionId,
                replacement.getSessionId())) {
            throw new IllegalArgumentException(
                    "A session cannot replace itself");
        }

        if (!Objects.equals(
                user.getUserId(),
                replacement.getUser().getUserId())) {
            throw new IllegalArgumentException(
                    "Replacement must belong to the same user");
        }

        if (!tokenFamilyKey.equals(
                replacement.getTokenFamilyKey())) {
            throw new IllegalArgumentException(
                    "Replacement must belong to the same token family");
        }

        if (replacement.getRevokedAt() != null) {
            throw new IllegalArgumentException(
                    "Replacement session must not be revoked");
        }

        revoke(reason, revokedAt);
        replacedBySession = replacement;
    }

    public boolean isActiveAt(OffsetDateTime instant) {
        OffsetDateTime timestamp = requireUtcTimestamp(
                instant,
                "instant");

        return revokedAt == null
                && timestamp.isBefore(expiresAt);
    }

    private static String requireText(
            String value,
            String fieldName,
            int maximumLength) {

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + " is required");
        }

        String normalizedValue = value.trim();

        if (normalizedValue.length() > maximumLength) {
            throw new IllegalArgumentException(
                    fieldName + " has an invalid length");
        }

        return normalizedValue;
    }

    private static OffsetDateTime requireUtcTimestamp(
            OffsetDateTime value,
            String fieldName) {

        return Objects.requireNonNull(
                value,
                fieldName + " is required")
                .withOffsetSameInstant(ZoneOffset.UTC);
    }

    public Long getSessionId() {
        return sessionId;
    }

    public User getUser() {
        return user;
    }

    public String getTokenFamilyKey() {
        return tokenFamilyKey;
    }

    public String getRefreshTokenHash() {
        return refreshTokenHash;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public OffsetDateTime getLastUsedAt() {
        return lastUsedAt;
    }

    public OffsetDateTime getRevokedAt() {
        return revokedAt;
    }

    public String getRevocationReason() {
        return revocationReason;
    }

    public AuthSession getReplacedBySession() {
        return replacedBySession;
    }

    public long getVersionNo() {
        return versionNo;
    }
}