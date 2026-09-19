--------------------------------------------------------------------------------
-- SafePay V1
-- Versioned migration: V4
-- Transaction foundation, amount-risk evidence and request idempotency
--------------------------------------------------------------------------------

--------------------------------------------------------------------------------
-- 1. Sequences
--------------------------------------------------------------------------------

CREATE SEQUENCE SEQ_PAYMENT_TRANSACTION_ID
    START WITH 1
    INCREMENT BY 1
    MINVALUE 1
    NOMAXVALUE
    NOCYCLE
    NOCACHE
    NOORDER;

CREATE SEQUENCE SEQ_TX_RISK_FACTOR_ID
    START WITH 1
    INCREMENT BY 1
    MINVALUE 1
    NOMAXVALUE
    NOCYCLE
    NOCACHE
    NOORDER;

CREATE SEQUENCE SEQ_IDEMPOTENCY_RECORD_ID
    START WITH 1
    INCREMENT BY 1
    MINVALUE 1
    NOMAXVALUE
    NOCYCLE
    NOCACHE
    NOORDER;

--------------------------------------------------------------------------------
-- 2. Payment transaction
--------------------------------------------------------------------------------

CREATE TABLE PAYMENT_TRANSACTION (
    transaction_id          NUMBER(19, 0)          NOT NULL,
    transaction_reference   VARCHAR2(64 CHAR)      NOT NULL,

    customer_user_id        NUMBER(19, 0)          NOT NULL,
    source_account_id       NUMBER(19, 0)          NOT NULL,
    beneficiary_id          NUMBER(19, 0)          NOT NULL,

    amount                  NUMBER(18, 2)          NOT NULL,
    currency_code           VARCHAR2(3 CHAR)
                            DEFAULT 'INR'          NOT NULL,
    purpose                 VARCHAR2(280 CHAR),
    customer_reference      VARCHAR2(100 CHAR),

    state                   VARCHAR2(32 CHAR)      NOT NULL,
    terminal_reason_code    VARCHAR2(64 CHAR),

    version_no              NUMBER(10, 0)
                            DEFAULT 0              NOT NULL,

    reserved_amount         NUMBER(18, 2)
                            DEFAULT 0              NOT NULL,
    reserved_at             TIMESTAMP(6) WITH TIME ZONE,
    reservation_ended_at    TIMESTAMP(6) WITH TIME ZONE,

    risk_policy_id          NUMBER(19, 0),
    risk_policy_band_id     NUMBER(19, 0),
    protection_policy_id    NUMBER(19, 0),

    risk_tier               VARCHAR2(20 CHAR),
    risk_score              NUMBER(5, 2),
    policy_version          NUMBER(10, 0),
    matched_band_code       VARCHAR2(50 CHAR),
    protection_seconds      NUMBER(10, 0),
    risk_explanation        VARCHAR2(1000 CHAR),
    risk_assessed_at        TIMESTAMP(6) WITH TIME ZONE,

    created_at              TIMESTAMP(6) WITH TIME ZONE
                            DEFAULT SYSTIMESTAMP   NOT NULL,
    authorized_at           TIMESTAMP(6) WITH TIME ZONE,
    protected_until         TIMESTAMP(6) WITH TIME ZONE,
    verification_completed_at
                            TIMESTAMP(6) WITH TIME ZONE,
    released_at             TIMESTAMP(6) WITH TIME ZONE,
    settled_at              TIMESTAMP(6) WITH TIME ZONE,
    cancelled_at            TIMESTAMP(6) WITH TIME ZONE,
    failed_at               TIMESTAMP(6) WITH TIME ZONE,
    updated_at              TIMESTAMP(6) WITH TIME ZONE
                            DEFAULT SYSTIMESTAMP   NOT NULL,

    CONSTRAINT PK_PAYMENT_TRANSACTION
        PRIMARY KEY (transaction_id),

    CONSTRAINT UK_PAYMENT_TX_REFERENCE
        UNIQUE (transaction_reference),

    CONSTRAINT FK_PAYMENT_TX_CUSTOMER
        FOREIGN KEY (customer_user_id)
        REFERENCES APP_USER (user_id),

    CONSTRAINT FK_PAYMENT_TX_SOURCE_ACCOUNT
        FOREIGN KEY (source_account_id)
        REFERENCES ACCOUNT (account_id),

    CONSTRAINT FK_PAYMENT_TX_BENEFICIARY
        FOREIGN KEY (beneficiary_id)
        REFERENCES BENEFICIARY (beneficiary_id),

    CONSTRAINT FK_PAYMENT_TX_RISK_POLICY
        FOREIGN KEY (risk_policy_id)
        REFERENCES RISK_POLICY (risk_policy_id),

    CONSTRAINT FK_PAYMENT_TX_RISK_BAND
        FOREIGN KEY (risk_policy_band_id)
        REFERENCES RISK_POLICY_BAND (risk_policy_band_id),

    CONSTRAINT FK_PAYMENT_TX_PROTECTION
        FOREIGN KEY (protection_policy_id)
        REFERENCES PROTECTION_POLICY (protection_policy_id),

    CONSTRAINT CK_PAYMENT_TX_REFERENCE
        CHECK (TRIM(transaction_reference) IS NOT NULL),

    CONSTRAINT CK_PAYMENT_TX_AMOUNT
        CHECK (amount >= 1.00),

    CONSTRAINT CK_PAYMENT_TX_CURRENCY
        CHECK (currency_code = 'INR'),

    CONSTRAINT CK_PAYMENT_TX_STATE
        CHECK (
            state IN (
                'CREATED',
                'AUTHORIZED',
                'RISK_ASSESSED',
                'PROTECTED',
                'VERIFICATION_REQUIRED',
                'PENDING_RISK_REVIEW',
                'RELEASED',
                'SETTLED',
                'CANCELLED',
                'FAILED'
            )
        ),

    CONSTRAINT CK_PAYMENT_TX_REASON_CODE
        CHECK (
            terminal_reason_code IS NULL
            OR terminal_reason_code =
                UPPER(TRIM(terminal_reason_code))
        ),

    CONSTRAINT CK_PAYMENT_TX_VERSION
        CHECK (version_no >= 0),

    CONSTRAINT CK_PAYMENT_TX_RESERVED
        CHECK (
            reserved_amount = 0
            OR reserved_amount = amount
        ),

    CONSTRAINT CK_PAYMENT_TX_RISK_TIER
        CHECK (
            risk_tier IS NULL
            OR risk_tier IN (
                'LOW',
                'MEDIUM',
                'HIGH',
                'VERY_HIGH'
            )
        ),

    CONSTRAINT CK_PAYMENT_TX_RISK_SCORE
        CHECK (
            risk_score IS NULL
            OR risk_score BETWEEN 0 AND 100
        ),

    CONSTRAINT CK_PAYMENT_TX_POLICY_VERSION
        CHECK (
            policy_version IS NULL
            OR policy_version >= 1
        ),

    CONSTRAINT CK_PAYMENT_TX_BAND_CODE
        CHECK (
            matched_band_code IS NULL
            OR matched_band_code =
                UPPER(TRIM(matched_band_code))
        ),

    CONSTRAINT CK_PAYMENT_TX_PROTECTION_SECS
        CHECK (
            protection_seconds IS NULL
            OR protection_seconds >= 0
        ),

    CONSTRAINT CK_PAYMENT_TX_UPDATED_AT
        CHECK (updated_at >= created_at),

    CONSTRAINT CK_PAYMENT_TX_RESERVATION_TIME
        CHECK (
            reservation_ended_at IS NULL
            OR reserved_at IS NULL
            OR reservation_ended_at >= reserved_at
        )
);

