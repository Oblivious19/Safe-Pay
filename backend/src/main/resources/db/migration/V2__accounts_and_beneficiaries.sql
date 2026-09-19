-- SafePay V1
-- Migration: Simulated accounts and customer beneficiaries
-- Owner: SAFEPAY_OWNER

-----------------------------------------------------------------------
-- SEQUENCES
-----------------------------------------------------------------------

CREATE SEQUENCE SEQ_ACCOUNT_ID
    START WITH 1
    INCREMENT BY 1
    CACHE 50
    NOCYCLE;

CREATE SEQUENCE SEQ_BENEFICIARY_ID
    START WITH 1
    INCREMENT BY 1
    CACHE 50
    NOCYCLE;

-----------------------------------------------------------------------
-- ACCOUNT
-----------------------------------------------------------------------

CREATE TABLE ACCOUNT
(
    account_id          NUMBER(19,0)                 NOT NULL,
    owner_user_id       NUMBER(19,0),
    account_number      VARCHAR2(34 CHAR)            NOT NULL,
    account_type        VARCHAR2(30 CHAR)            NOT NULL,
    bank_name           VARCHAR2(120 CHAR)           NOT NULL,
    ifsc_code           VARCHAR2(11 CHAR),
    currency_code       VARCHAR2(3 CHAR)             DEFAULT 'INR' NOT NULL,
    current_balance     NUMBER(18,2)                 DEFAULT 0 NOT NULL,
    reserved_amount     NUMBER(18,2)                 DEFAULT 0 NOT NULL,

    available_balance   NUMBER(18,2)
        GENERATED ALWAYS AS
        (current_balance - reserved_amount) VIRTUAL,

    status              VARCHAR2(20 CHAR)            DEFAULT 'ACTIVE' NOT NULL,
    version_no          NUMBER(19,0)                 DEFAULT 0 NOT NULL,
    created_at          TIMESTAMP(6) WITH TIME ZONE  DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at          TIMESTAMP(6) WITH TIME ZONE  DEFAULT SYSTIMESTAMP NOT NULL,

    CONSTRAINT PK_ACCOUNT
        PRIMARY KEY (account_id),

    CONSTRAINT UK_ACCOUNT_NUMBER
        UNIQUE (account_number),

    CONSTRAINT FK_ACCOUNT_OWNER
        FOREIGN KEY (owner_user_id)
        REFERENCES APP_USER (user_id),

    CONSTRAINT CK_ACCOUNT_NUMBER
        CHECK (
            TRIM(account_number) IS NOT NULL
            AND account_number = TRIM(account_number)
        ),

    CONSTRAINT CK_ACCOUNT_TYPE
        CHECK (
            account_type IN (
                'SAVINGS',
                'CURRENT',
                'OUTBOUND_CLEARING',
                'OPENING_BALANCE_CONTROL'
            )
        ),

    CONSTRAINT CK_ACCOUNT_OWNERSHIP
        CHECK (
            (
                account_type IN ('SAVINGS', 'CURRENT')
                AND owner_user_id IS NOT NULL
                AND ifsc_code IS NOT NULL
            )
            OR
            (
                account_type IN (
                    'OUTBOUND_CLEARING',
                    'OPENING_BALANCE_CONTROL'
                )
                AND owner_user_id IS NULL
                AND ifsc_code IS NULL
            )
        ),

    CONSTRAINT CK_ACCOUNT_BANK_NAME
        CHECK (
            TRIM(bank_name) IS NOT NULL
        ),

    CONSTRAINT CK_ACCOUNT_IFSC
        CHECK (
            ifsc_code IS NULL
            OR
            (
                ifsc_code = UPPER(TRIM(ifsc_code))
                AND REGEXP_LIKE(
                    ifsc_code,
                    '^[A-Z]{4}0[A-Z0-9]{6}$'
                )
            )
        ),

    CONSTRAINT CK_ACCOUNT_CURRENCY
        CHECK (
            currency_code = 'INR'
        ),

    CONSTRAINT CK_ACCOUNT_CURRENT_BAL
        CHECK (
            current_balance >= 0
        ),

    CONSTRAINT CK_ACCOUNT_RESERVED
        CHECK (
            reserved_amount >= 0
        ),

    CONSTRAINT CK_ACCOUNT_RESERVE_LIMIT
        CHECK (
            reserved_amount <= current_balance
        ),

    CONSTRAINT CK_ACCOUNT_STATUS
        CHECK (
            status IN (
                'ACTIVE',
                'INACTIVE'
            )
        ),

    CONSTRAINT CK_ACCOUNT_VERSION
        CHECK (
            version_no >= 0
        )
);

-----------------------------------------------------------------------
-- BENEFICIARY
-----------------------------------------------------------------------

