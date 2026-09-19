--------------------------------------------------------------------------------
-- SafePay V1
-- Versioned migration: V10
-- Phase 1.9 Part 2: ownership, policy tuple, state and band integrity
--------------------------------------------------------------------------------

--------------------------------------------------------------------------------
-- 1. Preflight current data before applying non-transactional Oracle DDL.
--------------------------------------------------------------------------------

DECLARE
    l_count PLS_INTEGER;

    PROCEDURE assert_zero(
        p_count   IN PLS_INTEGER,
        p_code    IN PLS_INTEGER,
        p_message IN VARCHAR2
    )
    IS
    BEGIN
        IF p_count > 0 THEN
            RAISE_APPLICATION_ERROR(p_code, p_message);
        END IF;
    END assert_zero;

    PROCEDURE validate_policy_bands(
        p_risk_policy_id IN NUMBER
    )
    IS
        l_band_count    PLS_INTEGER;
        l_invalid_count PLS_INTEGER;
    BEGIN
        SELECT COUNT(*)
          INTO l_band_count
          FROM RISK_POLICY_BAND
         WHERE risk_policy_id = p_risk_policy_id;

        IF l_band_count <> 4 THEN
            RAISE_APPLICATION_ERROR(
                -20110,
                'V10 stopped: each active policy must contain exactly four risk bands'
            );
        END IF;

        SELECT COUNT(*)
          INTO l_invalid_count
          FROM (
                SELECT
                    band.display_order,
                    band.risk_tier,
                    band.minimum_amount,
                    band.maximum_amount,
                    ROW_NUMBER() OVER (
                        ORDER BY band.display_order
                    ) AS expected_order,
                    LAG(band.maximum_amount) OVER (
                        ORDER BY band.display_order
                    ) AS previous_maximum
                FROM RISK_POLICY_BAND band
                WHERE band.risk_policy_id = p_risk_policy_id
          ) ordered_band
         WHERE ordered_band.display_order <> ordered_band.expected_order
            OR (
                ordered_band.expected_order = 1
                AND ordered_band.minimum_amount <> 1.00
            )
            OR (
                ordered_band.expected_order > 1
                AND (
                    ordered_band.previous_maximum IS NULL
                    OR ordered_band.minimum_amount <>
                        ordered_band.previous_maximum + 0.01
                )
            )
            OR (
                ordered_band.expected_order < 4
                AND ordered_band.maximum_amount IS NULL
            )
            OR (
                ordered_band.expected_order = 4
                AND ordered_band.maximum_amount IS NOT NULL
            )
            OR ordered_band.risk_tier <>
                CASE ordered_band.expected_order
                    WHEN 1 THEN 'LOW'
                    WHEN 2 THEN 'MEDIUM'
                    WHEN 3 THEN 'HIGH'
                    WHEN 4 THEN 'VERY_HIGH'
                END
            OR (
                ordered_band.expected_order = 1
                AND (
                    ordered_band.minimum_amount <> 1.00
                    OR ordered_band.maximum_amount <> 5000.00
                )
            )
            OR (
                ordered_band.expected_order = 2
                AND (
                    ordered_band.minimum_amount <> 5000.01
                    OR ordered_band.maximum_amount <> 25000.00
                )
            )
            OR (
                ordered_band.expected_order = 3
                AND (
                    ordered_band.minimum_amount <> 25000.01
                    OR ordered_band.maximum_amount <> 100000.00
                )
            )
            OR (
                ordered_band.expected_order = 4
                AND ordered_band.minimum_amount <> 100000.01
            );

        IF l_invalid_count > 0 THEN
            RAISE_APPLICATION_ERROR(
                -20111,
                'V10 stopped: active policy bands contain a gap, overlap, boundary, order or tier error'
            );
        END IF;

        SELECT COUNT(*)
          INTO l_invalid_count
          FROM RISK_POLICY_BAND band
          JOIN PROTECTION_POLICY protection
            ON protection.protection_policy_id =
               band.protection_policy_id
         WHERE band.risk_policy_id = p_risk_policy_id
           AND (
                (
                    band.risk_tier = 'LOW'
                    AND (
                        protection.release_mode <> 'IMMEDIATE'
                        OR protection.protection_seconds <> 0
                    )
                )
                OR
                (
                    band.risk_tier = 'MEDIUM'
                    AND (
                        protection.release_mode <> 'AFTER_TIMER'
                        OR protection.protection_seconds <> 10
                    )
                )
                OR
                (
                    band.risk_tier = 'HIGH'
                    AND (
                        protection.release_mode <> 'AFTER_TIMER'
                        OR protection.protection_seconds <> 60
                    )
                )
                OR
                (
                    band.risk_tier = 'VERY_HIGH'
                    AND (
                        protection.release_mode <> 'AFTER_REVIEW'
                        OR protection.protection_seconds IS NOT NULL
                    )
                )
           );

        IF l_invalid_count > 0 THEN
            RAISE_APPLICATION_ERROR(
                -20112,
                'V10 stopped: a risk tier is linked to an incompatible protection mode or duration'
            );
        END IF;
    END validate_policy_bands;