--------------------------------------------------------------------------------
-- 3. Transaction risk-factor evidence
--------------------------------------------------------------------------------

CREATE TABLE TRANSACTION_RISK_FACTOR (
    transaction_risk_factor_id
                            NUMBER(19, 0)          NOT NULL,
    transaction_id          NUMBER(19, 0)          NOT NULL,
    risk_policy_band_id     NUMBER(19, 0)          NOT NULL,

    factor_code             VARCHAR2(50 CHAR)      NOT NULL,
    raw_value               VARCHAR2(500 CHAR)     NOT NULL,
    resulting_tier          VARCHAR2(20 CHAR)      NOT NULL,
    explanation             VARCHAR2(1000 CHAR)    NOT NULL,

    evaluated_at            TIMESTAMP(6) WITH TIME ZONE
                            DEFAULT SYSTIMESTAMP   NOT NULL,

    CONSTRAINT PK_TX_RISK_FACTOR
        PRIMARY KEY (transaction_risk_factor_id),

    CONSTRAINT FK_TX_RISK_FACTOR_TX
        FOREIGN KEY (transaction_id)
        REFERENCES PAYMENT_TRANSACTION (transaction_id),

    CONSTRAINT FK_TX_RISK_FACTOR_BAND
        FOREIGN KEY (risk_policy_band_id)
        REFERENCES RISK_POLICY_BAND (risk_policy_band_id),

    CONSTRAINT UK_TX_RISK_FACTOR_CODE
        UNIQUE (transaction_id, factor_code),

    CONSTRAINT CK_TX_RISK_FACTOR_CODE
        CHECK (factor_code = 'PAYMENT_AMOUNT'),

    CONSTRAINT CK_TX_RISK_FACTOR_RAW
        CHECK (TRIM(raw_value) IS NOT NULL),

    CONSTRAINT CK_TX_RISK_FACTOR_TIER
        CHECK (
            resulting_tier IN (
                'LOW',
                'MEDIUM',
                'HIGH',
                'VERY_HIGH'
            )
        ),

    CONSTRAINT CK_TX_RISK_FACTOR_EXPLANATION
        CHECK (TRIM(explanation) IS NOT NULL)
);

