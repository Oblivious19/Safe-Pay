--------------------------------------------------------------------------------
-- SafePay V1
-- Versioned migration: V6
-- Immutable double-entry ledger and transaction exception tracking
--------------------------------------------------------------------------------

--------------------------------------------------------------------------------
-- 1. Sequences
--------------------------------------------------------------------------------

CREATE SEQUENCE SEQ_LEDGER_POSTING_ID
    START WITH 1
    INCREMENT BY 1
    MINVALUE 1
    NOMAXVALUE
    NOCYCLE
    NOCACHE
    NOORDER;

CREATE SEQUENCE SEQ_LEDGER_ENTRY_ID
    START WITH 1
    INCREMENT BY 1
    MINVALUE 1
    NOMAXVALUE
    NOCYCLE
    NOCACHE
    NOORDER;

CREATE SEQUENCE SEQ_TRANSACTION_EXCEPTION_ID
    START WITH 1
    INCREMENT BY 1
    MINVALUE 1
    NOMAXVALUE
    NOCYCLE
    NOCACHE
    NOORDER;

--------------------------------------------------------------------------------
-- 2. Ledger posting header
--------------------------------------------------------------------------------

CREATE TABLE LEDGER_POSTING (
    posting_id              NUMBER(19, 0)          NOT NULL,
    posting_reference       VARCHAR2(64 CHAR)      NOT NULL,
    posting_type            VARCHAR2(30 CHAR)      NOT NULL,

    transaction_id          NUMBER(19, 0),

    source_system           VARCHAR2(30 CHAR)
                            DEFAULT 'SAFEPAY'       NOT NULL,
    idempotency_key         VARCHAR2(128 CHAR)     NOT NULL,

    amount                  NUMBER(18, 2)          NOT NULL,
    currency_code           VARCHAR2(3 CHAR)
                            DEFAULT 'INR'           NOT NULL,
    expected_entry_count    NUMBER(3, 0)
                            DEFAULT 2               NOT NULL,

    status                  VARCHAR2(20 CHAR)
                            DEFAULT 'PENDING'       NOT NULL,

    created_at              TIMESTAMP(6) WITH TIME ZONE
                            DEFAULT SYSTIMESTAMP   NOT NULL,
    posted_at               TIMESTAMP(6) WITH TIME ZONE,
    failed_at               TIMESTAMP(6) WITH TIME ZONE,
    failure_code            VARCHAR2(100 CHAR),

    updated_at              TIMESTAMP(6) WITH TIME ZONE
                            DEFAULT SYSTIMESTAMP   NOT NULL,

    version_no              NUMBER(10, 0)
                            DEFAULT 0               NOT NULL,

    CONSTRAINT PK_LEDGER_POSTING
        PRIMARY KEY (posting_id),

    CONSTRAINT UK_LEDGER_POSTING_REFERENCE
        UNIQUE (posting_reference),

    CONSTRAINT UK_LEDGER_POSTING_IDEMPOTENCY
        UNIQUE (source_system, idempotency_key),

    CONSTRAINT UK_LEDGER_POSTING_TRANSACTION
        UNIQUE (transaction_id),

    CONSTRAINT FK_LEDGER_POSTING_TRANSACTION
        FOREIGN KEY (transaction_id)
        REFERENCES PAYMENT_TRANSACTION (transaction_id),

    CONSTRAINT CK_LEDGER_POSTING_REFERENCE
        CHECK (TRIM(posting_reference) IS NOT NULL),

    CONSTRAINT CK_LEDGER_POSTING_TYPE
        CHECK (
            posting_type IN (
                'PAYMENT_SETTLEMENT',
                'OPENING_BALANCE'
            )
        ),

    CONSTRAINT CK_LEDGER_POSTING_TX_LINK
        CHECK (
            (
                posting_type = 'PAYMENT_SETTLEMENT'
                AND transaction_id IS NOT NULL
            )
            OR
            (
                posting_type = 'OPENING_BALANCE'
                AND transaction_id IS NULL
            )
        ),

    CONSTRAINT CK_LEDGER_POSTING_SOURCE
        CHECK (source_system = 'SAFEPAY'),

    CONSTRAINT CK_LEDGER_POSTING_IDEMP_KEY
        CHECK (TRIM(idempotency_key) IS NOT NULL),

    CONSTRAINT CK_LEDGER_POSTING_AMOUNT
        CHECK (amount > 0),

    CONSTRAINT CK_LEDGER_POSTING_CURRENCY
        CHECK (currency_code = 'INR'),

    CONSTRAINT CK_LEDGER_POSTING_ENTRY_COUNT
        CHECK (expected_entry_count = 2),

    CONSTRAINT CK_LEDGER_POSTING_STATUS
        CHECK (
            status IN (
                'PENDING',
                'POSTED',
                'FAILED'
            )
        ),

    CONSTRAINT CK_LEDGER_POSTING_FAILURE_CODE
        CHECK (
            failure_code IS NULL
            OR failure_code = UPPER(TRIM(failure_code))
        ),

    CONSTRAINT CK_LEDGER_POSTING_LIFECYCLE
        CHECK (
            (
                status = 'PENDING'
                AND posted_at IS NULL
                AND failed_at IS NULL
                AND failure_code IS NULL
            )
            OR
            (
                status = 'POSTED'
                AND posted_at IS NOT NULL
                AND failed_at IS NULL
                AND failure_code IS NULL
            )
            OR
            (
                status = 'FAILED'
                AND posted_at IS NULL
                AND failed_at IS NOT NULL
                AND TRIM(failure_code) IS NOT NULL
            )
        ),

    CONSTRAINT CK_LEDGER_POSTING_POSTED_AT
        CHECK (
            posted_at IS NULL
            OR posted_at >= created_at
        ),

    CONSTRAINT CK_LEDGER_POSTING_FAILED_AT
        CHECK (
            failed_at IS NULL
            OR failed_at >= created_at
        ),

    CONSTRAINT CK_LEDGER_POSTING_UPDATED_AT
        CHECK (updated_at >= created_at),

    CONSTRAINT CK_LEDGER_POSTING_VERSION
        CHECK (version_no >= 0)
);