BEGIN
    SELECT COUNT(*)
      INTO l_count
      FROM PAYMENT_TRANSACTION payment
      JOIN ACCOUNT source_account
        ON source_account.account_id = payment.source_account_id
     WHERE source_account.owner_user_id IS NULL
        OR source_account.owner_user_id <> payment.customer_user_id;

    assert_zero(
        l_count,
        -20100,
        'V10 stopped: a payment references a source account owned by another user'
    );

    SELECT COUNT(*)
      INTO l_count
      FROM PAYMENT_TRANSACTION payment
      JOIN BENEFICIARY beneficiary
        ON beneficiary.beneficiary_id = payment.beneficiary_id
     WHERE beneficiary.owner_user_id <> payment.customer_user_id;

    assert_zero(
        l_count,
        -20101,
        'V10 stopped: a payment references a beneficiary owned by another user'
    );

    SELECT COUNT(*)
      INTO l_count
      FROM PAYMENT_OTP_CHALLENGE challenge
      JOIN PAYMENT_TRANSACTION payment
        ON payment.transaction_id = challenge.transaction_id
     WHERE challenge.customer_user_id <> payment.customer_user_id;

    assert_zero(
        l_count,
        -20104,
        'V10 stopped: an OTP challenge customer does not own its transaction'
    );

    SELECT COUNT(*)
      INTO l_count
      FROM RISK_REVIEW review
      JOIN PAYMENT_TRANSACTION payment
        ON payment.transaction_id = review.transaction_id
     WHERE review.customer_user_id <> payment.customer_user_id;

    assert_zero(
        l_count,
        -20105,
        'V10 stopped: a risk review customer does not own its transaction'
    );

    SELECT COUNT(*)
      INTO l_count
      FROM TRANSACTION_RISK_FACTOR factor
      JOIN PAYMENT_TRANSACTION payment
        ON payment.transaction_id = factor.transaction_id
     WHERE payment.risk_policy_band_id IS NULL
        OR factor.risk_policy_band_id <>
           payment.risk_policy_band_id;

    assert_zero(
        l_count,
        -20106,
        'V10 stopped: risk evidence does not match the transaction policy band'
    );

    SELECT COUNT(*)
      INTO l_count
      FROM PAYMENT_TRANSACTION payment
     WHERE NOT (
            (
                payment.risk_policy_id IS NULL
                AND payment.risk_policy_band_id IS NULL
                AND payment.protection_policy_id IS NULL
                AND payment.risk_tier IS NULL
                AND payment.policy_version IS NULL
                AND payment.matched_band_code IS NULL
                AND payment.protection_seconds IS NULL
                AND payment.risk_explanation IS NULL
                AND payment.risk_assessed_at IS NULL
                AND payment.risk_score IS NULL
            )
            OR
            (
                payment.risk_policy_id IS NOT NULL
                AND payment.risk_policy_band_id IS NOT NULL
                AND payment.protection_policy_id IS NOT NULL
                AND payment.risk_tier IS NOT NULL
                AND payment.policy_version IS NOT NULL
                AND payment.matched_band_code IS NOT NULL
                AND TRIM(payment.risk_explanation) IS NOT NULL
                AND payment.risk_assessed_at IS NOT NULL
                AND payment.risk_score IS NULL
            )
     );

    assert_zero(
        l_count,
        -20102,
        'V10 stopped: a payment contains a partial or invalid V1 risk snapshot'
    );

    SELECT COUNT(*)
      INTO l_count
      FROM PAYMENT_TRANSACTION payment
      LEFT JOIN RISK_POLICY policy
        ON policy.risk_policy_id = payment.risk_policy_id
       AND policy.policy_version = payment.policy_version
      LEFT JOIN RISK_POLICY_BAND band
        ON band.risk_policy_band_id =
           payment.risk_policy_band_id
       AND band.risk_policy_id =
           payment.risk_policy_id
       AND band.protection_policy_id =
           payment.protection_policy_id
       AND band.risk_tier =
           payment.risk_tier
       AND band.band_code =
           payment.matched_band_code
     WHERE payment.risk_assessed_at IS NOT NULL
       AND (
            policy.risk_policy_id IS NULL
            OR band.risk_policy_band_id IS NULL
       );

    assert_zero(
        l_count,
        -20103,
        'V10 stopped: a transaction policy snapshot does not match one canonical policy band'
    );

    FOR active_policy IN (
        SELECT risk_policy_id
          FROM RISK_POLICY
         WHERE status = 'ACTIVE'
    )
    LOOP
        validate_policy_bands(active_policy.risk_policy_id);
    END LOOP;