--------------------------------------------------------------------------------
-- 4. Idempotency record
--------------------------------------------------------------------------------

CREATE TABLE IDEMPOTENCY_RECORD (
    idempotency_record_id   NUMBER(19, 0)          NOT NULL,
    user_id                 NUMBER(19, 0)          NOT NULL,

    operation_code          VARCHAR2(50 CHAR)      NOT NULL,
    idempotency_key         VARCHAR2(128 CHAR)     NOT NULL,
    request_hash            VARCHAR2(64 CHAR)      NOT NULL,

    transaction_id          NUMBER(19, 0),
    status                  VARCHAR2(20 CHAR)      NOT NULL,

    http_status             NUMBER(3, 0),
    response_body           CLOB,
    correlation_id          VARCHAR2(64 CHAR)      NOT NULL,

    created_at              TIMESTAMP(6) WITH TIME ZONE
                            DEFAULT SYSTIMESTAMP   NOT NULL,
    completed_at            TIMESTAMP(6) WITH TIME ZONE,
    expires_at              TIMESTAMP(6) WITH TIME ZONE
                                                    NOT NULL,

    version_no              NUMBER(10, 0)
                            DEFAULT 0              NOT NULL,

    CONSTRAINT PK_IDEMPOTENCY_RECORD
        PRIMARY KEY (idempotency_record_id),

    CONSTRAINT UK_IDEMPOTENCY_REQUEST
        UNIQUE (
            user_id,
            operation_code,
            idempotency_key
        ),

    CONSTRAINT FK_IDEMPOTENCY_USER
        FOREIGN KEY (user_id)
        REFERENCES APP_USER (user_id),

    CONSTRAINT FK_IDEMPOTENCY_TRANSACTION
        FOREIGN KEY (transaction_id)
        REFERENCES PAYMENT_TRANSACTION (transaction_id),

    CONSTRAINT CK_IDEMPOTENCY_OPERATION
        CHECK (
            TRIM(operation_code) IS NOT NULL
            AND operation_code =
                UPPER(TRIM(operation_code))
        ),

    CONSTRAINT CK_IDEMPOTENCY_KEY
        CHECK (TRIM(idempotency_key) IS NOT NULL),

    CONSTRAINT CK_IDEMPOTENCY_HASH
        CHECK (
            LENGTH(request_hash) = 64
            AND REGEXP_LIKE(
                request_hash,
                '^[0-9A-Fa-f]{64}$'
            )
        ),

    CONSTRAINT CK_IDEMPOTENCY_STATUS
        CHECK (
            status IN (
                'IN_PROGRESS',
                'COMPLETED',
                'FAILED'
            )
        ),

    CONSTRAINT CK_IDEMPOTENCY_HTTP_STATUS
        CHECK (
            http_status IS NULL
            OR http_status BETWEEN 100 AND 599
        ),

    CONSTRAINT CK_IDEMPOTENCY_COMPLETION
        CHECK (
            (
                status = 'IN_PROGRESS'
                AND completed_at IS NULL
            )
            OR
            (
                status IN ('COMPLETED', 'FAILED')
                AND completed_at IS NOT NULL
            )
        ),

    CONSTRAINT CK_IDEMPOTENCY_COMPLETED_AT
        CHECK (
            completed_at IS NULL
            OR completed_at >= created_at
        ),

    CONSTRAINT CK_IDEMPOTENCY_EXPIRES_AT
        CHECK (expires_at > created_at),

    CONSTRAINT CK_IDEMPOTENCY_VERSION
        CHECK (version_no >= 0),

    CONSTRAINT CK_IDEMPOTENCY_CORRELATION
        CHECK (TRIM(correlation_id) IS NOT NULL)
);