CREATE INDEX IDX_LEDGER_POSTING_STATUS_TIME
    ON LEDGER_POSTING (
        status,
        created_at
    );

CREATE INDEX IDX_LEDGER_POSTING_TYPE_TIME
    ON LEDGER_POSTING (
        posting_type,
        created_at
    );

--------------------------------------------------------------------------------
-- 3. Immutable ledger entries
--------------------------------------------------------------------------------

CREATE TABLE LEDGER_ENTRY (
    ledger_entry_id         NUMBER(19, 0)          NOT NULL,
    posting_id              NUMBER(19, 0)          NOT NULL,
    transaction_id          NUMBER(19, 0),

    source_system           VARCHAR2(30 CHAR)
                            DEFAULT 'SAFEPAY'       NOT NULL,
    idempotency_key         VARCHAR2(128 CHAR)     NOT NULL,

    line_number             NUMBER(2, 0)           NOT NULL,
    account_id              NUMBER(19, 0)          NOT NULL,
    entry_type              VARCHAR2(10 CHAR)      NOT NULL,

    amount                  NUMBER(18, 2)          NOT NULL,
    currency_code           VARCHAR2(3 CHAR)
                            DEFAULT 'INR'           NOT NULL,
    status                  VARCHAR2(20 CHAR)
                            DEFAULT 'POSTED'        NOT NULL,

    description             VARCHAR2(500 CHAR),

    created_at              TIMESTAMP(6) WITH TIME ZONE
                            DEFAULT SYSTIMESTAMP   NOT NULL,

    CONSTRAINT PK_LEDGER_ENTRY
        PRIMARY KEY (ledger_entry_id),

    CONSTRAINT UK_LEDGER_ENTRY_POSTING_LINE
        UNIQUE (posting_id, line_number),

    CONSTRAINT UK_LEDGER_ENTRY_POSTING_SIDE
        UNIQUE (posting_id, entry_type),

    CONSTRAINT UK_LEDGER_ENTRY_IDEMP_SIDE
        UNIQUE (
            source_system,
            idempotency_key,
            entry_type
        ),

    CONSTRAINT FK_LEDGER_ENTRY_POSTING
        FOREIGN KEY (posting_id)
        REFERENCES LEDGER_POSTING (posting_id),

    CONSTRAINT FK_LEDGER_ENTRY_TRANSACTION
        FOREIGN KEY (transaction_id)
        REFERENCES PAYMENT_TRANSACTION (transaction_id),

    CONSTRAINT FK_LEDGER_ENTRY_ACCOUNT
        FOREIGN KEY (account_id)
        REFERENCES ACCOUNT (account_id),

    CONSTRAINT CK_LEDGER_ENTRY_SOURCE
        CHECK (source_system = 'SAFEPAY'),

    CONSTRAINT CK_LEDGER_ENTRY_IDEMP_KEY
        CHECK (TRIM(idempotency_key) IS NOT NULL),

    CONSTRAINT CK_LEDGER_ENTRY_LINE
        CHECK (line_number IN (1, 2)),

    CONSTRAINT CK_LEDGER_ENTRY_TYPE
        CHECK (entry_type IN ('DEBIT', 'CREDIT')),

    CONSTRAINT CK_LEDGER_ENTRY_LINE_TYPE
        CHECK (
            (
                line_number = 1
                AND entry_type = 'DEBIT'
            )
            OR
            (
                line_number = 2
                AND entry_type = 'CREDIT'
            )
        ),

    CONSTRAINT CK_LEDGER_ENTRY_AMOUNT
        CHECK (amount > 0),

    CONSTRAINT CK_LEDGER_ENTRY_CURRENCY
        CHECK (currency_code = 'INR'),

    CONSTRAINT CK_LEDGER_ENTRY_STATUS
        CHECK (status = 'POSTED'),

    CONSTRAINT CK_LEDGER_ENTRY_DESCRIPTION
        CHECK (
            description IS NULL
            OR TRIM(description) IS NOT NULL
        )
);