END;
/

--------------------------------------------------------------------------------
-- 2. Enforce source-account and beneficiary ownership at database level.
--------------------------------------------------------------------------------

ALTER TABLE ACCOUNT
    ADD CONSTRAINT UK_ACCOUNT_ID_OWNER
        UNIQUE (account_id, owner_user_id);

ALTER TABLE BENEFICIARY
    ADD CONSTRAINT UK_BENEFICIARY_ID_OWNER
        UNIQUE (beneficiary_id, owner_user_id);

ALTER TABLE PAYMENT_TRANSACTION
    ADD CONSTRAINT UK_PAYMENT_TX_ID_CUSTOMER
        UNIQUE (transaction_id, customer_user_id);

ALTER TABLE PAYMENT_TRANSACTION
    ADD CONSTRAINT UK_PAYMENT_TX_ID_RISK_BAND
        UNIQUE (transaction_id, risk_policy_band_id);

ALTER TABLE PAYMENT_TRANSACTION
    ADD CONSTRAINT FK_PAYMENT_TX_ACCOUNT_OWNER
        FOREIGN KEY (source_account_id, customer_user_id)
        REFERENCES ACCOUNT (account_id, owner_user_id);

ALTER TABLE PAYMENT_TRANSACTION
    ADD CONSTRAINT FK_PAYMENT_TX_BEN_OWNER
        FOREIGN KEY (beneficiary_id, customer_user_id)
        REFERENCES BENEFICIARY (beneficiary_id, owner_user_id);

ALTER TABLE PAYMENT_OTP_CHALLENGE
    ADD CONSTRAINT FK_PAYMENT_OTP_TX_CUSTOMER
        FOREIGN KEY (transaction_id, customer_user_id)
        REFERENCES PAYMENT_TRANSACTION (
            transaction_id,
            customer_user_id
        );

ALTER TABLE RISK_REVIEW
    ADD CONSTRAINT FK_RISK_REVIEW_TX_CUSTOMER
        FOREIGN KEY (transaction_id, customer_user_id)
        REFERENCES PAYMENT_TRANSACTION (
            transaction_id,
            customer_user_id
        );

ALTER TABLE TRANSACTION_RISK_FACTOR
    ADD CONSTRAINT FK_TX_RISK_FACTOR_SNAPSHOT
        FOREIGN KEY (transaction_id, risk_policy_band_id)
        REFERENCES PAYMENT_TRANSACTION (
            transaction_id,
            risk_policy_band_id
        );

CREATE INDEX IDX_PAYMENT_TX_ACCOUNT_OWNER
    ON PAYMENT_TRANSACTION (
        source_account_id,
        customer_user_id
    );

CREATE INDEX IDX_PAYMENT_TX_BEN_OWNER
    ON PAYMENT_TRANSACTION (
        beneficiary_id,
        customer_user_id
    );

