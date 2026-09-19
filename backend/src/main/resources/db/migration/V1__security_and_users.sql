-- SafePay V1
-- Migration: Identity and security foundation
-- Owner: SAFEPAY_OWNER
-- Runtime user grants are intentionally handled separately.

-----------------------------------------------------------------------
-- SEQUENCES
-----------------------------------------------------------------------

CREATE SEQUENCE SEQ_APP_ROLE_ID
    START WITH 1
    INCREMENT BY 1
    CACHE 20
    NOCYCLE;

CREATE SEQUENCE SEQ_APP_USER_ID
    START WITH 1
    INCREMENT BY 1
    CACHE 50
    NOCYCLE;

CREATE SEQUENCE SEQ_AUTH_SESSION_ID
    START WITH 1
    INCREMENT BY 1
    CACHE 50
    NOCYCLE;

-----------------------------------------------------------------------
-- APP_ROLE
-----------------------------------------------------------------------

CREATE TABLE APP_ROLE
(
    role_id       NUMBER(19,0)                 NOT NULL,
    role_code     VARCHAR2(30 CHAR)            NOT NULL,
    description   VARCHAR2(200 CHAR)           NOT NULL,
    created_at    TIMESTAMP(6) WITH TIME ZONE  DEFAULT SYSTIMESTAMP NOT NULL,

    CONSTRAINT PK_APP_ROLE
        PRIMARY KEY (role_id),

    CONSTRAINT UK_APP_ROLE_CODE
        UNIQUE (role_code),

    CONSTRAINT CK_APP_ROLE_CODE
        CHECK (
            role_code IN (
                'CUSTOMER',
                'RISK_OFFICER',
                'SYSTEM_ADMIN',
                'AUDITOR'
            )
        ),

    CONSTRAINT CK_APP_ROLE_DESCRIPTION
        CHECK (
            TRIM(description) IS NOT NULL
        )
);

-----------------------------------------------------------------------
-- APP_USER
-----------------------------------------------------------------------

CREATE TABLE APP_USER
(
    user_id                    NUMBER(19,0)                 NOT NULL,
    full_name                  VARCHAR2(120 CHAR)           NOT NULL,
    email                      VARCHAR2(254 CHAR),
    mobile_number              VARCHAR2(16 CHAR),
    password_hash              VARCHAR2(255 CHAR)           NOT NULL,
    status                     VARCHAR2(20 CHAR)            DEFAULT 'ACTIVE' NOT NULL,
    failed_login_count         NUMBER(5,0)                  DEFAULT 0 NOT NULL,
    locked_until               TIMESTAMP(6) WITH TIME ZONE,
    last_successful_login_at   TIMESTAMP(6) WITH TIME ZONE,
    last_failed_login_at       TIMESTAMP(6) WITH TIME ZONE,
    password_changed_at        TIMESTAMP(6) WITH TIME ZONE  DEFAULT SYSTIMESTAMP NOT NULL,
    security_version           NUMBER(19,0)                 DEFAULT 0 NOT NULL,
    version_no                 NUMBER(19,0)                 DEFAULT 0 NOT NULL,
    created_at                 TIMESTAMP(6) WITH TIME ZONE  DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at                 TIMESTAMP(6) WITH TIME ZONE  DEFAULT SYSTIMESTAMP NOT NULL,

    CONSTRAINT PK_APP_USER
        PRIMARY KEY (user_id),

    CONSTRAINT UK_APP_USER_EMAIL
        UNIQUE (email),

    CONSTRAINT UK_APP_USER_MOBILE
        UNIQUE (mobile_number),

    CONSTRAINT CK_APP_USER_FULL_NAME
        CHECK (
            TRIM(full_name) IS NOT NULL
            AND LENGTH(TRIM(full_name)) BETWEEN 2 AND 120
        ),

    CONSTRAINT CK_APP_USER_CONTACT
        CHECK (
            email IS NOT NULL
            OR mobile_number IS NOT NULL
        ),

    CONSTRAINT CK_APP_USER_EMAIL_NORMAL
        CHECK (
            email IS NULL
            OR email = LOWER(TRIM(email))
        ),

    CONSTRAINT CK_APP_USER_MOBILE_FORMAT
        CHECK (
            mobile_number IS NULL
            OR REGEXP_LIKE(
                mobile_number,
                '^\+[1-9][0-9]{7,14}$'
            )
        ),

    CONSTRAINT CK_APP_USER_STATUS
        CHECK (
            status IN (
                'ACTIVE',
                'LOCKED',
                'DISABLED'
            )
        ),

    CONSTRAINT CK_APP_USER_FAILED_LOGINS
        CHECK (
            failed_login_count >= 0
        ),

    CONSTRAINT CK_APP_USER_SECURITY_VER
        CHECK (
            security_version >= 0
        ),

    CONSTRAINT CK_APP_USER_VERSION
        CHECK (
            version_no >= 0
        )
);