CREATE INDEX IDX_LEDGER_ENTRY_TRANSACTION
    ON LEDGER_ENTRY (
        transaction_id,
        created_at
    );

CREATE INDEX IDX_LEDGER_ENTRY_ACCOUNT_TIME
    ON LEDGER_ENTRY (
        account_id,
        created_at
    );

--------------------------------------------------------------------------------
-- 4. Transaction exception and reconciliation tracking
--------------------------------------------------------------------------------

CREATE TABLE TRANSACTION_EXCEPTION (
    transaction_exception_id
                            NUMBER(19, 0)          NOT NULL,
    exception_reference     VARCHAR2(64 CHAR)      NOT NULL,

    transaction_id          NUMBER(19, 0)          NOT NULL,
    posting_id              NUMBER(19, 0),

    processing_stage        VARCHAR2(30 CHAR)      NOT NULL,
    error_code              VARCHAR2(100 CHAR)     NOT NULL,
    error_message           VARCHAR2(2000 CHAR)    NOT NULL,

    retryable_flag          CHAR(1 CHAR)
                            DEFAULT 'N'             NOT NULL,

    status                  VARCHAR2(30 CHAR)
                            DEFAULT 'OPEN'          NOT NULL,

    retry_count             NUMBER(10, 0)
                            DEFAULT 0               NOT NULL,
    next_retry_at           TIMESTAMP(6) WITH TIME ZONE,

    first_occurred_at       TIMESTAMP(6) WITH TIME ZONE
                            DEFAULT SYSTIMESTAMP   NOT NULL,
    last_occurred_at        TIMESTAMP(6) WITH TIME ZONE
                            DEFAULT SYSTIMESTAMP   NOT NULL,

    resolved_at             TIMESTAMP(6) WITH TIME ZONE,
    resolved_by_user_id     NUMBER(19, 0),
    resolution_note         VARCHAR2(2000 CHAR),

    correlation_id          VARCHAR2(64 CHAR)      NOT NULL,

    created_at              TIMESTAMP(6) WITH TIME ZONE
                            DEFAULT SYSTIMESTAMP   NOT NULL,
    updated_at              TIMESTAMP(6) WITH TIME ZONE
                            DEFAULT SYSTIMESTAMP   NOT NULL,

    version_no              NUMBER(10, 0)
                            DEFAULT 0               NOT NULL,

    CONSTRAINT PK_TRANSACTION_EXCEPTION
        PRIMARY KEY (transaction_exception_id),

    CONSTRAINT UK_TRANSACTION_EXCEPTION_REF
        UNIQUE (exception_reference),

    CONSTRAINT FK_TRANSACTION_EXCEPTION_TX
        FOREIGN KEY (transaction_id)
        REFERENCES PAYMENT_TRANSACTION (transaction_id),

    CONSTRAINT FK_TRANSACTION_EXCEPTION_POST
        FOREIGN KEY (posting_id)
        REFERENCES LEDGER_POSTING (posting_id),

    CONSTRAINT FK_TRANSACTION_EXCEPTION_RESOLVER
        FOREIGN KEY (resolved_by_user_id)
        REFERENCES APP_USER (user_id),

    CONSTRAINT CK_TRANSACTION_EXCEPTION_REF
        CHECK (TRIM(exception_reference) IS NOT NULL),

    CONSTRAINT CK_TRANSACTION_EXCEPTION_STAGE
        CHECK (
            processing_stage IN (
                'AUTHORIZATION',
                'RISK_ASSESSMENT',
                'RESERVATION',
                'OTP_VERIFICATION',
                'RISK_REVIEW',
                'RELEASE',
                'SETTLEMENT',
                'ACCOUNT_UPDATE',
                'STATE_TRANSITION',
                'SCHEDULER'
            )
        ),

    CONSTRAINT CK_TRANSACTION_EXCEPTION_CODE
        CHECK (
            TRIM(error_code) IS NOT NULL
            AND error_code = UPPER(TRIM(error_code))
        ),

    CONSTRAINT CK_TRANSACTION_EXCEPTION_MESSAGE
        CHECK (TRIM(error_message) IS NOT NULL),

    CONSTRAINT CK_TRANSACTION_EXCEPTION_RETRYABLE
        CHECK (retryable_flag IN ('Y', 'N')),

    CONSTRAINT CK_TRANSACTION_EXCEPTION_STATUS
        CHECK (
            status IN (
                'OPEN',
                'RETRY_PENDING',
                'MANUAL_REVIEW',
                'RESOLVED'
            )
        ),

    CONSTRAINT CK_TRANSACTION_EXCEPTION_RETRY_COUNT
        CHECK (retry_count >= 0),

    CONSTRAINT CK_TRANSACTION_EXCEPTION_RETRY_STATE
        CHECK (
            (
                status = 'RETRY_PENDING'
                AND retryable_flag = 'Y'
                AND next_retry_at IS NOT NULL
            )
            OR
            (
                status <> 'RETRY_PENDING'
                AND next_retry_at IS NULL
            )
        ),

    CONSTRAINT CK_TRANSACTION_EXCEPTION_TIME_ORDER
        CHECK (last_occurred_at >= first_occurred_at),

    CONSTRAINT CK_TRANSACTION_EXCEPTION_NEXT_RETRY
        CHECK (
            next_retry_at IS NULL
            OR next_retry_at >= last_occurred_at
        ),

    CONSTRAINT CK_TRANSACTION_EXCEPTION_RESOLUTION
        CHECK (
            (
                status = 'RESOLVED'
                AND resolved_at IS NOT NULL
                AND resolved_by_user_id IS NOT NULL
                AND TRIM(resolution_note) IS NOT NULL
            )
            OR
            (
                status <> 'RESOLVED'
                AND resolved_at IS NULL
                AND resolved_by_user_id IS NULL
                AND resolution_note IS NULL
            )
        ),

    CONSTRAINT CK_TRANSACTION_EXCEPTION_RESOLVED_AT
        CHECK (
            resolved_at IS NULL
            OR resolved_at >= first_occurred_at
        ),

    CONSTRAINT CK_TRANSACTION_EXCEPTION_CORRELATION
        CHECK (TRIM(correlation_id) IS NOT NULL),

    CONSTRAINT CK_TRANSACTION_EXCEPTION_UPDATED_AT
        CHECK (updated_at >= created_at),

    CONSTRAINT CK_TRANSACTION_EXCEPTION_VERSION
        CHECK (version_no >= 0)
);