CREATE INDEX IDX_PAYMENT_OTP_TX_CUSTOMER
    ON PAYMENT_OTP_CHALLENGE (
        transaction_id,
        customer_user_id
    );

CREATE INDEX IDX_RISK_REVIEW_TX_CUSTOMER
    ON RISK_REVIEW (
        transaction_id,
        customer_user_id
    );

CREATE INDEX IDX_TX_RISK_FACTOR_SNAPSHOT
    ON TRANSACTION_RISK_FACTOR (
        transaction_id,
        risk_policy_band_id
    );

--------------------------------------------------------------------------------
-- 3. Bind every persisted transaction snapshot to one policy and one band.
--------------------------------------------------------------------------------

ALTER TABLE RISK_POLICY_BAND
    MODIFY (
        band_code VARCHAR2(50 CHAR)
    );

ALTER TABLE RISK_POLICY
    ADD CONSTRAINT UK_RISK_POLICY_ID_VERSION
        UNIQUE (risk_policy_id, policy_version);

ALTER TABLE RISK_POLICY_BAND
    ADD CONSTRAINT UK_RISK_BAND_SNAPSHOT
        UNIQUE (
            risk_policy_band_id,
            risk_policy_id,
            protection_policy_id,
            risk_tier,
            band_code
        );

ALTER TABLE PAYMENT_TRANSACTION
    ADD CONSTRAINT FK_PAYMENT_TX_POLICY_VER
        FOREIGN KEY (
            risk_policy_id,
            policy_version
        )
        REFERENCES RISK_POLICY (
            risk_policy_id,
            policy_version
        );

ALTER TABLE PAYMENT_TRANSACTION
    ADD CONSTRAINT FK_PAYMENT_TX_RISK_SNAPSHOT
        FOREIGN KEY (
            risk_policy_band_id,
            risk_policy_id,
            protection_policy_id,
            risk_tier,
            matched_band_code
        )
        REFERENCES RISK_POLICY_BAND (
            risk_policy_band_id,
            risk_policy_id,
            protection_policy_id,
            risk_tier,
            band_code
        );

CREATE INDEX IDX_PAYMENT_TX_POLICY_VER
    ON PAYMENT_TRANSACTION (
        risk_policy_id,
        policy_version
    );

CREATE INDEX IDX_PAYMENT_TX_RISK_SNAPSHOT
    ON PAYMENT_TRANSACTION (
        risk_policy_band_id,
        risk_policy_id,
        protection_policy_id,
        risk_tier,
        matched_band_code
    );

ALTER TABLE PAYMENT_TRANSACTION
    ADD CONSTRAINT CK_PAYMENT_TX_RISK_SNAPSHOT
        CHECK (
            (
                risk_policy_id IS NULL
                AND risk_policy_band_id IS NULL
                AND protection_policy_id IS NULL
                AND risk_tier IS NULL
                AND policy_version IS NULL
                AND matched_band_code IS NULL
                AND protection_seconds IS NULL
                AND risk_explanation IS NULL
                AND risk_assessed_at IS NULL
                AND risk_score IS NULL
            )
            OR
            (
                risk_policy_id IS NOT NULL
                AND risk_policy_band_id IS NOT NULL
                AND protection_policy_id IS NOT NULL
                AND risk_tier IS NOT NULL
                AND policy_version IS NOT NULL
                AND matched_band_code IS NOT NULL
                AND TRIM(risk_explanation) IS NOT NULL
                AND risk_assessed_at IS NOT NULL
                AND risk_score IS NULL
            )
        );

--------------------------------------------------------------------------------
-- 4. Enforce V1 risk action and transaction-state compatibility.
--------------------------------------------------------------------------------

ALTER TABLE PAYMENT_TRANSACTION
    ADD CONSTRAINT CK_PAYMENT_TX_V1_RISK_ACTION
        CHECK (
            risk_assessed_at IS NULL
            OR
            (
                risk_tier = 'LOW'
                AND protection_seconds IS NOT NULL
                AND protection_seconds = 0
                AND protected_until IS NULL
            )
            OR
            (
                risk_tier = 'MEDIUM'
                AND protection_seconds IS NOT NULL
                AND protection_seconds = 10
            )
            OR
            (
                risk_tier = 'HIGH'
                AND protection_seconds IS NOT NULL
                AND protection_seconds = 60
            )
            OR
            (
                risk_tier = 'VERY_HIGH'
                AND protection_seconds IS NULL
                AND protected_until IS NULL
            )
        );

