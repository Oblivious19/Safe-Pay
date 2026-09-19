--------------------------------------------------------------------------------
-- SafePay V1
-- Versioned migration: V7
-- Immutable audit trail and durable in-app notifications
--------------------------------------------------------------------------------

--------------------------------------------------------------------------------
-- 1. Sequences
--------------------------------------------------------------------------------

CREATE SEQUENCE SEQ_AUDIT_LOG_ID
    START WITH 1
    INCREMENT BY 1
    MINVALUE 1
    NOMAXVALUE
    NOCYCLE
    NOCACHE
    NOORDER;

CREATE SEQUENCE SEQ_APP_NOTIFICATION_ID
    START WITH 1
    INCREMENT BY 1
    MINVALUE 1
    NOMAXVALUE
    NOCYCLE
    NOCACHE
    NOORDER;

--------------------------------------------------------------------------------
-- 2. Immutable audit log
--------------------------------------------------------------------------------

CREATE TABLE AUDIT_LOG (
    audit_log_id            NUMBER(19, 0)          NOT NULL,
    event_reference         VARCHAR2(64 CHAR)      NOT NULL,

    actor_user_id           NUMBER(19, 0),
    actor_type              VARCHAR2(20 CHAR)      NOT NULL,
    actor_role_code         VARCHAR2(40 CHAR),

    action_code             VARCHAR2(100 CHAR)     NOT NULL,
    entity_type             VARCHAR2(50 CHAR)      NOT NULL,
    entity_id               NUMBER(19, 0),

    transaction_id          NUMBER(19, 0),

    previous_state          VARCHAR2(40 CHAR),
    new_state               VARCHAR2(40 CHAR),

    outcome                 VARCHAR2(20 CHAR)      NOT NULL,
    reason_code             VARCHAR2(100 CHAR),

    correlation_id          VARCHAR2(64 CHAR)      NOT NULL,
    idempotency_key         VARCHAR2(128 CHAR),

    details_json            CLOB,

    occurred_at             TIMESTAMP(6) WITH TIME ZONE
                            DEFAULT SYSTIMESTAMP   NOT NULL,

    CONSTRAINT PK_AUDIT_LOG
        PRIMARY KEY (audit_log_id),

    CONSTRAINT UK_AUDIT_LOG_EVENT_REF
        UNIQUE (event_reference),

    CONSTRAINT FK_AUDIT_LOG_ACTOR
        FOREIGN KEY (actor_user_id)
        REFERENCES APP_USER (user_id),

    CONSTRAINT FK_AUDIT_LOG_TRANSACTION
        FOREIGN KEY (transaction_id)
        REFERENCES PAYMENT_TRANSACTION (transaction_id),

    CONSTRAINT CK_AUDIT_LOG_EVENT_REF
        CHECK (TRIM(event_reference) IS NOT NULL),

    CONSTRAINT CK_AUDIT_LOG_ACTOR_TYPE
        CHECK (
            actor_type IN (
                'USER',
                'SYSTEM'
            )
        ),

    CONSTRAINT CK_AUDIT_LOG_ACTOR
        CHECK (
            (
                actor_type = 'USER'
                AND actor_user_id IS NOT NULL
            )
            OR
            (
                actor_type = 'SYSTEM'
                AND actor_user_id IS NULL
            )
        ),

    CONSTRAINT CK_AUDIT_LOG_ROLE_CODE
        CHECK (
            actor_role_code IS NULL
            OR actor_role_code =
                UPPER(TRIM(actor_role_code))
        ),

    CONSTRAINT CK_AUDIT_LOG_ACTION
        CHECK (
            TRIM(action_code) IS NOT NULL
            AND action_code =
                UPPER(TRIM(action_code))
        ),

    CONSTRAINT CK_AUDIT_LOG_ENTITY_TYPE
        CHECK (
            TRIM(entity_type) IS NOT NULL
            AND entity_type =
                UPPER(TRIM(entity_type))
        ),

    CONSTRAINT CK_AUDIT_LOG_PREVIOUS_STATE
        CHECK (
            previous_state IS NULL
            OR previous_state =
                UPPER(TRIM(previous_state))
        ),

    CONSTRAINT CK_AUDIT_LOG_NEW_STATE
        CHECK (
            new_state IS NULL
            OR new_state =
                UPPER(TRIM(new_state))
        ),

    CONSTRAINT CK_AUDIT_LOG_OUTCOME
        CHECK (
            outcome IN (
                'SUCCESS',
                'DENIED',
                'FAILED'
            )
        ),

    CONSTRAINT CK_AUDIT_LOG_REASON
        CHECK (
            reason_code IS NULL
            OR reason_code =
                UPPER(TRIM(reason_code))
        ),

    CONSTRAINT CK_AUDIT_LOG_CORRELATION
        CHECK (TRIM(correlation_id) IS NOT NULL),

    CONSTRAINT CK_AUDIT_LOG_IDEMPOTENCY
        CHECK (
            idempotency_key IS NULL
            OR TRIM(idempotency_key) IS NOT NULL
        )
);