CREATE INDEX IDX_TRANSACTION_EXCEPTION_TX
    ON TRANSACTION_EXCEPTION (
        transaction_id,
        status
    );

CREATE INDEX IDX_TRANSACTION_EXCEPTION_POST
    ON TRANSACTION_EXCEPTION (
        posting_id
    );

CREATE INDEX IDX_TRANSACTION_EXCEPTION_RETRY
    ON TRANSACTION_EXCEPTION (
        status,
        next_retry_at
    );

CREATE INDEX IDX_TRANSACTION_EXCEPTION_STAGE
    ON TRANSACTION_EXCEPTION (
        processing_stage,
        last_occurred_at
    );

--------------------------------------------------------------------------------
-- 5. Ledger-entry write guard
--
-- Ledger entries may only be inserted while their parent posting is PENDING.
-- Their transaction, source, idempotency key, amount and currency must match
-- the parent posting. Existing entries can never be updated or deleted.
--------------------------------------------------------------------------------

CREATE OR REPLACE TRIGGER TRG_LEDGER_ENTRY_WRITE_GUARD
    BEFORE INSERT OR UPDATE OR DELETE
    ON LEDGER_ENTRY
    FOR EACH ROW
DECLARE
    v_posting_status        LEDGER_POSTING.status%TYPE;
    v_transaction_id        LEDGER_POSTING.transaction_id%TYPE;
    v_source_system         LEDGER_POSTING.source_system%TYPE;
    v_idempotency_key       LEDGER_POSTING.idempotency_key%TYPE;
    v_amount                LEDGER_POSTING.amount%TYPE;
    v_currency_code         LEDGER_POSTING.currency_code%TYPE;