ALTER TABLE PAYMENT_TRANSACTION
    ADD CONSTRAINT CK_PAYMENT_TX_RISK_ROUTE
        CHECK (
            (
                state NOT IN (
                    'RISK_ASSESSED',
                    'PROTECTED',
                    'VERIFICATION_REQUIRED',
                    'PENDING_RISK_REVIEW',
                    'RELEASED',
                    'SETTLED'
                )
                OR risk_assessed_at IS NOT NULL
            )
            AND
            (
                state NOT IN ('CREATED', 'AUTHORIZED')
                OR risk_assessed_at IS NULL
            )
            AND
            (
                state <> 'PROTECTED'
                OR (
                    risk_tier IN ('MEDIUM', 'HIGH')
                    AND protected_until IS NOT NULL
                )
            )
            AND
            (
                state <> 'VERIFICATION_REQUIRED'
                OR (
                    risk_tier = 'VERY_HIGH'
                    AND verification_completed_at IS NULL
                )
            )
            AND
            (
                state <> 'PENDING_RISK_REVIEW'
                OR (
                    risk_tier = 'VERY_HIGH'
                    AND verification_completed_at IS NOT NULL
                )
            )
            AND
            (
                state NOT IN ('RELEASED', 'SETTLED')
                OR risk_tier NOT IN ('MEDIUM', 'HIGH')
                OR protected_until IS NOT NULL
            )
            AND
            (
                state NOT IN (
                    'RELEASED',
                    'SETTLED'
                )
                OR risk_tier <> 'VERY_HIGH'
                OR verification_completed_at IS NOT NULL
            )
        );

ALTER TABLE PAYMENT_TRANSACTION
    DROP CONSTRAINT CK_PAYMENT_TX_RESERVATION_TIME;

ALTER TABLE PAYMENT_TRANSACTION
    ADD CONSTRAINT CK_PAYMENT_TX_RESERVATION_TIME
        CHECK (
            (
                reserved_at IS NULL
                AND reservation_ended_at IS NULL
            )
            OR
            (
                reserved_at IS NOT NULL
                AND (
                    reservation_ended_at IS NULL
                    OR reservation_ended_at >= reserved_at
                )
            )
        );

ALTER TABLE PAYMENT_TRANSACTION
    ADD CONSTRAINT CK_PAYMENT_TX_RESERVE_STATE
        CHECK (
            (
                state IN (
                    'PROTECTED',
                    'VERIFICATION_REQUIRED',
                    'PENDING_RISK_REVIEW',
                    'RELEASED'
                )
                AND reserved_amount = amount
                AND reserved_at IS NOT NULL
                AND reservation_ended_at IS NULL
            )
            OR
            (
                state NOT IN (
                    'PROTECTED',
                    'VERIFICATION_REQUIRED',
                    'PENDING_RISK_REVIEW',
                    'RELEASED'
                )
                AND reserved_amount = 0
                AND (
                    (
                        reserved_at IS NULL
                        AND reservation_ended_at IS NULL
                    )
                    OR
                    (
                        reserved_at IS NOT NULL
                        AND reservation_ended_at IS NOT NULL
                    )
                )
            )
        );

ALTER TABLE PAYMENT_TRANSACTION
    ADD CONSTRAINT CK_PAYMENT_TX_DEADLINE_ORDER
        CHECK (
            protected_until IS NULL
            OR (
                reserved_at IS NOT NULL
                AND protected_until > reserved_at
            )
        );

--------------------------------------------------------------------------------
-- 5. Validate band coverage before activation and freeze published policy data.
--------------------------------------------------------------------------------

CREATE OR REPLACE PROCEDURE PR_VALIDATE_RISK_POLICY_BANDS (
    p_risk_policy_id IN NUMBER
)
AUTHID DEFINER
AS
    l_band_count    PLS_INTEGER;
    l_invalid_count PLS_INTEGER;
