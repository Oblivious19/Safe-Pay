-- SafePay V1
-- Migration: Versioned amount-risk and protection policy
-- Owner: SAFEPAY_OWNER
-- Policy seed data will be introduced separately in Phase 1.7.

-----------------------------------------------------------------------
-- SEQUENCES
-----------------------------------------------------------------------

CREATE SEQUENCE SEQ_RISK_POLICY_ID
    START WITH 1
    INCREMENT BY 1
    CACHE 20
    NOCYCLE;

CREATE SEQUENCE SEQ_PROTECTION_POLICY_ID
    START WITH 1
    INCREMENT BY 1
    CACHE 20
    NOCYCLE;

CREATE SEQUENCE SEQ_RISK_POLICY_BAND_ID
    START WITH 1
    INCREMENT BY 1
    CACHE 20
    NOCYCLE;

-----------------------------------------------------------------------
-- RISK_POLICY
-----------------------------------------------------------------------

CREATE TABLE RISK_POLICY
(
    risk_policy_id   NUMBER(19,0)                 NOT NULL,
    policy_version   VARCHAR2(50 CHAR)            NOT NULL,
    policy_name      VARCHAR2(120 CHAR)           NOT NULL,
    algorithm_type   VARCHAR2(30 CHAR)            NOT NULL,
    currency_code    VARCHAR2(3 CHAR)             DEFAULT 'INR' NOT NULL,
    status           VARCHAR2(20 CHAR)            NOT NULL,
    effective_from   TIMESTAMP(6) WITH TIME ZONE  NOT NULL,
    effective_to     TIMESTAMP(6) WITH TIME ZONE,
    description      VARCHAR2(500 CHAR)           NOT NULL,
    created_at       TIMESTAMP(6) WITH TIME ZONE  DEFAULT SYSTIMESTAMP NOT NULL,

    CONSTRAINT PK_RISK_POLICY
        PRIMARY KEY (risk_policy_id),

    CONSTRAINT UK_RISK_POLICY_VERSION
        UNIQUE (policy_version),

    CONSTRAINT CK_RISK_POLICY_VERSION
        CHECK (
            TRIM(policy_version) IS NOT NULL
            AND policy_version = UPPER(TRIM(policy_version))
        ),

    CONSTRAINT CK_RISK_POLICY_NAME
        CHECK (
            TRIM(policy_name) IS NOT NULL
        ),

    CONSTRAINT CK_RISK_POLICY_ALGORITHM
        CHECK (
            algorithm_type = 'AMOUNT_ONLY'
        ),

    CONSTRAINT CK_RISK_POLICY_CURRENCY
        CHECK (
            currency_code = 'INR'
        ),

    CONSTRAINT CK_RISK_POLICY_STATUS
        CHECK (
            status IN (
                'DRAFT',
                'ACTIVE',
                'RETIRED'
            )
        ),

    CONSTRAINT CK_RISK_POLICY_DATES
        CHECK (
            effective_to IS NULL
            OR effective_to > effective_from
        ),

    CONSTRAINT CK_RISK_POLICY_LIFECYCLE
        CHECK (
            (
                status IN ('DRAFT', 'ACTIVE')
                AND effective_to IS NULL
            )
            OR
            (
                status = 'RETIRED'
                AND effective_to IS NOT NULL
            )
        ),

    CONSTRAINT CK_RISK_POLICY_DESCRIPTION
        CHECK (
            TRIM(description) IS NOT NULL
        )
);

-----------------------------------------------------------------------
-- Enforces at most one ACTIVE policy.
-- Oracle does not store an all-NULL entry in this single-expression index,
-- so DRAFT and RETIRED policies may coexist.
-----------------------------------------------------------------------

CREATE UNIQUE INDEX UX_RISK_POLICY_ONE_ACTIVE
    ON RISK_POLICY
    (
        CASE
            WHEN status = 'ACTIVE' THEN 1
            ELSE NULL
        END
    );

-----------------------------------------------------------------------
-- PROTECTION_POLICY
-----------------------------------------------------------------------