BEGIN
    IF UPDATING OR DELETING THEN
        RAISE_APPLICATION_ERROR(
            -20046,
            'Ledger entries are immutable and cannot be changed'
        );
    END IF;

    SELECT
        status,
        transaction_id,
        source_system,
        idempotency_key,
        amount,
        currency_code
    INTO
        v_posting_status,
        v_transaction_id,
        v_source_system,
        v_idempotency_key,
        v_amount,
        v_currency_code
    FROM LEDGER_POSTING
    WHERE posting_id = :NEW.posting_id;

    IF v_posting_status <> 'PENDING' THEN
        RAISE_APPLICATION_ERROR(
            -20047,
            'Ledger entries may only be added to a pending posting'
        );
    END IF;

    IF NVL(:NEW.transaction_id, -1)
           <> NVL(v_transaction_id, -1)
       OR :NEW.source_system <> v_source_system
       OR :NEW.idempotency_key <> v_idempotency_key
       OR :NEW.amount <> v_amount
       OR :NEW.currency_code <> v_currency_code
    THEN
        RAISE_APPLICATION_ERROR(
            -20048,
            'Ledger entry does not match its posting header'
        );
    END IF;

EXCEPTION
    WHEN NO_DATA_FOUND THEN
        RAISE_APPLICATION_ERROR(
            -20049,
            'Ledger posting header does not exist'
        );
END;
/

--------------------------------------------------------------------------------
-- 6. Posting finalization and terminal protection
--
-- Every posting must begin as PENDING. Before PENDING becomes POSTED, Oracle
-- verifies the complete, balanced double-entry pair. POSTED and FAILED headers
-- are terminal and cannot be edited or deleted.
--------------------------------------------------------------------------------

CREATE OR REPLACE TRIGGER TRG_LEDGER_POSTING_FINALIZE
    BEFORE INSERT OR UPDATE OR DELETE
    ON LEDGER_POSTING
    FOR EACH ROW