CREATE INDEX IDX_AUDIT_LOG_ACTOR_TIME
    ON AUDIT_LOG (
        actor_user_id,
        occurred_at
    );

CREATE INDEX IDX_AUDIT_LOG_TRANSACTION
    ON AUDIT_LOG (
        transaction_id,
        occurred_at
    );

CREATE INDEX IDX_AUDIT_LOG_ACTION_TIME
    ON AUDIT_LOG (
        action_code,
        occurred_at
    );

CREATE INDEX IDX_AUDIT_LOG_OUTCOME_TIME
    ON AUDIT_LOG (
        outcome,
        occurred_at
    );

CREATE INDEX IDX_AUDIT_LOG_CORRELATION
    ON AUDIT_LOG (
        correlation_id
    );

--------------------------------------------------------------------------------
-- 3. Durable in-app notification and outbox
--------------------------------------------------------------------------------

CREATE TABLE APP_NOTIFICATION (
    notification_id         NUMBER(19, 0)          NOT NULL,
    notification_reference  VARCHAR2(64 CHAR)      NOT NULL,

    recipient_user_id       NUMBER(19, 0)          NOT NULL,
    transaction_id          NUMBER(19, 0),

    notification_type       VARCHAR2(40 CHAR)      NOT NULL,
    severity                VARCHAR2(20 CHAR)      NOT NULL,

    title                   VARCHAR2(200 CHAR)     NOT NULL,
    message                 VARCHAR2(1000 CHAR)    NOT NULL,

    delivery_channel        VARCHAR2(20 CHAR)
                            DEFAULT 'IN_APP'       NOT NULL,
    delivery_status         VARCHAR2(20 CHAR)
                            DEFAULT 'PENDING'      NOT NULL,

    deduplication_key       VARCHAR2(160 CHAR)     NOT NULL,

    attempt_count           NUMBER(10, 0)
                            DEFAULT 0               NOT NULL,
    max_attempts            NUMBER(10, 0)
                            DEFAULT 5               NOT NULL,

    next_attempt_at         TIMESTAMP(6) WITH TIME ZONE,
    delivered_at            TIMESTAMP(6) WITH TIME ZONE,
    failed_at               TIMESTAMP(6) WITH TIME ZONE,
    read_at                 TIMESTAMP(6) WITH TIME ZONE,

    last_error_code         VARCHAR2(100 CHAR),
    correlation_id          VARCHAR2(64 CHAR)      NOT NULL,

    created_at              TIMESTAMP(6) WITH TIME ZONE
                            DEFAULT SYSTIMESTAMP   NOT NULL,
    updated_at              TIMESTAMP(6) WITH TIME ZONE
                            DEFAULT SYSTIMESTAMP   NOT NULL,

    version_no              NUMBER(10, 0)
                            DEFAULT 0               NOT NULL,

    CONSTRAINT PK_APP_NOTIFICATION
        PRIMARY KEY (notification_id),

    CONSTRAINT UK_APP_NOTIFICATION_REF
        UNIQUE (notification_reference),

    CONSTRAINT UK_APP_NOTIFICATION_DEDUP
        UNIQUE (
            recipient_user_id,
            deduplication_key
        ),

    CONSTRAINT FK_APP_NOTIFICATION_RECIPIENT
        FOREIGN KEY (recipient_user_id)
        REFERENCES APP_USER (user_id),

    CONSTRAINT FK_APP_NOTIFICATION_TX
        FOREIGN KEY (transaction_id)
        REFERENCES PAYMENT_TRANSACTION (transaction_id),

    CONSTRAINT CK_APP_NOTIFICATION_REF
        CHECK (TRIM(notification_reference) IS NOT NULL),

    CONSTRAINT CK_APP_NOTIFICATION_TYPE
        CHECK (
            notification_type IN (
                'PAYMENT_PROTECTED',
                'PAYMENT_RELEASED',
                'PAYMENT_CANCELLED',
                'OTP_REQUIRED',
                'OTP_VERIFIED',
                'RISK_REVIEW_PENDING',
                'RISK_REVIEW_APPROVED',
                'RISK_REVIEW_REJECTED',
                'PAYMENT_SETTLED',
                'PAYMENT_FAILED'
            )
        ),

    CONSTRAINT CK_APP_NOTIFICATION_SEVERITY
        CHECK (
            severity IN (
                'INFO',
                'WARNING',
                'CRITICAL'
            )
        ),

    CONSTRAINT CK_APP_NOTIFICATION_TITLE
        CHECK (TRIM(title) IS NOT NULL),

    CONSTRAINT CK_APP_NOTIFICATION_MESSAGE
        CHECK (TRIM(message) IS NOT NULL),

    CONSTRAINT CK_APP_NOTIFICATION_CHANNEL
        CHECK (delivery_channel = 'IN_APP'),

    CONSTRAINT CK_APP_NOTIFICATION_STATUS
        CHECK (
            delivery_status IN (
                'PENDING',
                'RETRY_PENDING',
                'DELIVERED',
                'FAILED'
            )
        ),

    CONSTRAINT CK_APP_NOTIFICATION_DEDUP_KEY
        CHECK (TRIM(deduplication_key) IS NOT NULL),

    CONSTRAINT CK_APP_NOTIFICATION_ATTEMPTS
        CHECK (
            max_attempts >= 1
            AND attempt_count BETWEEN 0 AND max_attempts
        ),

    CONSTRAINT CK_APP_NOTIFICATION_ERROR_CODE
        CHECK (
            last_error_code IS NULL
            OR last_error_code =
                UPPER(TRIM(last_error_code))
        ),

    CONSTRAINT CK_APP_NOTIFICATION_CORRELATION
        CHECK (TRIM(correlation_id) IS NOT NULL),

    CONSTRAINT CK_APP_NOTIFICATION_LIFECYCLE
        CHECK (
            (
                delivery_status = 'PENDING'
                AND attempt_count = 0
                AND next_attempt_at IS NULL
                AND delivered_at IS NULL
                AND failed_at IS NULL
                AND last_error_code IS NULL
            )
            OR
            (
                delivery_status = 'RETRY_PENDING'
                AND attempt_count >= 1
                AND attempt_count < max_attempts
                AND next_attempt_at IS NOT NULL
                AND delivered_at IS NULL
                AND failed_at IS NULL
                AND TRIM(last_error_code) IS NOT NULL
            )
            OR
            (
                delivery_status = 'DELIVERED'
                AND attempt_count >= 1
                AND attempt_count <= max_attempts
                AND next_attempt_at IS NULL
                AND delivered_at IS NOT NULL
                AND failed_at IS NULL
                AND last_error_code IS NULL
            )
            OR
            (
                delivery_status = 'FAILED'
                AND attempt_count = max_attempts
                AND next_attempt_at IS NULL
                AND delivered_at IS NULL
                AND failed_at IS NOT NULL
                AND TRIM(last_error_code) IS NOT NULL
            )
        ),

    CONSTRAINT CK_APP_NOTIFICATION_NEXT_ATTEMPT
        CHECK (
            next_attempt_at IS NULL
            OR next_attempt_at >= created_at
        ),

    CONSTRAINT CK_APP_NOTIFICATION_DELIVERED_AT
        CHECK (
            delivered_at IS NULL
            OR delivered_at >= created_at
        ),

    CONSTRAINT CK_APP_NOTIFICATION_FAILED_AT
        CHECK (
            failed_at IS NULL
            OR failed_at >= created_at
        ),

    CONSTRAINT CK_APP_NOTIFICATION_READ_AT
        CHECK (
            read_at IS NULL
            OR read_at >= created_at
        ),

    CONSTRAINT CK_APP_NOTIFICATION_UPDATED_AT
        CHECK (updated_at >= created_at),

    CONSTRAINT CK_APP_NOTIFICATION_VERSION
        CHECK (version_no >= 0)
);