CREATE TABLE PROTECTION_POLICY
(
    protection_policy_id   NUMBER(19,0)                 NOT NULL,
    protection_code        VARCHAR2(50 CHAR)            NOT NULL,
    release_mode           VARCHAR2(30 CHAR)            NOT NULL,
    protection_seconds     NUMBER(10,0),
    customer_can_cancel    CHAR(1)                      NOT NULL,
    auto_release           CHAR(1)                      NOT NULL,
    otp_required           CHAR(1)                      NOT NULL,
    risk_review_required   CHAR(1)                      NOT NULL,
    description            VARCHAR2(300 CHAR)           NOT NULL,
    created_at             TIMESTAMP(6) WITH TIME ZONE  DEFAULT SYSTIMESTAMP NOT NULL,

    CONSTRAINT PK_PROTECTION_POLICY
        PRIMARY KEY (protection_policy_id),

    CONSTRAINT UK_PROTECTION_POLICY_CODE
        UNIQUE (protection_code),

    CONSTRAINT CK_PROTECTION_POLICY_CODE
        CHECK (
            TRIM(protection_code) IS NOT NULL
            AND protection_code = UPPER(TRIM(protection_code))
        ),

    CONSTRAINT CK_PROTECTION_RELEASE_MODE
        CHECK (
            release_mode IN (
                'IMMEDIATE',
                'AFTER_TIMER',
                'AFTER_REVIEW'
            )
        ),

    CONSTRAINT CK_PROTECTION_FLAGS
        CHECK (
            customer_can_cancel IN ('Y', 'N')
            AND auto_release IN ('Y', 'N')
            AND otp_required IN ('Y', 'N')
            AND risk_review_required IN ('Y', 'N')
        ),

    CONSTRAINT CK_PROTECTION_CONSISTENCY
        CHECK (
            (
                release_mode = 'IMMEDIATE'
                AND protection_seconds = 0
                AND customer_can_cancel = 'N'
                AND auto_release = 'Y'
                AND otp_required = 'N'
                AND risk_review_required = 'N'
            )
            OR
            (
                release_mode = 'AFTER_TIMER'
                AND protection_seconds > 0
                AND customer_can_cancel = 'Y'
                AND auto_release = 'Y'
                AND otp_required = 'N'
                AND risk_review_required = 'N'
            )
            OR
            (
                release_mode = 'AFTER_REVIEW'
                AND protection_seconds IS NULL
                AND customer_can_cancel = 'Y'
                AND auto_release = 'N'
                AND otp_required = 'Y'
                AND risk_review_required = 'Y'
            )
        ),

    CONSTRAINT CK_PROTECTION_DESCRIPTION
        CHECK (
            TRIM(description) IS NOT NULL
        )
);

-----------------------------------------------------------------------
-- RISK_POLICY_BAND
-----------------------------------------------------------------------

CREATE TABLE RISK_POLICY_BAND
(
    risk_policy_band_id    NUMBER(19,0)                 NOT NULL,
    risk_policy_id         NUMBER(19,0)                 NOT NULL,
    protection_policy_id   NUMBER(19,0)                 NOT NULL,
    band_code              VARCHAR2(40 CHAR)            NOT NULL,
    risk_tier              VARCHAR2(20 CHAR)            NOT NULL,
    minimum_amount         NUMBER(18,2)                 NOT NULL,
    maximum_amount         NUMBER(18,2),
    display_order          NUMBER(3,0)                  NOT NULL,
    explanation_template   VARCHAR2(500 CHAR)           NOT NULL,
    created_at             TIMESTAMP(6) WITH TIME ZONE  DEFAULT SYSTIMESTAMP NOT NULL,

    CONSTRAINT PK_RISK_POLICY_BAND
        PRIMARY KEY (risk_policy_band_id),

    CONSTRAINT UK_RISK_BAND_CODE
        UNIQUE (
            risk_policy_id,
            band_code
        ),

    CONSTRAINT UK_RISK_BAND_TIER
        UNIQUE (
            risk_policy_id,
            risk_tier
        ),

    CONSTRAINT UK_RISK_BAND_ORDER
        UNIQUE (
            risk_policy_id,
            display_order
        ),

    CONSTRAINT FK_RISK_BAND_POLICY
        FOREIGN KEY (risk_policy_id)
        REFERENCES RISK_POLICY (risk_policy_id),

    CONSTRAINT FK_RISK_BAND_PROTECTION
        FOREIGN KEY (protection_policy_id)
        REFERENCES PROTECTION_POLICY (protection_policy_id),

    CONSTRAINT CK_RISK_BAND_CODE
        CHECK (
            TRIM(band_code) IS NOT NULL
            AND band_code = UPPER(TRIM(band_code))
        ),

    CONSTRAINT CK_RISK_BAND_TIER
        CHECK (
            risk_tier IN (
                'LOW',
                'MEDIUM',
                'HIGH',
                'VERY_HIGH'
            )
        ),

    CONSTRAINT CK_RISK_BAND_MINIMUM
        CHECK (
            minimum_amount >= 1.00
        ),

    CONSTRAINT CK_RISK_BAND_MAXIMUM
        CHECK (
            maximum_amount IS NULL
            OR maximum_amount >= minimum_amount
        ),

    CONSTRAINT CK_RISK_BAND_UNBOUNDED
        CHECK (
            (
                risk_tier = 'VERY_HIGH'
                AND maximum_amount IS NULL
            )
            OR
            (
                risk_tier IN ('LOW', 'MEDIUM', 'HIGH')
                AND maximum_amount IS NOT NULL
            )
        ),

    CONSTRAINT CK_RISK_BAND_DISPLAY_ORDER
        CHECK (
            display_order BETWEEN 1 AND 999
        ),

    CONSTRAINT CK_RISK_BAND_EXPLANATION
        CHECK (
            TRIM(explanation_template) IS NOT NULL
        )
);

-----------------------------------------------------------------------
-- INDEXES
-----------------------------------------------------------------------

CREATE INDEX IX_RISK_POLICY_STATUS_EFF
    ON RISK_POLICY (status, effective_from, effective_to);

CREATE INDEX IX_PROTECTION_RELEASE_MODE
    ON PROTECTION_POLICY (release_mode);

CREATE INDEX IX_RISK_BAND_AMOUNT_LOOKUP
    ON RISK_POLICY_BAND
    (
        risk_policy_id,
        minimum_amount,
        maximum_amount
    );

CREATE INDEX IX_RISK_BAND_PROTECTION
    ON RISK_POLICY_BAND (protection_policy_id);