CREATE TABLE BENEFICIARY
(
    beneficiary_id       NUMBER(19,0)                 NOT NULL,
    owner_user_id        NUMBER(19,0)                 NOT NULL,
    beneficiary_name     VARCHAR2(120 CHAR)           NOT NULL,
    nickname             VARCHAR2(60 CHAR),
    payment_method       VARCHAR2(20 CHAR)            NOT NULL,
    bank_name            VARCHAR2(120 CHAR),
    bank_account_number  VARCHAR2(34 CHAR),
    ifsc_code            VARCHAR2(11 CHAR),
    upi_id               VARCHAR2(255 CHAR),
    relationship_label   VARCHAR2(50 CHAR),
    purpose_note         VARCHAR2(140 CHAR),
    status               VARCHAR2(20 CHAR)            DEFAULT 'ACTIVE' NOT NULL,
    version_no           NUMBER(19,0)                 DEFAULT 0 NOT NULL,
    created_at           TIMESTAMP(6) WITH TIME ZONE  DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at           TIMESTAMP(6) WITH TIME ZONE  DEFAULT SYSTIMESTAMP NOT NULL,

    CONSTRAINT PK_BENEFICIARY
        PRIMARY KEY (beneficiary_id),

    CONSTRAINT UK_BEN_OWNER_BANK
        UNIQUE (
            owner_user_id,
            bank_account_number,
            ifsc_code
        ),

    CONSTRAINT UK_BEN_OWNER_UPI
        UNIQUE (
            owner_user_id,
            upi_id
        ),

    CONSTRAINT FK_BENEFICIARY_OWNER
        FOREIGN KEY (owner_user_id)
        REFERENCES APP_USER (user_id),

    CONSTRAINT CK_BEN_NAME
        CHECK (
            TRIM(beneficiary_name) IS NOT NULL
            AND LENGTH(TRIM(beneficiary_name)) BETWEEN 2 AND 120
        ),

    CONSTRAINT CK_BEN_NICKNAME
        CHECK (
            nickname IS NULL
            OR TRIM(nickname) IS NOT NULL
        ),

    CONSTRAINT CK_BEN_PAYMENT_METHOD
        CHECK (
            payment_method IN (
                'BANK_ACCOUNT',
                'UPI'
            )
        ),

    CONSTRAINT CK_BEN_METHOD_FIELDS
        CHECK (
            (
                payment_method = 'BANK_ACCOUNT'
                AND bank_name IS NOT NULL
                AND bank_account_number IS NOT NULL
                AND ifsc_code IS NOT NULL
                AND upi_id IS NULL
            )
            OR
            (
                payment_method = 'UPI'
                AND bank_name IS NULL
                AND bank_account_number IS NULL
                AND ifsc_code IS NULL
                AND upi_id IS NOT NULL
            )
        ),

    CONSTRAINT CK_BEN_BANK_NAME
        CHECK (
            bank_name IS NULL
            OR TRIM(bank_name) IS NOT NULL
        ),

    CONSTRAINT CK_BEN_BANK_ACCOUNT
        CHECK (
            bank_account_number IS NULL
            OR
            (
                TRIM(bank_account_number) IS NOT NULL
                AND bank_account_number = TRIM(bank_account_number)
            )
        ),

    CONSTRAINT CK_BEN_IFSC
        CHECK (
            ifsc_code IS NULL
            OR
            (
                ifsc_code = UPPER(TRIM(ifsc_code))
                AND REGEXP_LIKE(
                    ifsc_code,
                    '^[A-Z]{4}0[A-Z0-9]{6}$'
                )
            )
        ),

    CONSTRAINT CK_BEN_UPI_NORMAL
        CHECK (
            upi_id IS NULL
            OR upi_id = LOWER(TRIM(upi_id))
        ),

    CONSTRAINT CK_BEN_RELATIONSHIP
        CHECK (
            relationship_label IS NULL
            OR TRIM(relationship_label) IS NOT NULL
        ),

    CONSTRAINT CK_BEN_PURPOSE
        CHECK (
            purpose_note IS NULL
            OR TRIM(purpose_note) IS NOT NULL
        ),

    CONSTRAINT CK_BEN_STATUS
        CHECK (
            status IN (
                'ACTIVE',
                'DISABLED'
            )
        ),

    CONSTRAINT CK_BEN_VERSION
        CHECK (
            version_no >= 0
        )
);

-----------------------------------------------------------------------
-- ACCOUNT INDEXES
-----------------------------------------------------------------------

CREATE INDEX IX_ACCOUNT_OWNER_STATUS
    ON ACCOUNT (owner_user_id, status);

CREATE INDEX IX_ACCOUNT_TYPE_STATUS
    ON ACCOUNT (account_type, status);

CREATE INDEX IX_ACCOUNT_STATUS_AVAILABLE
    ON ACCOUNT (status, available_balance);

-----------------------------------------------------------------------
-- BENEFICIARY INDEXES
-----------------------------------------------------------------------

CREATE INDEX IX_BEN_OWNER_STATUS
    ON BENEFICIARY (owner_user_id, status);

CREATE INDEX IX_BEN_OWNER_METHOD
    ON BENEFICIARY (owner_user_id, payment_method);