CREATE INDEX IDX_APP_NOTIFICATION_RECIPIENT
    ON APP_NOTIFICATION (
        recipient_user_id,
        read_at,
        created_at
    );

CREATE INDEX IDX_APP_NOTIFICATION_DELIVERY
    ON APP_NOTIFICATION (
        delivery_status,
        next_attempt_at
    );

CREATE INDEX IDX_APP_NOTIFICATION_TX
    ON APP_NOTIFICATION (
        transaction_id,
        created_at
    );

CREATE INDEX IDX_APP_NOTIFICATION_TYPE_TIME
    ON APP_NOTIFICATION (
        notification_type,
        created_at
    );

CREATE INDEX IDX_APP_NOTIFICATION_CORRELATION
    ON APP_NOTIFICATION (
        correlation_id
    );

--------------------------------------------------------------------------------
-- 4. Audit immutability
--------------------------------------------------------------------------------

CREATE OR REPLACE TRIGGER TRG_AUDIT_LOG_IMMUTABLE
    BEFORE UPDATE OR DELETE
    ON AUDIT_LOG
BEGIN
    RAISE_APPLICATION_ERROR(
        -20056,
        'Audit log records are immutable'
    );
END;
/

--------------------------------------------------------------------------------
-- 5. Notification content and lifecycle protection
--
-- Recipient, message and business identity are immutable. Delivery, retry,
-- read tracking, updated_at and version_no remain updateable.
--------------------------------------------------------------------------------