BEGIN
    SELECT COUNT(*)
      INTO l_band_count
      FROM RISK_POLICY_BAND
     WHERE risk_policy_id = p_risk_policy_id;

    IF l_band_count <> 4 THEN
        RAISE_APPLICATION_ERROR(
            -20110,
            'A policy must contain exactly four risk bands before activation'
        );
    END IF;

    SELECT COUNT(*)
      INTO l_invalid_count
      FROM (
            SELECT
                band.display_order,
                band.risk_tier,
                band.minimum_amount,
                band.maximum_amount,
                ROW_NUMBER() OVER (
                    ORDER BY band.display_order
                ) AS expected_order,
                LAG(band.maximum_amount) OVER (
                    ORDER BY band.display_order
                ) AS previous_maximum
            FROM RISK_POLICY_BAND band
            WHERE band.risk_policy_id = p_risk_policy_id
      ) ordered_band
     WHERE ordered_band.display_order <> ordered_band.expected_order
        OR (
            ordered_band.expected_order = 1
            AND ordered_band.minimum_amount <> 1.00
        )
        OR (
            ordered_band.expected_order > 1
            AND (
                ordered_band.previous_maximum IS NULL
                OR ordered_band.minimum_amount <>
                    ordered_band.previous_maximum + 0.01
            )
        )
        OR (
            ordered_band.expected_order < 4
            AND ordered_band.maximum_amount IS NULL
        )
        OR (
            ordered_band.expected_order = 4
            AND ordered_band.maximum_amount IS NOT NULL
        )
        OR ordered_band.risk_tier <>
            CASE ordered_band.expected_order
                WHEN 1 THEN 'LOW'
                WHEN 2 THEN 'MEDIUM'
                WHEN 3 THEN 'HIGH'
                WHEN 4 THEN 'VERY_HIGH'
            END
        OR (
            ordered_band.expected_order = 1
            AND (
                ordered_band.minimum_amount <> 1.00
                OR ordered_band.maximum_amount <> 5000.00
            )
        )
        OR (
            ordered_band.expected_order = 2
            AND (
                ordered_band.minimum_amount <> 5000.01
                OR ordered_band.maximum_amount <> 25000.00
            )
        )
        OR (
            ordered_band.expected_order = 3
            AND (
                ordered_band.minimum_amount <> 25000.01
                OR ordered_band.maximum_amount <> 100000.00
            )
        )
        OR (
            ordered_band.expected_order = 4
            AND ordered_band.minimum_amount <> 100000.01
        );

    IF l_invalid_count > 0 THEN
        RAISE_APPLICATION_ERROR(
            -20111,
            'Policy bands contain a gap, overlap, boundary, order or tier error'
        );
    END IF;

    SELECT COUNT(*)
      INTO l_invalid_count
      FROM RISK_POLICY_BAND band
      JOIN PROTECTION_POLICY protection
        ON protection.protection_policy_id =
           band.protection_policy_id
     WHERE band.risk_policy_id = p_risk_policy_id
       AND (
            (
                band.risk_tier = 'LOW'
                AND (
                    protection.release_mode <> 'IMMEDIATE'
                    OR protection.protection_seconds <> 0
                )
            )
            OR
            (
                band.risk_tier = 'MEDIUM'
                AND (
                    protection.release_mode <> 'AFTER_TIMER'
                    OR protection.protection_seconds <> 10
                )
            )
            OR
            (
                band.risk_tier = 'HIGH'
                AND (
                    protection.release_mode <> 'AFTER_TIMER'
                    OR protection.protection_seconds <> 60
                )
            )
            OR
            (
                band.risk_tier = 'VERY_HIGH'
                AND (
                    protection.release_mode <> 'AFTER_REVIEW'
                    OR protection.protection_seconds IS NOT NULL
                )
            )
       );

    IF l_invalid_count > 0 THEN
        RAISE_APPLICATION_ERROR(
            -20112,
            'A risk tier is linked to an incompatible protection mode or duration'
        );
    END IF;
END;
/