DECLARE
    v_entry_count           NUMBER;
    v_debit_count           NUMBER;
    v_credit_count          NUMBER;
    v_debit_total           NUMBER(18, 2);
    v_credit_total          NUMBER(18, 2);
    v_distinct_accounts     NUMBER;
    v_mismatch_count        NUMBER;
BEGIN
    IF INSERTING THEN
        IF :NEW.status <> 'PENDING' THEN
            RAISE_APPLICATION_ERROR(
                -20050,
                'A ledger posting must begin in PENDING status'
            );
        END IF;

        RETURN;
    END IF;

    IF DELETING THEN
        RAISE_APPLICATION_ERROR(
            -20051,
            'Ledger posting records cannot be deleted'
        );
    END IF;

    IF :OLD.status IN ('POSTED', 'FAILED') THEN
        RAISE_APPLICATION_ERROR(
            -20052,
            'A terminal ledger posting cannot be modified'
        );
    END IF;

    IF :NEW.posting_reference <> :OLD.posting_reference
       OR :NEW.posting_type <> :OLD.posting_type
       OR NVL(:NEW.transaction_id, -1)
            <> NVL(:OLD.transaction_id, -1)
       OR :NEW.source_system <> :OLD.source_system
       OR :NEW.idempotency_key <> :OLD.idempotency_key
       OR :NEW.amount <> :OLD.amount
       OR :NEW.currency_code <> :OLD.currency_code
       OR :NEW.expected_entry_count <> :OLD.expected_entry_count
    THEN
        RAISE_APPLICATION_ERROR(
            -20053,
            'Ledger posting financial identity cannot be modified'
        );
    END IF;

    IF :NEW.status = 'POSTED' THEN
        SELECT
            COUNT(*),
            NVL(
                SUM(
                    CASE
                        WHEN entry_type = 'DEBIT' THEN 1
                        ELSE 0
                    END
                ),
                0
            ),
            NVL(
                SUM(
                    CASE
                        WHEN entry_type = 'CREDIT' THEN 1
                        ELSE 0
                    END
                ),
                0
            ),
            NVL(
                SUM(
                    CASE
                        WHEN entry_type = 'DEBIT' THEN amount
                        ELSE 0
                    END
                ),
                0
            ),
            NVL(
                SUM(
                    CASE
                        WHEN entry_type = 'CREDIT' THEN amount
                        ELSE 0
                    END
                ),
                0
            ),
            COUNT(DISTINCT account_id),
            NVL(
                SUM(
                    CASE
                        WHEN NVL(transaction_id, -1)
                                 <> NVL(:NEW.transaction_id, -1)
                          OR source_system <> :NEW.source_system
                          OR idempotency_key <> :NEW.idempotency_key
                          OR amount <> :NEW.amount
                          OR currency_code <> :NEW.currency_code
                          OR status <> 'POSTED'
                        THEN 1
                        ELSE 0
                    END
                ),
                0
            )
        INTO
            v_entry_count,
            v_debit_count,
            v_credit_count,
            v_debit_total,
            v_credit_total,
            v_distinct_accounts,
            v_mismatch_count
        FROM LEDGER_ENTRY
        WHERE posting_id = :NEW.posting_id;

        IF v_entry_count <> :NEW.expected_entry_count
           OR v_entry_count <> 2
           OR v_debit_count <> 1
           OR v_credit_count <> 1
           OR v_debit_total <> v_credit_total
           OR v_debit_total <> :NEW.amount
           OR v_credit_total <> :NEW.amount
           OR v_distinct_accounts <> 2
           OR v_mismatch_count <> 0
        THEN
            RAISE_APPLICATION_ERROR(
                -20054,
                'Ledger posting is incomplete, mismatched or unbalanced'
            );
        END IF;
    END IF;

    IF :NEW.status = 'FAILED' THEN
        SELECT COUNT(*)
        INTO v_entry_count
        FROM LEDGER_ENTRY
        WHERE posting_id = :NEW.posting_id;

        IF v_entry_count <> 0 THEN
            RAISE_APPLICATION_ERROR(
                -20055,
                'A failed posting cannot contain posted ledger entries'
            );
        END IF;
    END IF;
END;
/