-----------------------------------------------------------------------
-- USER_ROLE
-----------------------------------------------------------------------

CREATE TABLE USER_ROLE
(
    user_id               NUMBER(19,0)                 NOT NULL,
    role_id               NUMBER(19,0)                 NOT NULL,
    assigned_at           TIMESTAMP(6) WITH TIME ZONE  DEFAULT SYSTIMESTAMP NOT NULL,
    assigned_by_user_id   NUMBER(19,0),

    CONSTRAINT PK_USER_ROLE
        PRIMARY KEY (user_id, role_id),

    CONSTRAINT FK_USER_ROLE_USER
        FOREIGN KEY (user_id)
        REFERENCES APP_USER (user_id),

    CONSTRAINT FK_USER_ROLE_ROLE
        FOREIGN KEY (role_id)
        REFERENCES APP_ROLE (role_id),

    CONSTRAINT FK_USER_ROLE_ASSIGNED_BY
        FOREIGN KEY (assigned_by_user_id)
        REFERENCES APP_USER (user_id)
);

-----------------------------------------------------------------------
-- AUTH_SESSION
-----------------------------------------------------------------------

CREATE TABLE AUTH_SESSION
(
    session_id                NUMBER(19,0)                 NOT NULL,
    user_id                   NUMBER(19,0)                 NOT NULL,
    token_family_key          VARCHAR2(36 CHAR)            NOT NULL,
    refresh_token_hash        VARCHAR2(255 CHAR)           NOT NULL,
    created_at                TIMESTAMP(6) WITH TIME ZONE  DEFAULT SYSTIMESTAMP NOT NULL,
    expires_at                TIMESTAMP(6) WITH TIME ZONE  NOT NULL,
    last_used_at              TIMESTAMP(6) WITH TIME ZONE,
    revoked_at                TIMESTAMP(6) WITH TIME ZONE,
    revocation_reason         VARCHAR2(60 CHAR),
    replaced_by_session_id    NUMBER(19,0),
    version_no                NUMBER(19,0)                 DEFAULT 0 NOT NULL,

    CONSTRAINT PK_AUTH_SESSION
        PRIMARY KEY (session_id),

    CONSTRAINT UK_AUTH_SESSION_TOKEN_HASH
        UNIQUE (refresh_token_hash),

    CONSTRAINT UK_AUTH_SESSION_REPLACED
        UNIQUE (replaced_by_session_id),

    CONSTRAINT FK_AUTH_SESSION_USER
        FOREIGN KEY (user_id)
        REFERENCES APP_USER (user_id),

    CONSTRAINT FK_AUTH_SESSION_REPLACED
        FOREIGN KEY (replaced_by_session_id)
        REFERENCES AUTH_SESSION (session_id),

    CONSTRAINT CK_AUTH_SESSION_FAMILY
        CHECK (
            TRIM(token_family_key) IS NOT NULL
        ),

    CONSTRAINT CK_AUTH_SESSION_EXPIRY
        CHECK (
            expires_at > created_at
        ),

    CONSTRAINT CK_AUTH_SESSION_LAST_USED
        CHECK (
            last_used_at IS NULL
            OR last_used_at >= created_at
        ),

    CONSTRAINT CK_AUTH_SESSION_REVOKED_AT
        CHECK (
            revoked_at IS NULL
            OR revoked_at >= created_at
        ),

    CONSTRAINT CK_AUTH_SESSION_REVOCATION
        CHECK (
            (
                revoked_at IS NULL
                AND revocation_reason IS NULL
            )
            OR
            (
                revoked_at IS NOT NULL
                AND revocation_reason IS NOT NULL
            )
        ),

    CONSTRAINT CK_AUTH_SESSION_REPLACEMENT
        CHECK (
            replaced_by_session_id IS NULL
            OR replaced_by_session_id <> session_id
        ),

    CONSTRAINT CK_AUTH_SESSION_REPLACED_REV
        CHECK (
            replaced_by_session_id IS NULL
            OR revoked_at IS NOT NULL
        ),

    CONSTRAINT CK_AUTH_SESSION_VERSION
        CHECK (
            version_no >= 0
        )
);

-----------------------------------------------------------------------
-- INDEXES
-----------------------------------------------------------------------

CREATE INDEX IX_APP_USER_STATUS
    ON APP_USER (status);

CREATE INDEX IX_USER_ROLE_ROLE
    ON USER_ROLE (role_id);

CREATE INDEX IX_USER_ROLE_ASSIGNER
    ON USER_ROLE (assigned_by_user_id);

CREATE INDEX IX_AUTH_SESSION_USER_STATE
    ON AUTH_SESSION (user_id, revoked_at, expires_at);

CREATE INDEX IX_AUTH_SESSION_FAMILY
    ON AUTH_SESSION (token_family_key);

CREATE INDEX IX_AUTH_SESSION_EXPIRY
    ON AUTH_SESSION (expires_at);