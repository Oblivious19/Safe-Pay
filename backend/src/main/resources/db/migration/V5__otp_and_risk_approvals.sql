--------------------------------------------------------------------------------
-- SafePay V1
-- Versioned migration: V5
-- OTP step-up verification and Risk Officer approval workflow
--------------------------------------------------------------------------------

--------------------------------------------------------------------------------
-- 1. Sequences
--------------------------------------------------------------------------------

CREATE SEQUENCE SEQ_PAYMENT_OTP_CHALLENGE_ID
    START WITH 1
    INCREMENT BY 1
    MINVALUE 1
    NOMAXVALUE
    NOCYCLE
    NOCACHE
    NOORDER;

CREATE SEQUENCE SEQ_MAKER_CHECKER_APPROVAL_ID
    START WITH 1
    INCREMENT BY 1
    MINVALUE 1
    NOMAXVALUE
    NOCYCLE
    NOCACHE
    NOORDER;

--------------------------------------------------------------------------------
-- 2. Payment OTP challenge
--------------------------------------------------------------------------------

CREATE TABLE PAYMENT_OTP_CHALLENGE (
    otp_challenge_id        NUMBER(19, 0)          NOT NULL,
    transaction_id          NUMBER(19, 0)          NOT NULL,
    customer_user_id        NUMBER(19, 0)          NOT NULL,

    challenge_purpose       VARCHAR2(40 CHAR)
                            DEFAULT 'VERY_HIGH_PAYMENT'
                                                    NOT NULL,
    delivery_channel        VARCHAR2(20 CHAR)
                            DEFAULT 'SIMULATED'     NOT NULL,

    otp_hash                VARCHAR2(255 CHAR)     NOT NULL,
    status                  VARCHAR2(20 CHAR)
                            DEFAULT 'PENDING'       NOT NULL,

    attempt_count           NUMBER(3, 0)
                            DEFAULT 0               NOT NULL,
    max_attempts            NUMBER(3, 0)
                            DEFAULT 3               NOT NULL,

    expires_at              TIMESTAMP(6) WITH TIME ZONE
                                                    NOT NULL,
    verified_at             TIMESTAMP(6) WITH TIME ZONE,
    invalidated_at          TIMESTAMP(6) WITH TIME ZONE,

    created_at              TIMESTAMP(6) WITH TIME ZONE
                            DEFAULT SYSTIMESTAMP   NOT NULL,
    updated_at              TIMESTAMP(6) WITH TIME ZONE
                            DEFAULT SYSTIMESTAMP   NOT NULL,

    version_no              NUMBER(10, 0)
                            DEFAULT 0               NOT NULL,

    CONSTRAINT PK_PAYMENT_OTP_CHALLENGE
        PRIMARY KEY (otp_challenge_id),

    CONSTRAINT FK_PAYMENT_OTP_TRANSACTION
        FOREIGN KEY (transaction_id)
        REFERENCES PAYMENT_TRANSACTION (transaction_id),

    CONSTRAINT FK_PAYMENT_OTP_CUSTOMER
        FOREIGN KEY (customer_user_id)
        REFERENCES APP_USER (user_id),

    CONSTRAINT CK_PAYMENT_OTP_PURPOSE
        CHECK (challenge_purpose = 'VERY_HIGH_PAYMENT'),

    CONSTRAINT CK_PAYMENT_OTP_CHANNEL
        CHECK (delivery_channel = 'SIMULATED'),

    CONSTRAINT CK_PAYMENT_OTP_HASH
        CHECK (TRIM(otp_hash) IS NOT NULL),

    CONSTRAINT CK_PAYMENT_OTP_STATUS
        CHECK (
            status IN (
                'PENDING',
                'VERIFIED',
                'EXPIRED',
                'LOCKED',
                'CANCELLED'
            )
        ),

    CONSTRAINT CK_PAYMENT_OTP_ATTEMPTS
        CHECK (
            max_attempts >= 1
            AND attempt_count BETWEEN 0 AND max_attempts
        ),

    CONSTRAINT CK_PAYMENT_OTP_EXPIRY
        CHECK (expires_at > created_at),

    CONSTRAINT CK_PAYMENT_OTP_VERIFIED_AT
        CHECK (
            verified_at IS NULL
            OR verified_at >= created_at
        ),

    CONSTRAINT CK_PAYMENT_OTP_INVALIDATED_AT
        CHECK (
            invalidated_at IS NULL
            OR invalidated_at >= created_at
        ),

    CONSTRAINT CK_PAYMENT_OTP_UPDATED_AT
        CHECK (updated_at >= created_at),

    CONSTRAINT CK_PAYMENT_OTP_VERSION
        CHECK (version_no >= 0),

    CONSTRAINT CK_PAYMENT_OTP_LIFECYCLE
        CHECK (
            (
                status = 'PENDING'
                AND verified_at IS NULL
                AND invalidated_at IS NULL
                AND attempt_count < max_attempts
            )
            OR
            (
                status = 'VERIFIED'
                AND verified_at IS NOT NULL
                AND invalidated_at IS NULL
            )
            OR
            (
                status = 'LOCKED'
                AND verified_at IS NULL
                AND invalidated_at IS NOT NULL
                AND attempt_count = max_attempts
            )
            OR
            (
                status IN ('EXPIRED', 'CANCELLED')
                AND verified_at IS NULL
                AND invalidated_at IS NOT NULL
            )
        )
);