--------------------------------------------------------------------------------
-- 5. Performance indexes
--------------------------------------------------------------------------------

CREATE INDEX IDX_PAYMENT_TX_CUSTOMER_TIME
    ON PAYMENT_TRANSACTION (
        customer_user_id,
        created_at
    );

CREATE INDEX IDX_PAYMENT_TX_ACCOUNT_STATE
    ON PAYMENT_TRANSACTION (
        source_account_id,
        state
    );

CREATE INDEX IDX_PAYMENT_TX_BENEFICIARY
    ON PAYMENT_TRANSACTION (
        beneficiary_id,
        created_at
    );

CREATE INDEX IDX_PAYMENT_TX_STATE_DUE
    ON PAYMENT_TRANSACTION (
        state,
        protected_until
    );

CREATE INDEX IDX_PAYMENT_TX_RISK_TIER
    ON PAYMENT_TRANSACTION (
        risk_tier,
        created_at
    );

CREATE INDEX IDX_TX_RISK_FACTOR_TX
    ON TRANSACTION_RISK_FACTOR (
        transaction_id,
        evaluated_at
    );

CREATE INDEX IDX_IDEMPOTENCY_STATUS_EXP
    ON IDEMPOTENCY_RECORD (
        status,
        expires_at
    );

CREATE INDEX IDX_IDEMPOTENCY_TRANSACTION
    ON IDEMPOTENCY_RECORD (
        transaction_id
    );

--------------------------------------------------------------------------------
-- 6. Database-level append-only protection for risk evidence
--------------------------------------------------------------------------------

CREATE OR REPLACE TRIGGER TRG_TX_RISK_FACTOR_IMMUTABLE
    BEFORE UPDATE OR DELETE
    ON TRANSACTION_RISK_FACTOR
BEGIN
    RAISE_APPLICATION_ERROR(
        -20041,
        'Transaction risk-factor evidence is append-only'
    );
END;
/