CREATE OR REPLACE TRIGGER TRG_APP_NOTIFICATION_WRITE_GUARD
    BEFORE UPDATE OR DELETE
    ON APP_NOTIFICATION
    FOR EACH ROW
BEGIN
    IF DELETING THEN
        RAISE_APPLICATION_ERROR(
            -20057,
            'Application notifications cannot be deleted'
        );
    END IF;

    IF :NEW.notification_reference
           <> :OLD.notification_reference
       OR :NEW.recipient_user_id
           <> :OLD.recipient_user_id
       OR NVL(:NEW.transaction_id, -1)
           <> NVL(:OLD.transaction_id, -1)
       OR :NEW.notification_type
           <> :OLD.notification_type
       OR :NEW.severity
           <> :OLD.severity
       OR :NEW.title
           <> :OLD.title
       OR :NEW.message
           <> :OLD.message
       OR :NEW.delivery_channel
           <> :OLD.delivery_channel
       OR :NEW.deduplication_key
           <> :OLD.deduplication_key
       OR :NEW.max_attempts
           <> :OLD.max_attempts
       OR :NEW.correlation_id
           <> :OLD.correlation_id
       OR :NEW.created_at
           <> :OLD.created_at
    THEN
        RAISE_APPLICATION_ERROR(
            -20058,
            'Notification recipient, content and identity are immutable'
        );
    END IF;

    IF :NEW.attempt_count < :OLD.attempt_count THEN
        RAISE_APPLICATION_ERROR(
            -20059,
            'Notification attempt count cannot decrease'
        );
    END IF;

    IF :OLD.delivery_status IN ('DELIVERED', 'FAILED')
       AND :NEW.delivery_status <> :OLD.delivery_status
    THEN
        RAISE_APPLICATION_ERROR(
            -20060,
            'A terminal notification delivery status cannot change'
        );
    END IF;

    IF :OLD.read_at IS NOT NULL
       AND (
           :NEW.read_at IS NULL
           OR :NEW.read_at <> :OLD.read_at
       )
    THEN
        RAISE_APPLICATION_ERROR(
            -20061,
            'A read notification cannot be marked unread'
        );
    END IF;
END;
/