--------------------------------------------------------------------------------
-- Only one pending OTP challenge may exist for a transaction.
-- Previous challenges must be VERIFIED, EXPIRED, LOCKED or CANCELLED first.
--------------------------------------------------------------------------------

CREATE UNIQUE INDEX UK_PAYMENT_OTP_PENDING_TX
    ON PAYMENT_OTP_CHALLENGE (
        CASE
            WHEN status = 'PENDING'
            THEN transaction_id
            ELSE NULL
        END
    );

CREATE INDEX IDX_PAYMENT_OTP_TX_CREATED
    ON PAYMENT_OTP_CHALLENGE (
        transaction_id,
        created_at
    );

CREATE INDEX IDX_PAYMENT_OTP_CUSTOMER_STATUS
    ON PAYMENT_OTP_CHALLENGE (
        customer_user_id,
        status
    );

CREATE INDEX IDX_PAYMENT_OTP_STATUS_EXPIRY
    ON PAYMENT_OTP_CHALLENGE (
        status,
        expires_at
    );

--------------------------------------------------------------------------------
-- 3. Maker-checker / Risk Officer approval
--------------------------------------------------------------------------------

CREATE TABLE MAKER_CHECKER_APPROVAL (
    approval_id             NUMBER(19, 0)          NOT NULL,
    transaction_id          NUMBER(19, 0)          NOT NULL,
    review_round            NUMBER(5, 0)
                            DEFAULT 1               NOT NULL,

    customer_user_id        NUMBER(19, 0)          NOT NULL,
    assigned_risk_officer_id
                            NUMBER(19, 0),

    status                  VARCHAR2(30 CHAR)
                            DEFAULT 'PENDING'       NOT NULL,
    decision_reason         VARCHAR2(1000 CHAR),

    requested_at            TIMESTAMP(6) WITH TIME ZONE
                            DEFAULT SYSTIMESTAMP   NOT NULL,
    claimed_at              TIMESTAMP(6) WITH TIME ZONE,
    decided_at              TIMESTAMP(6) WITH TIME ZONE,
    decided_by_user_id      NUMBER(19, 0),

    updated_at              TIMESTAMP(6) WITH TIME ZONE
                            DEFAULT SYSTIMESTAMP   NOT NULL,

    version_no              NUMBER(10, 0)
                            DEFAULT 0               NOT NULL,

    CONSTRAINT PK_MAKER_CHECKER_APPROVAL
        PRIMARY KEY (approval_id),

    CONSTRAINT UK_MAKER_CHECKER_TX_ROUND
        UNIQUE (transaction_id, review_round),

    CONSTRAINT FK_MAKER_CHECKER_TRANSACTION
        FOREIGN KEY (transaction_id)
        REFERENCES PAYMENT_TRANSACTION (transaction_id),

    CONSTRAINT FK_MAKER_CHECKER_CUSTOMER
        FOREIGN KEY (customer_user_id)
        REFERENCES APP_USER (user_id),

    CONSTRAINT FK_MAKER_CHECKER_ASSIGNEE
        FOREIGN KEY (assigned_risk_officer_id)
        REFERENCES APP_USER (user_id),

    CONSTRAINT FK_MAKER_CHECKER_DECIDER
        FOREIGN KEY (decided_by_user_id)
        REFERENCES APP_USER (user_id),

    CONSTRAINT CK_MAKER_CHECKER_ROUND
        CHECK (review_round >= 1),

    CONSTRAINT CK_MAKER_CHECKER_STATUS
        CHECK (
            status IN (
                'PENDING',
                'IN_REVIEW',
                'APPROVED',
                'REJECTED',
                'VERIFICATION_REQUESTED',
                'ESCALATED',
                'CANCELLED'
            )
        ),

    CONSTRAINT CK_MAKER_CHECKER_REASON
        CHECK (
            status NOT IN (
                'REJECTED',
                'VERIFICATION_REQUESTED',
                'ESCALATED'
            )
            OR TRIM(decision_reason) IS NOT NULL
        ),

    CONSTRAINT CK_MAKER_CHECKER_LIFECYCLE
        CHECK (
            (
                status = 'PENDING'
                AND claimed_at IS NULL
                AND decided_at IS NULL
                AND decided_by_user_id IS NULL
            )
            OR
            (
                status = 'IN_REVIEW'
                AND assigned_risk_officer_id IS NOT NULL
                AND claimed_at IS NOT NULL
                AND decided_at IS NULL
                AND decided_by_user_id IS NULL
            )
            OR
            (
                status IN (
                    'APPROVED',
                    'REJECTED',
                    'VERIFICATION_REQUESTED',
                    'ESCALATED'
                )
                AND assigned_risk_officer_id IS NOT NULL
                AND claimed_at IS NOT NULL
                AND decided_at IS NOT NULL
                AND decided_by_user_id IS NOT NULL
            )
            OR
            (
                status = 'CANCELLED'
                AND decided_at IS NOT NULL
                AND decided_by_user_id IS NOT NULL
            )
        ),

    CONSTRAINT CK_MAKER_CHECKER_SEPARATION
        CHECK (
            status NOT IN (
                'APPROVED',
                'REJECTED',
                'VERIFICATION_REQUESTED',
                'ESCALATED'
            )
            OR decided_by_user_id <> customer_user_id
        ),

    CONSTRAINT CK_MAKER_CHECKER_CANCEL_ACTOR
        CHECK (
            status <> 'CANCELLED'
            OR decided_by_user_id = customer_user_id
        ),

    CONSTRAINT CK_MAKER_CHECKER_CLAIMED_AT
        CHECK (
            claimed_at IS NULL
            OR claimed_at >= requested_at
        ),

    CONSTRAINT CK_MAKER_CHECKER_DECIDED_AT
        CHECK (
            decided_at IS NULL
            OR decided_at >= requested_at
        ),

    CONSTRAINT CK_MAKER_CHECKER_TIME_ORDER
        CHECK (
            claimed_at IS NULL
            OR decided_at IS NULL
            OR decided_at >= claimed_at
        ),

    CONSTRAINT CK_MAKER_CHECKER_UPDATED_AT
        CHECK (updated_at >= requested_at),

    CONSTRAINT CK_MAKER_CHECKER_VERSION
        CHECK (version_no >= 0)
);