CREATE OR REPLACE TRIGGER TRG_RISK_POLICY_GUARD
    BEFORE INSERT OR UPDATE OR DELETE
    ON RISK_POLICY
    FOR EACH ROW
BEGIN
    IF INSERTING THEN
        IF :NEW.status <> 'DRAFT' THEN
            RAISE_APPLICATION_ERROR(
                -20120,
                'A risk policy must be created as DRAFT'
            );
        END IF;

    ELSIF DELETING THEN
        IF :OLD.status <> 'DRAFT' THEN
            RAISE_APPLICATION_ERROR(
                -20121,
                'An active or retired risk policy cannot be deleted'
            );
        END IF;

    ELSIF UPDATING THEN
        IF :OLD.status = 'DRAFT' THEN
            IF :NEW.status NOT IN ('DRAFT', 'ACTIVE') THEN
                RAISE_APPLICATION_ERROR(
                    -20122,
                    'A DRAFT policy may remain DRAFT or become ACTIVE'
                );
            END IF;

            IF :NEW.status = 'ACTIVE' THEN
                PR_VALIDATE_RISK_POLICY_BANDS(
                    :NEW.risk_policy_id
                );
            END IF;

        ELSIF :OLD.status = 'ACTIVE' THEN
            IF :NEW.status <> 'RETIRED'
               OR :NEW.risk_policy_id <> :OLD.risk_policy_id
               OR :NEW.policy_version <> :OLD.policy_version
               OR :NEW.policy_name <> :OLD.policy_name
               OR :NEW.algorithm_type <> :OLD.algorithm_type
               OR :NEW.currency_code <> :OLD.currency_code
               OR :NEW.effective_from <> :OLD.effective_from
               OR :NEW.description <> :OLD.description
               OR :NEW.created_at <> :OLD.created_at
               OR :NEW.effective_to IS NULL
               OR :NEW.effective_to <= :OLD.effective_from
            THEN
                RAISE_APPLICATION_ERROR(
                    -20123,
                    'An ACTIVE policy may only transition unchanged to RETIRED'
                );
            END IF;

        ELSE
            RAISE_APPLICATION_ERROR(
                -20124,
                'A RETIRED policy is immutable'
            );
        END IF;
    END IF;
END;
/

CREATE OR REPLACE TRIGGER TRG_RISK_BAND_DRAFT_ONLY
    BEFORE INSERT OR UPDATE OR DELETE
    ON RISK_POLICY_BAND
    FOR EACH ROW
DECLARE
    l_policy_status RISK_POLICY.status%TYPE;
BEGIN
    IF UPDATING OR DELETING THEN
        SELECT status
          INTO l_policy_status
          FROM RISK_POLICY
         WHERE risk_policy_id = :OLD.risk_policy_id;

        IF l_policy_status <> 'DRAFT' THEN
            RAISE_APPLICATION_ERROR(
                -20125,
                'Bands of an active or retired policy are immutable'
            );
        END IF;
    END IF;

    IF INSERTING OR UPDATING THEN
        SELECT status
          INTO l_policy_status
          FROM RISK_POLICY
         WHERE risk_policy_id = :NEW.risk_policy_id;

        IF l_policy_status <> 'DRAFT' THEN
            RAISE_APPLICATION_ERROR(
                -20126,
                'Bands may be inserted or changed only for a DRAFT policy'
            );
        END IF;
    END IF;
END;
/

CREATE OR REPLACE TRIGGER TRG_PROTECTION_POLICY_LOCK
    BEFORE UPDATE OR DELETE
    ON PROTECTION_POLICY
    FOR EACH ROW
DECLARE
    l_published_references PLS_INTEGER;
BEGIN
    SELECT COUNT(*)
      INTO l_published_references
      FROM RISK_POLICY_BAND band
      JOIN RISK_POLICY policy
        ON policy.risk_policy_id = band.risk_policy_id
     WHERE band.protection_policy_id =
           :OLD.protection_policy_id
       AND policy.status IN ('ACTIVE', 'RETIRED');

    IF l_published_references > 0 THEN
        RAISE_APPLICATION_ERROR(
            -20127,
            'A protection policy used by active or retired policy history is immutable'
        );
    END IF;
END;
/