--------------------------------------------------------------------------------
-- Only one open approval round may exist for a transaction.
-- VERIFICATION_REQUESTED and ESCALATED close the current round, allowing a
-- later review round to preserve the earlier decision history.
--------------------------------------------------------------------------------

CREATE UNIQUE INDEX UK_MAKER_CHECKER_OPEN_TX
    ON MAKER_CHECKER_APPROVAL (
        CASE
            WHEN status IN ('PENDING', 'IN_REVIEW')
            THEN transaction_id
            ELSE NULL
        END
    );

CREATE INDEX IDX_MAKER_CHECKER_QUEUE
    ON MAKER_CHECKER_APPROVAL (
        status,
        requested_at
    );

CREATE INDEX IDX_MAKER_CHECKER_ASSIGNEE
    ON MAKER_CHECKER_APPROVAL (
        assigned_risk_officer_id,
        status
    );

CREATE INDEX IDX_MAKER_CHECKER_CUSTOMER
    ON MAKER_CHECKER_APPROVAL (
        customer_user_id,
        requested_at
    );

--------------------------------------------------------------------------------
-- 4. Terminal-record protection
--------------------------------------------------------------------------------

CREATE OR REPLACE TRIGGER TRG_PAYMENT_OTP_TERMINAL_LOCK
    BEFORE UPDATE OR DELETE
    ON PAYMENT_OTP_CHALLENGE
    FOR EACH ROW
BEGIN
    IF DELETING THEN
        RAISE_APPLICATION_ERROR(
            -20042,
            'OTP challenge records cannot be deleted'
        );
    END IF;

    IF UPDATING
       AND :OLD.status IN (
           'VERIFIED',
           'EXPIRED',
           'LOCKED',
           'CANCELLED'
       )
    THEN
        RAISE_APPLICATION_ERROR(
            -20043,
            'A terminal OTP challenge cannot be modified'
        );
    END IF;
END;
/

CREATE OR REPLACE TRIGGER TRG_MAKER_CHECKER_FINAL_LOCK
    BEFORE UPDATE OR DELETE
    ON MAKER_CHECKER_APPROVAL
    FOR EACH ROW
BEGIN
    IF DELETING THEN
        RAISE_APPLICATION_ERROR(
            -20044,
            'Maker-checker approval records cannot be deleted'
        );
    END IF;

    IF UPDATING
       AND :OLD.status IN (
           'APPROVED',
           'REJECTED',
           'VERIFICATION_REQUESTED',
           'ESCALATED',
           'CANCELLED'
       )
    THEN
        RAISE_APPLICATION_ERROR(
            -20045,
            'A completed approval round cannot be modified'
        );
    END IF;
END;
/