--------------------------------------------------------------------------------
-- SafePay V1
-- Versioned migration: V12
-- Persistent fictional Indian showcase dataset for local V1 evaluation.
--
-- Safety and integrity rules:
--   * V1-V11 remain unchanged.
--   * All people, contacts and account numbers below are synthetic.
--   * Passwords are BCrypt cost-12 hashes; no plaintext password is stored.
--   * OTP material is terminal historical evidence only; no raw OTP is stored.
--   * Refresh sessions and idempotency records are intentionally runtime-created.
--   * Customer opening balances have balanced immutable ledger origins.
--   * Customer-facing emails remain presentation identities. SMTP must stay disabled
--     until the approved local recipient override is implemented and enabled.
--------------------------------------------------------------------------------

--------------------------------------------------------------------------------
-- 1. Fail before inserting if the canonical V11 reference contract or seed marker
--    is missing/conflicting. Flyway applies this version exactly once.
--------------------------------------------------------------------------------

DECLARE
    l_count PLS_INTEGER;
BEGIN
    SELECT COUNT(*)
      INTO l_count
      FROM APP_ROLE
     WHERE role_code IN (
        'CUSTOMER',
        'RISK_OFFICER',
        'SYSTEM_ADMIN',
        'AUDITOR'
     );

    IF l_count <> 4 THEN
        RAISE_APPLICATION_ERROR(
            -20300,
            'V12 stopped: the four canonical V11 roles are required'
        );
    END IF;

    SELECT COUNT(*)
      INTO l_count
      FROM RISK_POLICY policy
     WHERE policy.policy_version = 'AMOUNT_ONLY_V1'
       AND policy.status = 'ACTIVE'
       AND (
            SELECT COUNT(*)
              FROM RISK_POLICY_BAND band
             WHERE band.risk_policy_id = policy.risk_policy_id
       ) = 4;

    IF l_count <> 1 THEN
        RAISE_APPLICATION_ERROR(
            -20301,
            'V12 stopped: the active four-band AMOUNT_ONLY_V1 policy is required'
        );
    END IF;

    SELECT
          (SELECT COUNT(*)
             FROM APP_USER
            WHERE email = 'priya.nair@gmail.com')
        + (SELECT COUNT(*)
             FROM ACCOUNT
            WHERE account_number IN (
                'SAFEPAY_OUTBOUND_CLEARING',
                'SAFEPAY_OPENING_BALANCE_CONTROL'
            ))
        + (SELECT COUNT(*)
             FROM PAYMENT_TRANSACTION
            WHERE transaction_reference LIKE 'SPV1-SHOWCASE-%')
      INTO l_count
      FROM DUAL;

    IF l_count <> 0 THEN
        RAISE_APPLICATION_ERROR(
            -20302,
            'V12 stopped: showcase seed identifiers already exist'
        );
    END IF;
END;
/

--------------------------------------------------------------------------------
-- 2. Insert identities, roles, accounts, beneficiaries and financial histories.
--------------------------------------------------------------------------------

DECLARE
    FUNCTION user_id_for(p_email IN VARCHAR2)
        RETURN NUMBER
    IS
        l_user_id APP_USER.user_id%TYPE;
    BEGIN
        SELECT user_id
          INTO l_user_id
          FROM APP_USER
         WHERE email = p_email;

        RETURN l_user_id;
    END;

    FUNCTION account_id_for(p_account_number IN VARCHAR2)
        RETURN NUMBER
    IS
        l_account_id ACCOUNT.account_id%TYPE;
    BEGIN
        SELECT account_id
          INTO l_account_id
          FROM ACCOUNT
         WHERE account_number = p_account_number;

        RETURN l_account_id;
    END;

    FUNCTION transaction_id_for(p_reference IN VARCHAR2)
        RETURN NUMBER
    IS
        l_transaction_id PAYMENT_TRANSACTION.transaction_id%TYPE;
    BEGIN
        SELECT transaction_id
          INTO l_transaction_id
          FROM PAYMENT_TRANSACTION
         WHERE transaction_reference = p_reference;

        RETURN l_transaction_id;
    END;

    PROCEDURE seed_user(
        p_full_name     IN VARCHAR2,
        p_email         IN VARCHAR2,
        p_mobile        IN VARCHAR2,
        p_password_hash IN VARCHAR2,
        p_role_code     IN VARCHAR2
    )
    IS
        l_user_id APP_USER.user_id%TYPE;
    BEGIN
        l_user_id := SEQ_APP_USER_ID.NEXTVAL;

        INSERT INTO APP_USER (
            user_id,
            full_name,
            email,
            mobile_number,
            password_hash,
            status,
            failed_login_count,
            password_changed_at,
            security_version,
            version_no,
            created_at,
            updated_at
        )
        VALUES (
            l_user_id,
            p_full_name,
            p_email,
            p_mobile,
            p_password_hash,
            'ACTIVE',
            0,
            SYSTIMESTAMP - NUMTODSINTERVAL(200, 'DAY'),
            0,
            0,
            SYSTIMESTAMP - NUMTODSINTERVAL(200, 'DAY'),
            SYSTIMESTAMP - NUMTODSINTERVAL(200, 'DAY')
        );

        INSERT INTO USER_ROLE (
            user_id,
            role_id,
            assigned_at,
            assigned_by_user_id
        )
        SELECT
            l_user_id,
            role.role_id,
            SYSTIMESTAMP - NUMTODSINTERVAL(200, 'DAY'),
            NULL
        FROM APP_ROLE role
        WHERE role.role_code = p_role_code;

        IF SQL%ROWCOUNT <> 1 THEN
            RAISE_APPLICATION_ERROR(
                -20303,
                'V12 stopped: canonical role was not resolved for a seeded user'
            );
        END IF;
    END;

    PROCEDURE seed_system_account(
        p_account_number  IN VARCHAR2,
        p_account_type    IN VARCHAR2,
        p_bank_name       IN VARCHAR2,
        p_current_balance IN NUMBER
    )
    IS
    BEGIN
        INSERT INTO ACCOUNT (
            account_id,
            owner_user_id,
            account_number,
            account_type,
            bank_name,
            ifsc_code,
            currency_code,
            current_balance,
            reserved_amount,
            status,
            version_no,
            created_at,
            updated_at
        )
        VALUES (
            SEQ_ACCOUNT_ID.NEXTVAL,
            NULL,
            p_account_number,
            p_account_type,
            p_bank_name,
            NULL,
            'INR',
            p_current_balance,
            0,
            'ACTIVE',
            0,
            SYSTIMESTAMP - NUMTODSINTERVAL(190, 'DAY'),
            SYSTIMESTAMP - NUMTODSINTERVAL(190, 'DAY')
        );
    END;

    PROCEDURE seed_opening_posting(
        p_customer_account_number IN VARCHAR2,
        p_opening_balance         IN NUMBER
    )
    IS
        l_posting_id        LEDGER_POSTING.posting_id%TYPE;
        l_customer_account  ACCOUNT.account_id%TYPE;
        l_control_account   ACCOUNT.account_id%TYPE;
        l_reference         LEDGER_POSTING.posting_reference%TYPE;
        l_idempotency_key   LEDGER_POSTING.idempotency_key%TYPE;
        l_created_at        TIMESTAMP(6) WITH TIME ZONE;
    BEGIN
        IF p_opening_balance <= 0 THEN
            RETURN;
        END IF;

        l_customer_account := account_id_for(p_customer_account_number);
        l_control_account := account_id_for(
            'SAFEPAY_OPENING_BALANCE_CONTROL');
        l_reference := 'SPV1-OPEN-' || p_customer_account_number;
        l_idempotency_key := 'SPV1-OPEN-' || p_customer_account_number;
        l_created_at := SYSTIMESTAMP - NUMTODSINTERVAL(189, 'DAY');
        l_posting_id := SEQ_LEDGER_POSTING_ID.NEXTVAL;

        INSERT INTO LEDGER_POSTING (
            posting_id,
            posting_reference,
            posting_type,
            transaction_id,
            source_system,
            idempotency_key,
            amount,
            currency_code,
            expected_entry_count,
            status,
            created_at,
            updated_at,
            version_no
        )
        VALUES (
            l_posting_id,
            l_reference,
            'OPENING_BALANCE',
            NULL,
            'SAFEPAY',
            l_idempotency_key,
            p_opening_balance,
            'INR',
            2,
            'PENDING',
            l_created_at,
            l_created_at,
            0
        );

        INSERT INTO LEDGER_ENTRY (
            ledger_entry_id,
            posting_id,
            transaction_id,
            source_system,
            idempotency_key,
            line_number,
            account_id,
            entry_type,
            amount,
            currency_code,
            status,
            description,
            created_at
        )
        VALUES (
            SEQ_LEDGER_ENTRY_ID.NEXTVAL,
            l_posting_id,
            NULL,
            'SAFEPAY',
            l_idempotency_key,
            1,
            l_control_account,
            'DEBIT',
            p_opening_balance,
            'INR',
            'POSTED',
            'Opening-balance control debit',
            l_created_at + NUMTODSINTERVAL(1, 'SECOND')
        );

        INSERT INTO LEDGER_ENTRY (
            ledger_entry_id,
            posting_id,
            transaction_id,
            source_system,
            idempotency_key,
            line_number,
            account_id,
            entry_type,
            amount,
            currency_code,
            status,
            description,
            created_at
        )
        VALUES (
            SEQ_LEDGER_ENTRY_ID.NEXTVAL,
            l_posting_id,
            NULL,
            'SAFEPAY',
            l_idempotency_key,
            2,
            l_customer_account,
            'CREDIT',
            p_opening_balance,
            'INR',
            'POSTED',
            'Customer opening-balance credit',
            l_created_at + NUMTODSINTERVAL(1, 'SECOND')
        );

        UPDATE LEDGER_POSTING
           SET status = 'POSTED',
               posted_at = l_created_at
                    + NUMTODSINTERVAL(2, 'SECOND'),
               updated_at = l_created_at
                    + NUMTODSINTERVAL(2, 'SECOND'),
               version_no = 1
         WHERE posting_id = l_posting_id
           AND status = 'PENDING';
    END;

    PROCEDURE seed_customer_account(
        p_email             IN VARCHAR2,
        p_account_number    IN VARCHAR2,
        p_account_type      IN VARCHAR2,
        p_bank_name         IN VARCHAR2,
        p_ifsc_code         IN VARCHAR2,
        p_opening_balance   IN NUMBER,
        p_settled_debits    IN NUMBER,
        p_reserved_amount   IN NUMBER,
        p_status            IN VARCHAR2
    )
    IS
        l_current_balance NUMBER(18, 2);
        l_owner_user_id   ACCOUNT.owner_user_id%TYPE;
    BEGIN
        l_current_balance := p_opening_balance - p_settled_debits;

        IF l_current_balance < 0
           OR p_reserved_amount > l_current_balance
        THEN
            RAISE_APPLICATION_ERROR(
                -20304,
                'V12 stopped: seeded account balance invariant failed'
            );
        END IF;

        l_owner_user_id := user_id_for(p_email);

        INSERT INTO ACCOUNT (
            account_id,
            owner_user_id,
            account_number,
            account_type,
            bank_name,
            ifsc_code,
            currency_code,
            current_balance,
            reserved_amount,
            status,
            version_no,
            created_at,
            updated_at
        )
        VALUES (
            SEQ_ACCOUNT_ID.NEXTVAL,
            l_owner_user_id,
            p_account_number,
            p_account_type,
            p_bank_name,
            p_ifsc_code,
            'INR',
            l_current_balance,
            p_reserved_amount,
            p_status,
            0,
            SYSTIMESTAMP - NUMTODSINTERVAL(190, 'DAY'),
            SYSTIMESTAMP - NUMTODSINTERVAL(1, 'DAY')
        );

        seed_opening_posting(
            p_account_number,
            p_opening_balance);
    END;

    PROCEDURE seed_beneficiary(
        p_owner_email         IN VARCHAR2,
        p_beneficiary_name    IN VARCHAR2,
        p_nickname            IN VARCHAR2,
        p_payment_method      IN VARCHAR2,
        p_bank_name           IN VARCHAR2,
        p_bank_account_number IN VARCHAR2,
        p_ifsc_code           IN VARCHAR2,
        p_upi_id              IN VARCHAR2,
        p_relationship        IN VARCHAR2,
        p_purpose             IN VARCHAR2,
        p_status              IN VARCHAR2
    )
    IS
        l_owner_user_id BENEFICIARY.owner_user_id%TYPE;
    BEGIN
        l_owner_user_id := user_id_for(p_owner_email);

        INSERT INTO BENEFICIARY (
            beneficiary_id,
            owner_user_id,
            beneficiary_name,
            nickname,
            payment_method,
            bank_name,
            bank_account_number,
            ifsc_code,
            upi_id,
            relationship_label,
            purpose_note,
            status,
            version_no,
            created_at,
            updated_at
        )
        VALUES (
            SEQ_BENEFICIARY_ID.NEXTVAL,
            l_owner_user_id,
            p_beneficiary_name,
            p_nickname,
            p_payment_method,
            p_bank_name,
            p_bank_account_number,
            p_ifsc_code,
            p_upi_id,
            p_relationship,
            p_purpose,
            p_status,
            0,
            SYSTIMESTAMP - NUMTODSINTERVAL(120, 'DAY'),
            SYSTIMESTAMP - NUMTODSINTERVAL(120, 'DAY')
        );
    END;

    PROCEDURE seed_transaction(
        p_reference          IN VARCHAR2,
        p_customer_email     IN VARCHAR2,
        p_beneficiary_name   IN VARCHAR2,
        p_amount             IN NUMBER,
        p_purpose            IN VARCHAR2,
        p_state              IN VARCHAR2,
        p_terminal_reason    IN VARCHAR2,
        p_authorized         IN CHAR,
        p_has_risk           IN CHAR,
        p_had_reservation    IN CHAR,
        p_verified           IN CHAR,
        p_days_ago           IN NUMBER
    )
    IS
        l_transaction_id       PAYMENT_TRANSACTION.transaction_id%TYPE;
        l_customer_id          APP_USER.user_id%TYPE;
        l_source_account_id    ACCOUNT.account_id%TYPE;
        l_beneficiary_id       BENEFICIARY.beneficiary_id%TYPE;
        l_risk_policy_id       RISK_POLICY.risk_policy_id%TYPE;
        l_risk_band_id         RISK_POLICY_BAND.risk_policy_band_id%TYPE;
        l_protection_policy_id PROTECTION_POLICY.protection_policy_id%TYPE;
        l_risk_tier            RISK_POLICY_BAND.risk_tier%TYPE;
        l_band_code            RISK_POLICY_BAND.band_code%TYPE;
        l_protection_seconds   PROTECTION_POLICY.protection_seconds%TYPE;
        l_explanation          RISK_POLICY_BAND.explanation_template%TYPE;
        l_created_at           TIMESTAMP(6) WITH TIME ZONE;
        l_authorized_at        TIMESTAMP(6) WITH TIME ZONE;
        l_risk_assessed_at     TIMESTAMP(6) WITH TIME ZONE;
        l_reserved_at          TIMESTAMP(6) WITH TIME ZONE;
        l_reservation_ended_at TIMESTAMP(6) WITH TIME ZONE;
        l_protected_until      TIMESTAMP(6) WITH TIME ZONE;
        l_verified_at          TIMESTAMP(6) WITH TIME ZONE;
        l_released_at          TIMESTAMP(6) WITH TIME ZONE;
        l_settled_at           TIMESTAMP(6) WITH TIME ZONE;
        l_cancelled_at         TIMESTAMP(6) WITH TIME ZONE;
        l_failed_at            TIMESTAMP(6) WITH TIME ZONE;
        l_updated_at           TIMESTAMP(6) WITH TIME ZONE;
        l_reserved_amount      NUMBER(18, 2) := 0;
    BEGIN
        l_customer_id := user_id_for(p_customer_email);

        SELECT account_id
          INTO l_source_account_id
          FROM ACCOUNT
         WHERE owner_user_id = l_customer_id
           AND account_type IN ('SAVINGS', 'CURRENT');

        SELECT beneficiary_id
          INTO l_beneficiary_id
          FROM BENEFICIARY
         WHERE owner_user_id = l_customer_id
           AND beneficiary_name = p_beneficiary_name;

        l_created_at := SYSTIMESTAMP
            - NUMTODSINTERVAL(p_days_ago, 'DAY');
        l_updated_at := l_created_at
            + NUMTODSINTERVAL(10, 'MINUTE');

        IF p_authorized = 'Y' THEN
            l_authorized_at := l_created_at
                + NUMTODSINTERVAL(1, 'MINUTE');
        END IF;

        IF p_has_risk = 'Y' THEN
            SELECT
                policy.risk_policy_id,
                band.risk_policy_band_id,
                protection.protection_policy_id,
                band.risk_tier,
                band.band_code,
                protection.protection_seconds,
                band.explanation_template
              INTO
                l_risk_policy_id,
                l_risk_band_id,
                l_protection_policy_id,
                l_risk_tier,
                l_band_code,
                l_protection_seconds,
                l_explanation
              FROM RISK_POLICY policy
              JOIN RISK_POLICY_BAND band
                ON band.risk_policy_id = policy.risk_policy_id
              JOIN PROTECTION_POLICY protection
                ON protection.protection_policy_id =
                   band.protection_policy_id
             WHERE policy.policy_version = 'AMOUNT_ONLY_V1'
               AND policy.status = 'ACTIVE'
               AND p_amount >= band.minimum_amount
               AND (
                    band.maximum_amount IS NULL
                    OR p_amount <= band.maximum_amount
               );

            l_risk_assessed_at := l_created_at
                + NUMTODSINTERVAL(2, 'MINUTE');
        END IF;

        IF p_had_reservation = 'Y' THEN
            l_reserved_at := l_created_at
                + NUMTODSINTERVAL(3, 'MINUTE');

            IF l_risk_tier IN ('MEDIUM', 'HIGH') THEN
                l_protected_until := l_reserved_at
                    + NUMTODSINTERVAL(
                        l_protection_seconds,
                        'SECOND');
            END IF;

            IF p_state IN (
                'PROTECTED',
                'VERIFICATION_REQUIRED',
                'PENDING_RISK_REVIEW',
                'RELEASED'
            ) THEN
                l_reserved_amount := p_amount;
            ELSE
                l_reservation_ended_at := l_created_at
                    + NUMTODSINTERVAL(8, 'MINUTE');
            END IF;
        END IF;

        IF p_verified = 'Y' THEN
            l_verified_at := l_created_at
                + NUMTODSINTERVAL(5, 'MINUTE');
        END IF;

        IF p_state IN ('RELEASED', 'SETTLED') THEN
            l_released_at := l_created_at
                + NUMTODSINTERVAL(7, 'MINUTE');
        END IF;

        IF p_state = 'SETTLED' THEN
            l_settled_at := l_created_at
                + NUMTODSINTERVAL(8, 'MINUTE');
        ELSIF p_state = 'CANCELLED' THEN
            l_cancelled_at := l_created_at
                + NUMTODSINTERVAL(8, 'MINUTE');
        ELSIF p_state = 'FAILED' THEN
            l_failed_at := l_created_at
                + NUMTODSINTERVAL(8, 'MINUTE');
        END IF;

        l_transaction_id := SEQ_PAYMENT_TRANSACTION_ID.NEXTVAL;

        INSERT INTO PAYMENT_TRANSACTION (
            transaction_id,
            transaction_reference,
            customer_user_id,
            source_account_id,
            beneficiary_id,
            amount,
            currency_code,
            purpose,
            customer_reference,
            state,
            terminal_reason_code,
            version_no,
            reserved_amount,
            reserved_at,
            reservation_ended_at,
            risk_policy_id,
            risk_policy_band_id,
            protection_policy_id,
            risk_tier,
            risk_score,
            policy_version,
            matched_band_code,
            protection_seconds,
            risk_explanation,
            risk_assessed_at,
            created_at,
            authorized_at,
            protected_until,
            verification_completed_at,
            released_at,
            settled_at,
            cancelled_at,
            failed_at,
            updated_at
        )
        VALUES (
            l_transaction_id,
            p_reference,
            l_customer_id,
            l_source_account_id,
            l_beneficiary_id,
            p_amount,
            'INR',
            p_purpose,
            'SHOWCASE-' || SUBSTR(p_reference, -3),
            p_state,
            p_terminal_reason,
            0,
            l_reserved_amount,
            l_reserved_at,
            l_reservation_ended_at,
            l_risk_policy_id,
            l_risk_band_id,
            l_protection_policy_id,
            l_risk_tier,
            NULL,
            CASE WHEN p_has_risk = 'Y'
                 THEN 'AMOUNT_ONLY_V1' END,
            l_band_code,
            l_protection_seconds,
            l_explanation,
            l_risk_assessed_at,
            l_created_at,
            l_authorized_at,
            l_protected_until,
            l_verified_at,
            l_released_at,
            l_settled_at,
            l_cancelled_at,
            l_failed_at,
            l_updated_at
        );

        IF p_has_risk = 'Y' THEN
            INSERT INTO TRANSACTION_RISK_FACTOR (
                transaction_risk_factor_id,
                transaction_id,
                risk_policy_band_id,
                factor_code,
                raw_value,
                resulting_tier,
                explanation,
                evaluated_at
            )
            VALUES (
                SEQ_TX_RISK_FACTOR_ID.NEXTVAL,
                l_transaction_id,
                l_risk_band_id,
                'PAYMENT_AMOUNT',
                TO_CHAR(p_amount, 'FM9999999999999990D00'),
                l_risk_tier,
                l_explanation,
                l_risk_assessed_at
            );
        END IF;
    END;

    PROCEDURE seed_otp(
        p_transaction_reference IN VARCHAR2,
        p_hash                  IN VARCHAR2,
        p_status                IN VARCHAR2,
        p_attempt_count         IN NUMBER
    )
    IS
        l_transaction_id PAYMENT_TRANSACTION.transaction_id%TYPE;
        l_customer_id    PAYMENT_TRANSACTION.customer_user_id%TYPE;
        l_created_at     TIMESTAMP(6) WITH TIME ZONE;
        l_expires_at     TIMESTAMP(6) WITH TIME ZONE;
        l_verified_at    TIMESTAMP(6) WITH TIME ZONE;
        l_invalidated_at TIMESTAMP(6) WITH TIME ZONE;
    BEGIN
        SELECT
            transaction_id,
            customer_user_id,
            created_at + NUMTODSINTERVAL(4, 'MINUTE')
          INTO
            l_transaction_id,
            l_customer_id,
            l_created_at
          FROM PAYMENT_TRANSACTION
         WHERE transaction_reference = p_transaction_reference;

        l_expires_at := l_created_at
            + NUMTODSINTERVAL(5, 'MINUTE');

        IF p_status = 'VERIFIED' THEN
            l_verified_at := l_created_at
                + NUMTODSINTERVAL(1, 'MINUTE');
        ELSIF p_status = 'EXPIRED' THEN
            l_invalidated_at := l_expires_at
                + NUMTODSINTERVAL(1, 'MINUTE');
        ELSIF p_status IN ('LOCKED', 'CANCELLED') THEN
            l_invalidated_at := l_created_at
                + NUMTODSINTERVAL(2, 'MINUTE');
        END IF;

        INSERT INTO PAYMENT_OTP_CHALLENGE (
            otp_challenge_id,
            transaction_id,
            customer_user_id,
            challenge_purpose,
            delivery_channel,
            otp_hash,
            status,
            attempt_count,
            max_attempts,
            expires_at,
            verified_at,
            invalidated_at,
            created_at,
            updated_at,
            version_no
        )
        VALUES (
            SEQ_PAYMENT_OTP_CHALLENGE_ID.NEXTVAL,
            l_transaction_id,
            l_customer_id,
            'VERY_HIGH_PAYMENT',
            'SIMULATED',
            p_hash,
            p_status,
            p_attempt_count,
            3,
            l_expires_at,
            l_verified_at,
            l_invalidated_at,
            l_created_at,
            COALESCE(
                l_verified_at,
                l_invalidated_at,
                l_created_at),
            0
        );
    END;

    PROCEDURE seed_review(
        p_transaction_reference IN VARCHAR2,
        p_status                IN VARCHAR2,
        p_reason                IN VARCHAR2,
        p_round                 IN NUMBER
    )
    IS
        l_transaction_id PAYMENT_TRANSACTION.transaction_id%TYPE;
        l_customer_id    PAYMENT_TRANSACTION.customer_user_id%TYPE;
        l_officer_id     APP_USER.user_id%TYPE;
        l_requested_at   TIMESTAMP(6) WITH TIME ZONE;
        l_decided_at     TIMESTAMP(6) WITH TIME ZONE;
        l_assignee_id    APP_USER.user_id%TYPE;
        l_decider_id     APP_USER.user_id%TYPE;
    BEGIN
        SELECT
            transaction_id,
            customer_user_id,
            created_at + NUMTODSINTERVAL(6, 'MINUTE')
          INTO
            l_transaction_id,
            l_customer_id,
            l_requested_at
          FROM PAYMENT_TRANSACTION
         WHERE transaction_reference = p_transaction_reference;

        l_officer_id := user_id_for('rhea.malhotra@gmail.com');

        IF p_status IN (
            'APPROVED',
            'REJECTED',
            'REVERIFICATION_REQUESTED'
        ) THEN
            l_assignee_id := l_officer_id;
            l_decider_id := l_officer_id;
            l_decided_at := l_requested_at
                + NUMTODSINTERVAL(1, 'MINUTE');
        ELSIF p_status = 'CANCELLED' THEN
            l_decider_id := l_customer_id;
            l_decided_at := l_requested_at
                + NUMTODSINTERVAL(1, 'MINUTE');
        END IF;

        INSERT INTO RISK_REVIEW (
            approval_id,
            transaction_id,
            review_round,
            customer_user_id,
            assigned_risk_officer_id,
            status,
            decision_reason,
            requested_at,
            claimed_at,
            decided_at,
            decided_by_user_id,
            updated_at,
            version_no
        )
        VALUES (
            SEQ_RISK_REVIEW_ID.NEXTVAL,
            l_transaction_id,
            p_round,
            l_customer_id,
            l_assignee_id,
            p_status,
            p_reason,
            l_requested_at,
            NULL,
            l_decided_at,
            l_decider_id,
            COALESCE(l_decided_at, l_requested_at),
            0
        );
    END;

    PROCEDURE seed_settlement_posting(
        p_transaction_reference IN VARCHAR2
    )
    IS
        l_posting_id       LEDGER_POSTING.posting_id%TYPE;
        l_transaction_id   PAYMENT_TRANSACTION.transaction_id%TYPE;
        l_source_account   PAYMENT_TRANSACTION.source_account_id%TYPE;
        l_clearing_account ACCOUNT.account_id%TYPE;
        l_amount           PAYMENT_TRANSACTION.amount%TYPE;
        l_created_at       PAYMENT_TRANSACTION.released_at%TYPE;
        l_posted_at        PAYMENT_TRANSACTION.settled_at%TYPE;
        l_posting_ref      LEDGER_POSTING.posting_reference%TYPE;
        l_idempotency_key  LEDGER_POSTING.idempotency_key%TYPE;
    BEGIN
        SELECT
            transaction_id,
            source_account_id,
            amount,
            released_at,
            settled_at
          INTO
            l_transaction_id,
            l_source_account,
            l_amount,
            l_created_at,
            l_posted_at
          FROM PAYMENT_TRANSACTION
         WHERE transaction_reference = p_transaction_reference
           AND state = 'SETTLED';

        l_clearing_account := account_id_for(
            'SAFEPAY_OUTBOUND_CLEARING');
        l_posting_id := SEQ_LEDGER_POSTING_ID.NEXTVAL;
        l_posting_ref := 'SPV1-SET-' || SUBSTR(
            p_transaction_reference,
            -3);
        l_idempotency_key := 'SPV1-SETTLEMENT-' || SUBSTR(
            p_transaction_reference,
            -3);

        INSERT INTO LEDGER_POSTING (
            posting_id,
            posting_reference,
            posting_type,
            transaction_id,
            source_system,
            idempotency_key,
            amount,
            currency_code,
            expected_entry_count,
            status,
            created_at,
            updated_at,
            version_no
        )
        VALUES (
            l_posting_id,
            l_posting_ref,
            'PAYMENT_SETTLEMENT',
            l_transaction_id,
            'SAFEPAY',
            l_idempotency_key,
            l_amount,
            'INR',
            2,
            'PENDING',
            l_created_at,
            l_created_at,
            0
        );

        INSERT INTO LEDGER_ENTRY (
            ledger_entry_id,
            posting_id,
            transaction_id,
            source_system,
            idempotency_key,
            line_number,
            account_id,
            entry_type,
            amount,
            currency_code,
            status,
            description,
            created_at
        )
        VALUES (
            SEQ_LEDGER_ENTRY_ID.NEXTVAL,
            l_posting_id,
            l_transaction_id,
            'SAFEPAY',
            l_idempotency_key,
            1,
            l_source_account,
            'DEBIT',
            l_amount,
            'INR',
            'POSTED',
            'Customer source-account settlement debit',
            l_posted_at
        );

        INSERT INTO LEDGER_ENTRY (
            ledger_entry_id,
            posting_id,
            transaction_id,
            source_system,
            idempotency_key,
            line_number,
            account_id,
            entry_type,
            amount,
            currency_code,
            status,
            description,
            created_at
        )
        VALUES (
            SEQ_LEDGER_ENTRY_ID.NEXTVAL,
            l_posting_id,
            l_transaction_id,
            'SAFEPAY',
            l_idempotency_key,
            2,
            l_clearing_account,
            'CREDIT',
            l_amount,
            'INR',
            'POSTED',
            'Simulated outbound-clearing settlement credit',
            l_posted_at
        );

        UPDATE LEDGER_POSTING
           SET status = 'POSTED',
               posted_at = l_posted_at,
               updated_at = l_posted_at,
               version_no = 1
         WHERE posting_id = l_posting_id
           AND status = 'PENDING';
    END;

    PROCEDURE seed_exception(
        p_transaction_reference IN VARCHAR2,
        p_status                IN VARCHAR2,
        p_error_code            IN VARCHAR2,
        p_error_message         IN VARCHAR2,
        p_retryable             IN CHAR,
        p_retry_count           IN NUMBER,
        p_resolved              IN CHAR
    )
    IS
        l_transaction_id PAYMENT_TRANSACTION.transaction_id%TYPE;
        l_first_at       TIMESTAMP(6) WITH TIME ZONE;
        l_resolved_at    TIMESTAMP(6) WITH TIME ZONE;
        l_resolver_id    APP_USER.user_id%TYPE;
        l_note           VARCHAR2(2000);
    BEGIN
        SELECT
            transaction_id,
            created_at + NUMTODSINTERVAL(9, 'MINUTE')
          INTO
            l_transaction_id,
            l_first_at
          FROM PAYMENT_TRANSACTION
         WHERE transaction_reference = p_transaction_reference;

        IF p_resolved = 'Y' THEN
            l_resolved_at := l_first_at
                + NUMTODSINTERVAL(1, 'DAY');
            l_resolver_id := user_id_for('vikram.bhat@gmail.com');
            l_note := 'Reviewed and closed after confirming the safe final state.';
        END IF;

        INSERT INTO TRANSACTION_EXCEPTION (
            transaction_exception_id,
            exception_reference,
            transaction_id,
            posting_id,
            processing_stage,
            error_code,
            error_message,
            retryable_flag,
            status,
            retry_count,
            next_retry_at,
            first_occurred_at,
            last_occurred_at,
            resolved_at,
            resolved_by_user_id,
            resolution_note,
            correlation_id,
            created_at,
            updated_at,
            version_no
        )
        VALUES (
            SEQ_TRANSACTION_EXCEPTION_ID.NEXTVAL,
            'SPV1-EX-' || SUBSTR(p_transaction_reference, -3),
            l_transaction_id,
            NULL,
            'SETTLEMENT',
            p_error_code,
            p_error_message,
            p_retryable,
            p_status,
            p_retry_count,
            NULL,
            l_first_at,
            l_first_at,
            l_resolved_at,
            l_resolver_id,
            l_note,
            'SPV1-CORR-EX-' || SUBSTR(p_transaction_reference, -3),
            l_first_at,
            COALESCE(l_resolved_at, l_first_at),
            0
        );
    END;

    PROCEDURE seed_audit(
        p_transaction_reference IN VARCHAR2,
        p_actor_email           IN VARCHAR2,
        p_actor_role            IN VARCHAR2,
        p_action                IN VARCHAR2,
        p_previous_state        IN VARCHAR2,
        p_new_state             IN VARCHAR2,
        p_outcome               IN VARCHAR2,
        p_reason                IN VARCHAR2,
        p_suffix                IN VARCHAR2
    )
    IS
        l_transaction_id PAYMENT_TRANSACTION.transaction_id%TYPE;
        l_actor_id       APP_USER.user_id%TYPE;
        l_actor_type     VARCHAR2(20);
        l_occurred_at    TIMESTAMP(6) WITH TIME ZONE;
    BEGIN
        SELECT
            transaction_id,
            updated_at
          INTO
            l_transaction_id,
            l_occurred_at
          FROM PAYMENT_TRANSACTION
         WHERE transaction_reference = p_transaction_reference;

        IF p_actor_email IS NULL THEN
            l_actor_type := 'SYSTEM';
        ELSE
            l_actor_type := 'USER';
            l_actor_id := user_id_for(p_actor_email);
        END IF;

        INSERT INTO AUDIT_LOG (
            audit_log_id,
            event_reference,
            actor_user_id,
            actor_type,
            actor_role_code,
            action_code,
            entity_type,
            entity_id,
            transaction_id,
            previous_state,
            new_state,
            outcome,
            reason_code,
            correlation_id,
            idempotency_key,
            details_json,
            occurred_at
        )
        VALUES (
            SEQ_AUDIT_LOG_ID.NEXTVAL,
            'SPV1-AUD-' || p_suffix,
            l_actor_id,
            l_actor_type,
            p_actor_role,
            p_action,
            'PAYMENT_TRANSACTION',
            l_transaction_id,
            l_transaction_id,
            p_previous_state,
            p_new_state,
            p_outcome,
            p_reason,
            'SPV1-CORR-' || p_suffix,
            NULL,
            '{"source":"V12_SHOWCASE_SEED"}',
            l_occurred_at
        );
    END;

    PROCEDURE seed_notification(
        p_transaction_reference IN VARCHAR2,
        p_recipient_email       IN VARCHAR2,
        p_type                  IN VARCHAR2,
        p_severity              IN VARCHAR2,
        p_title                 IN VARCHAR2,
        p_message               IN VARCHAR2,
        p_status                IN VARCHAR2,
        p_read                  IN CHAR,
        p_suffix                IN VARCHAR2
    )
    IS
        l_transaction_id PAYMENT_TRANSACTION.transaction_id%TYPE;
        l_created_at     TIMESTAMP(6) WITH TIME ZONE;
        l_attempt_count  NUMBER := 0;
        l_delivered_at   TIMESTAMP(6) WITH TIME ZONE;
        l_failed_at      TIMESTAMP(6) WITH TIME ZONE;
        l_read_at        TIMESTAMP(6) WITH TIME ZONE;
        l_error_code     VARCHAR2(100);
        l_recipient_id   APP_NOTIFICATION.recipient_user_id%TYPE;
    BEGIN
        SELECT
            transaction_id,
            updated_at
          INTO
            l_transaction_id,
            l_created_at
         FROM PAYMENT_TRANSACTION
         WHERE transaction_reference = p_transaction_reference;

        l_recipient_id := user_id_for(p_recipient_email);

        IF p_status = 'DELIVERED' THEN
            l_attempt_count := 1;
            l_delivered_at := l_created_at
                + NUMTODSINTERVAL(1, 'SECOND');
        ELSIF p_status = 'FAILED' THEN
            l_attempt_count := 5;
            l_failed_at := l_created_at
                + NUMTODSINTERVAL(5, 'MINUTE');
            l_error_code := 'DELIVERY_ATTEMPTS_EXHAUSTED';
        END IF;

        IF p_read = 'Y' THEN
            l_read_at := l_created_at
                + NUMTODSINTERVAL(1, 'DAY');
        END IF;

        INSERT INTO APP_NOTIFICATION (
            notification_id,
            notification_reference,
            recipient_user_id,
            transaction_id,
            notification_type,
            severity,
            title,
            message,
            delivery_channel,
            delivery_status,
            deduplication_key,
            attempt_count,
            max_attempts,
            next_attempt_at,
            delivered_at,
            failed_at,
            read_at,
            last_error_code,
            correlation_id,
            created_at,
            updated_at,
            version_no
        )
        VALUES (
            SEQ_APP_NOTIFICATION_ID.NEXTVAL,
            'SPV1-NOT-' || p_suffix,
            l_recipient_id,
            l_transaction_id,
            p_type,
            p_severity,
            p_title,
            p_message,
            'IN_APP',
            p_status,
            'SPV1-DEDUP-' || p_suffix,
            l_attempt_count,
            5,
            NULL,
            l_delivered_at,
            l_failed_at,
            l_read_at,
            l_error_code,
            'SPV1-CORR-NOT-' || p_suffix,
            l_created_at,
            COALESCE(l_read_at, l_failed_at, l_delivered_at, l_created_at),
            0
        );
    END;

BEGIN
    ---------------------------------------------------------------------------
    -- 2.1 Thirty customers. Priya Nair is deliberately the first customer.
    ---------------------------------------------------------------------------

    seed_user('Priya Nair', 'priya.nair@gmail.com', '+919000000101', '$2a$12$WGMLScprCviVGEQ2ztcBPOSIpLv2Q.827fqgQe.tOj7vR16yQsja.', 'CUSTOMER');
    seed_user('Arjun Menon', 'arjun.menon@gmail.com', '+919000000102', '$2a$12$HY6hhYa1iJ.CcPJmcM4/GOW2dRsT13cxqqANKFymv5ongG2jEMkIe', 'CUSTOMER');
    seed_user('Kavya Sharma', 'kavya.sharma@gmail.com', '+919000000103', '$2a$12$WtFF/SEG70WO/8/5MRY43.sMKAWZTaHodO.sh0DQa2Qa3h92CZP5e', 'CUSTOMER');
    seed_user('Rohan Mehta', 'rohan.mehta@gmail.com', '+919000000104', '$2a$12$wEMCcaluFppEOGf.O5hmN.aoYpalc4uRR5gJ6LUsy2eP7XRa.PTVS', 'CUSTOMER');
    seed_user('Ananya Iyer', 'ananya.iyer@gmail.com', '+919000000105', '$2a$12$0dyP1aykuXmJL5FXZJljEOb/Zb3pe5sFCUVu6PiggpiwcQqURDy8C', 'CUSTOMER');
    seed_user('Vivek Reddy', 'vivek.reddy@gmail.com', '+919000000106', '$2a$12$E7b6OgqLSxe72ieEmGyBzOcqA4dNphkF1aWw5eVvDWTR7it79WJi.', 'CUSTOMER');
    seed_user('Sneha Kulkarni', 'sneha.kulkarni@gmail.com', '+919000000107', '$2a$12$ko7FLIfK13PmOIghfRA.UOwFxbayBqGEvh.38wxiAyZjdInho0Qhi', 'CUSTOMER');
    seed_user('Rahul Verma', 'rahul.verma@gmail.com', '+919000000108', '$2a$12$.gk2VcL.3myhytO3pQhDdObXLbg.MamdGvAfBy.tg2UvydufOH/E6', 'CUSTOMER');
    seed_user('Ishita Banerjee', 'ishita.banerjee@gmail.com', '+919000000109', '$2a$12$J1kTOKC/xb9xplZ9LNAdeuf7uXUJ8pcOwO3UBEgJBMRsen2QzrWgi', 'CUSTOMER');
    seed_user('Karthik Subramanian', 'karthik.subramanian@gmail.com', '+919000000110', '$2a$12$vxrtAhuSF4CoR1wCBk3IveBiLwvcia5PONIGw7uZrCPNME1FgTq4G', 'CUSTOMER');
    seed_user('Neha Gupta', 'neha.gupta@gmail.com', '+919000000111', '$2a$12$bFFri0Pd3/9agf.MqhGhdOfzf63BVetTMyMC1/y2fKhCSK0ml3dIi', 'CUSTOMER');
    seed_user('Aditya Joshi', 'aditya.joshi@gmail.com', '+919000000112', '$2a$12$p7IGwyatnc7qlAbEYPL1mu6NXrcajf079qnTX4q6rcmKLDxGPRvIa', 'CUSTOMER');
    seed_user('Pooja Deshmukh', 'pooja.deshmukh@gmail.com', '+919000000113', '$2a$12$0Dmgz9FGZLXqN6T/uAoABOcsBS9cMOQH6P7fAmcykq821hrR.UcHe', 'CUSTOMER');
    seed_user('Sanjay Patel', 'sanjay.patel@gmail.com', '+919000000114', '$2a$12$pP.TuYIMw8Egj.phJL8/COXiIiKLpfVTQK5FEPkGS3NtqbNea44lO', 'CUSTOMER');
    seed_user('Divya Rao', 'divya.rao@gmail.com', '+919000000115', '$2a$12$5Zh1AYnGL7p58OPf5EitVOP9vuMzgZgSvggceXtFCVQC8dBQ/mM2i', 'CUSTOMER');
    seed_user('Manish Agarwal', 'manish.agarwal@gmail.com', '+919000000116', '$2a$12$ErajaTfC.a0FxKmFSAGcd.X4DWC.qJpCIV4WIpMlplsQacih4pUHK', 'CUSTOMER');
    seed_user('Aisha Khan', 'aisha.khan@gmail.com', '+919000000117', '$2a$12$h8qM7SoOYpj6ZLYOg64auOKsjgNjnZ7SqLGKVTXbxlBZ6C9n13y9C', 'CUSTOMER');
    seed_user('Nikhil Chawla', 'nikhil.chawla@gmail.com', '+919000000118', '$2a$12$RAeq5ESpWV8axokvPpryyODfVWlXdX2ZXntvBW6w1pVySuBKM6bfW', 'CUSTOMER');
    seed_user('Meera Pillai', 'meera.pillai@gmail.com', '+919000000119', '$2a$12$yvz6rh0tQAdIRBux5gX88.IsyRC9Lt1WgYn3hw80GpNU6ias964GO', 'CUSTOMER');
    seed_user('Varun Kapoor', 'varun.kapoor@gmail.com', '+919000000120', '$2a$12$ebbz2HzOqCvRqWbq/iBRoOhoY2Kflbg.a.TjCfqUtqjvfUy2VPVry', 'CUSTOMER');
    seed_user('Ritu Singh', 'ritu.singh@gmail.com', '+919000000121', '$2a$12$J4v/DVdqpx.VjJE3D8ljpu6MFJHgVItpJF7nlr8G.RzlG59NI7Y4S', 'CUSTOMER');
    seed_user('Harish Gowda', 'harish.gowda@gmail.com', '+919000000122', '$2a$12$lEYObAxoQxF4cZS/7K7vN.2UEOscZhVxupZIR6b0l1rjjxs2sqStO', 'CUSTOMER');
    seed_user('Tanvi Shah', 'tanvi.shah@gmail.com', '+919000000123', '$2a$12$SQCF4HHwYVcK2odvsRp3aeLhtgD8HsUSnun7iOKmOVgBwP8LognD.', 'CUSTOMER');
    seed_user('Suresh Yadav', 'suresh.yadav@gmail.com', '+919000000124', '$2a$12$xYyq66tP7m5ynpzy6GKyuOFxEjD9PYa99Ml0Bx0qH5WH7RYT/7Tr2', 'CUSTOMER');
    seed_user('Nandini Bose', 'nandini.bose@gmail.com', '+919000000125', '$2a$12$.dh6XfQSJmfhU9AWiZUkgeruShFUXLTkgSKSDQzx6jUOVHtFJsOW.', 'CUSTOMER');
    seed_user('Akash Mishra', 'akash.mishra@gmail.com', '+919000000126', '$2a$12$PERBYS2KlVEq6G1AYLbheuF7p5W/egZdXpdTAAN4Fi8rGzE6I67t.', 'CUSTOMER');
    seed_user('Lakshmi Narayanan', 'lakshmi.narayanan@gmail.com', '+919000000127', '$2a$12$.lbAbb/cE.2NXUQsyzJynev8VFu3zRw7DqLYBpspAfFqQlu4r/3Eq', 'CUSTOMER');
    seed_user('Mohit Sethi', 'mohit.sethi@gmail.com', '+919000000128', '$2a$12$Ek55T.RA/ucWV9onk9JnvejEF3/4hJWfriX46iiA3aT81.OuBQUBO', 'CUSTOMER');
    seed_user('Shruti Das', 'shruti.das@gmail.com', '+919000000129', '$2a$12$y0QN67T.gA/84.uY4iEFjOdmVvRlMoRGdIYSASlqISVw/SAvbVCE6', 'CUSTOMER');
    seed_user('Dev Malhotra', 'dev.malhotra@gmail.com', '+919000000130', '$2a$12$ckyHllKiOaK8vqcWPV30xeNArGrIVYsq5tlLQSytqVhAcbfc.NbNu', 'CUSTOMER');

    ---------------------------------------------------------------------------
    -- 2.2 Dedicated staff personas keep V1 authorities independently testable.
    ---------------------------------------------------------------------------

    seed_user('Rhea Malhotra', 'rhea.malhotra@gmail.com', '+919000000131', '$2a$12$s0tlrAzfhrrMpwre434sD.nNOWOrnlyiQZSeUeAXl7n/Q4ahzjnkG', 'RISK_OFFICER');
    seed_user('Vikram Bhat', 'vikram.bhat@gmail.com', '+919000000132', '$2a$12$bKb7wZo3b9vE6fDplEabo.AQFXqI/RxyjncKgHdHmwLGUtuPDpZKq', 'SYSTEM_ADMIN');
    seed_user('Anjali Thomas', 'anjali.thomas@gmail.com', '+919000000133', '$2a$12$TSS4mkyQzKgprkW6JaGp8OEtuWbIBl88Y86ieCTzt53ClH7hP5p72', 'AUDITOR');

    ---------------------------------------------------------------------------
    -- 2.3 Internal system accounts. Clearing balance equals seeded settlement
    --     credits; the opening control is a non-customer contra account.
    ---------------------------------------------------------------------------

    seed_system_account(
        'SAFEPAY_OUTBOUND_CLEARING',
        'OUTBOUND_CLEARING',
        'SafePay Internal Outbound Clearing',
        362501.02);

    seed_system_account(
        'SAFEPAY_OPENING_BALANCE_CONTROL',
        'OPENING_BALANCE_CONTROL',
        'SafePay Opening Balance Control',
        0);

    ---------------------------------------------------------------------------
    -- 2.4 Customer accounts. Lengths intentionally vary across Indian-bank
    --     styles while values remain synthetic and non-routable test data.
    ---------------------------------------------------------------------------

    seed_customer_account('priya.nair@gmail.com', '50100123456789', 'SAVINGS', 'HDFC Bank', 'HDFC0000001', 350000.00, 0, 150000.00, 'ACTIVE');
    seed_customer_account('arjun.menon@gmail.com', '12345678901', 'SAVINGS', 'State Bank of India', 'SBIN0000456', 120000.00, 2500.00, 0, 'ACTIVE');
    seed_customer_account('kavya.sharma@gmail.com', '100123456789', 'SAVINGS', 'ICICI Bank', 'ICIC0001234', 200000.00, 0, 0, 'ACTIVE');
    seed_customer_account('rohan.mehta@gmail.com', '911010123456789', 'CURRENT', 'Axis Bank', 'UTIB0000123', 500000.00, 75000.00, 0, 'ACTIVE');
    seed_customer_account('ananya.iyer@gmail.com', '12345678901234', 'SAVINGS', 'Kotak Mahindra Bank', 'KKBK0000456', 300000.00, 0, 110000.00, 'ACTIVE');
    seed_customer_account('vivek.reddy@gmail.com', '0153001234567890', 'SAVINGS', 'Punjab National Bank', 'PUNB0015300', 275000.00, 0, 0, 'ACTIVE');
    seed_customer_account('sneha.kulkarni@gmail.com', '20123456789012', 'SAVINGS', 'Bank of Baroda', 'BARB0MUMBAI', 180000.00, 0, 0, 'ACTIVE');
    seed_customer_account('rahul.verma@gmail.com', '1234567890123', 'SAVINGS', 'Canara Bank', 'CNRB0001234', 250000.00, 0, 0, 'ACTIVE');
    seed_customer_account('ishita.banerjee@gmail.com', '123456789012345', 'CURRENT', 'Union Bank of India', 'UBIN0531234', 400000.00, 125000.00, 0, 'ACTIVE');
    seed_customer_account('karthik.subramanian@gmail.com', '40123456789012', 'SAVINGS', 'Federal Bank', 'FDRL0001234', 260000.00, 0, 120000.00, 'ACTIVE');
    seed_customer_account('neha.gupta@gmail.com', '501234567890', 'SAVINGS', 'IDFC FIRST Bank', 'IDFB0040101', 100000.00, 5000.01, 0, 'ACTIVE');
    seed_customer_account('aditya.joshi@gmail.com', '601234567890123', 'CURRENT', 'YES Bank', 'YESB0000123', 150000.00, 25000.01, 0, 'ACTIVE');
    seed_customer_account('pooja.deshmukh@gmail.com', '70123456789012', 'SAVINGS', 'IndusInd Bank', 'INDB0000123', 60000.00, 5000.00, 0, 'ACTIVE');
    seed_customer_account('sanjay.patel@gmail.com', '801234567890123', 'CURRENT', 'Bank of India', 'BKID0000123', 90000.00, 25000.00, 0, 'ACTIVE');
    seed_customer_account('divya.rao@gmail.com', '90123456789', 'SAVINGS', 'Indian Bank', 'IDIB000A123', 250000.00, 100000.00, 0, 'ACTIVE');
    seed_customer_account('manish.agarwal@gmail.com', '50100123456790', 'SAVINGS', 'HDFC Bank', 'HDFC0000001', 200000.00, 0, 100000.01, 'ACTIVE');
    seed_customer_account('aisha.khan@gmail.com', '12345678902', 'SAVINGS', 'State Bank of India', 'SBIN0000456', 75000.00, 0, 0, 'ACTIVE');
    seed_customer_account('nikhil.chawla@gmail.com', '100123456790', 'SAVINGS', 'ICICI Bank', 'ICIC0001234', 85000.00, 0, 0, 'ACTIVE');
    seed_customer_account('meera.pillai@gmail.com', '911010123456790', 'CURRENT', 'Axis Bank', 'UTIB0000123', 500.00, 0, 0, 'ACTIVE');
    seed_customer_account('varun.kapoor@gmail.com', '12345678901235', 'SAVINGS', 'Kotak Mahindra Bank', 'KKBK0000456', 50000.00, 0, 0, 'ACTIVE');
    seed_customer_account('ritu.singh@gmail.com', '0153001234567891', 'SAVINGS', 'Punjab National Bank', 'PUNB0015300', 65000.00, 0, 0, 'ACTIVE');
    seed_customer_account('harish.gowda@gmail.com', '20123456789013', 'SAVINGS', 'Bank of Baroda', 'BARB0MUMBAI', 150000.00, 0, 0, 'ACTIVE');
    seed_customer_account('tanvi.shah@gmail.com', '1234567890124', 'SAVINGS', 'Canara Bank', 'CNRB0001234', 100000.00, 0, 4500.00, 'ACTIVE');
    seed_customer_account('suresh.yadav@gmail.com', '123456789012346', 'CURRENT', 'Union Bank of India', 'UBIN0531234', 175000.00, 0, 60000.00, 'ACTIVE');
    seed_customer_account('nandini.bose@gmail.com', '40123456789013', 'SAVINGS', 'Federal Bank', 'FDRL0001234', 0, 0, 0, 'ACTIVE');
    seed_customer_account('akash.mishra@gmail.com', '501234567891', 'SAVINGS', 'IDFC FIRST Bank', 'IDFB0040101', 10000.00, 0, 0, 'INACTIVE');
    seed_customer_account('lakshmi.narayanan@gmail.com', '601234567890124', 'CURRENT', 'YES Bank', 'YESB0000123', 500000.00, 0, 0, 'ACTIVE');
    seed_customer_account('mohit.sethi@gmail.com', '70123456789013', 'SAVINGS', 'IndusInd Bank', 'INDB0000123', 1.00, 1.00, 0, 'ACTIVE');
    seed_customer_account('shruti.das@gmail.com', '801234567890124', 'SAVINGS', 'Bank of India', 'BKID0000123', 30000.00, 0, 0, 'ACTIVE');
    seed_customer_account('dev.malhotra@gmail.com', '90123456790', 'CURRENT', 'Indian Bank', 'IDIB000A123', 999999.99, 0, 0, 'ACTIVE');

    ---------------------------------------------------------------------------
    -- 2.5 One owned beneficiary per customer, covering bank-account, UPI,
    --     active and disabled contracts without claiming real routing.
    ---------------------------------------------------------------------------

    seed_beneficiary('priya.nair@gmail.com', 'Arjun Menon', 'Arjun Family', 'BANK_ACCOUNT', 'State Bank of India', '12345678901', 'SBIN0000456', NULL, 'FAMILY', 'Family support', 'ACTIVE');
    seed_beneficiary('arjun.menon@gmail.com', 'Kavya Sharma', 'Kavya UPI', 'UPI', NULL, NULL, NULL, 'kavya.sharma@okicici', 'FRIEND', 'Shared expenses', 'ACTIVE');
    seed_beneficiary('kavya.sharma@gmail.com', 'Rohan Mehta', 'Rohan Rent', 'BANK_ACCOUNT', 'Axis Bank', '911010123456789', 'UTIB0000123', NULL, 'LANDLORD', 'Monthly rent', 'ACTIVE');
    seed_beneficiary('rohan.mehta@gmail.com', 'Ananya Iyer', 'Ananya UPI', 'UPI', NULL, NULL, NULL, 'ananya.iyer@okhdfcbank', 'COLLEAGUE', 'Travel reimbursement', 'ACTIVE');
    seed_beneficiary('ananya.iyer@gmail.com', 'Vivek Reddy', 'Vivek Family', 'BANK_ACCOUNT', 'Punjab National Bank', '0153001234567890', 'PUNB0015300', NULL, 'FAMILY', 'Medical support', 'ACTIVE');
    seed_beneficiary('vivek.reddy@gmail.com', 'Sneha Kulkarni', 'Sneha UPI', 'UPI', NULL, NULL, NULL, 'sneha.kulkarni@okaxis', 'FRIEND', 'Event contribution', 'ACTIVE');
    seed_beneficiary('sneha.kulkarni@gmail.com', 'Rahul Verma', 'Rahul Savings', 'BANK_ACCOUNT', 'Canara Bank', '1234567890123', 'CNRB0001234', NULL, 'FAMILY', 'Savings transfer', 'ACTIVE');
    seed_beneficiary('rahul.verma@gmail.com', 'Ishita Banerjee', 'Ishita UPI', 'UPI', NULL, NULL, NULL, 'ishita.banerjee@okicici', 'FRIEND', 'Education expenses', 'ACTIVE');
    seed_beneficiary('ishita.banerjee@gmail.com', 'Karthik Subramanian', 'Karthik Vendor', 'BANK_ACCOUNT', 'Federal Bank', '40123456789012', 'FDRL0001234', NULL, 'VENDOR', 'Professional services', 'ACTIVE');
    seed_beneficiary('karthik.subramanian@gmail.com', 'Neha Gupta', 'Neha UPI', 'UPI', NULL, NULL, NULL, 'neha.gupta@okhdfcbank', 'FAMILY', 'Household contribution', 'ACTIVE');
    seed_beneficiary('neha.gupta@gmail.com', 'Aditya Joshi', 'Aditya Family', 'BANK_ACCOUNT', 'YES Bank', '601234567890123', 'YESB0000123', NULL, 'FAMILY', 'Family transfer', 'ACTIVE');
    seed_beneficiary('aditya.joshi@gmail.com', 'Pooja Deshmukh', 'Pooja UPI', 'UPI', NULL, NULL, NULL, 'pooja.deshmukh@okaxis', 'FRIEND', 'Group booking', 'ACTIVE');
    seed_beneficiary('pooja.deshmukh@gmail.com', 'Sanjay Patel', 'Sanjay Vendor', 'BANK_ACCOUNT', 'Bank of India', '801234567890123', 'BKID0000123', NULL, 'VENDOR', 'Supplier payment', 'ACTIVE');
    seed_beneficiary('sanjay.patel@gmail.com', 'Divya Rao', 'Divya UPI', 'UPI', NULL, NULL, NULL, 'divya.rao@okicici', 'COLLEAGUE', 'Expense settlement', 'ACTIVE');
    seed_beneficiary('divya.rao@gmail.com', 'Manish Agarwal', 'Manish Family', 'BANK_ACCOUNT', 'HDFC Bank', '50100123456790', 'HDFC0000001', NULL, 'FAMILY', 'Family support', 'ACTIVE');
    seed_beneficiary('manish.agarwal@gmail.com', 'Aisha Khan', 'Aisha UPI', 'UPI', NULL, NULL, NULL, 'aisha.khan@okhdfcbank', 'FRIEND', 'Celebration contribution', 'ACTIVE');
    seed_beneficiary('aisha.khan@gmail.com', 'Nikhil Chawla', 'Nikhil Fees', 'BANK_ACCOUNT', 'ICICI Bank', '100123456790', 'ICIC0001234', NULL, 'INSTITUTION', 'Course fees', 'ACTIVE');
    seed_beneficiary('nikhil.chawla@gmail.com', 'Meera Pillai', 'Meera UPI', 'UPI', NULL, NULL, NULL, 'meera.pillai@okaxis', 'FRIEND', 'Shared purchase', 'ACTIVE');
    seed_beneficiary('meera.pillai@gmail.com', 'Varun Kapoor', 'Varun Family', 'BANK_ACCOUNT', 'Kotak Mahindra Bank', '12345678901235', 'KKBK0000456', NULL, 'FAMILY', 'Emergency support', 'ACTIVE');
    seed_beneficiary('varun.kapoor@gmail.com', 'Ritu Singh', 'Ritu UPI', 'UPI', NULL, NULL, NULL, 'ritu.singh@okicici', 'FRIEND', 'Trip settlement', 'ACTIVE');
    seed_beneficiary('ritu.singh@gmail.com', 'Harish Gowda', 'Harish Home', 'BANK_ACCOUNT', 'Bank of Baroda', '20123456789013', 'BARB0MUMBAI', NULL, 'LANDLORD', 'Housing payment', 'ACTIVE');
    seed_beneficiary('harish.gowda@gmail.com', 'Tanvi Shah', 'Tanvi UPI', 'UPI', NULL, NULL, NULL, 'tanvi.shah@okhdfcbank', 'COLLEAGUE', 'Office collection', 'ACTIVE');
    seed_beneficiary('tanvi.shah@gmail.com', 'Suresh Yadav', 'Suresh Vendor', 'BANK_ACCOUNT', 'Union Bank of India', '123456789012346', 'UBIN0531234', NULL, 'VENDOR', 'Equipment purchase', 'ACTIVE');
    seed_beneficiary('suresh.yadav@gmail.com', 'Nandini Bose', 'Nandini UPI', 'UPI', NULL, NULL, NULL, 'nandini.bose@okaxis', 'FAMILY', 'Household transfer', 'ACTIVE');
    seed_beneficiary('nandini.bose@gmail.com', 'Akash Mishra', 'Akash Family', 'BANK_ACCOUNT', 'IDFC FIRST Bank', '501234567891', 'IDFB0040101', NULL, 'FAMILY', 'Family support', 'ACTIVE');
    seed_beneficiary('akash.mishra@gmail.com', 'Lakshmi Narayanan', 'Lakshmi UPI', 'UPI', NULL, NULL, NULL, 'lakshmi.narayanan@okicici', 'COLLEAGUE', 'Expense reimbursement', 'ACTIVE');
    seed_beneficiary('lakshmi.narayanan@gmail.com', 'Mohit Sethi', 'Mohit Savings', 'BANK_ACCOUNT', 'IndusInd Bank', '70123456789013', 'INDB0000123', NULL, 'FAMILY', 'Savings contribution', 'ACTIVE');
    seed_beneficiary('mohit.sethi@gmail.com', 'Shruti Das', 'Shruti UPI', 'UPI', NULL, NULL, NULL, 'shruti.das@okhdfcbank', 'FRIEND', 'Small-value transfer', 'ACTIVE');
    seed_beneficiary('shruti.das@gmail.com', 'Dev Malhotra', 'Dev Disabled', 'BANK_ACCOUNT', 'Indian Bank', '90123456790', 'IDIB000A123', NULL, 'VENDOR', 'Former supplier', 'DISABLED');
    seed_beneficiary('dev.malhotra@gmail.com', 'Priya Nair', 'Priya UPI', 'UPI', NULL, NULL, NULL, 'priya.nair@okaxis', 'FRIEND', 'Personal transfer', 'ACTIVE');

    ---------------------------------------------------------------------------
    -- 2.6 Historical and live-safe payment states. No active timer is seeded.
    ---------------------------------------------------------------------------

    seed_transaction('SPV1-SHOWCASE-101', 'priya.nair@gmail.com', 'Arjun Menon', 150000.00, 'Hospital advance', 'PENDING_RISK_REVIEW', NULL, 'Y', 'Y', 'Y', 'Y', 30);
    seed_transaction('SPV1-SHOWCASE-102', 'arjun.menon@gmail.com', 'Kavya Sharma', 2500.00, 'Shared travel expenses', 'SETTLED', NULL, 'Y', 'Y', 'Y', 'N', 29);
    seed_transaction('SPV1-SHOWCASE-103', 'kavya.sharma@gmail.com', 'Rohan Mehta', 15000.00, 'Monthly rent', 'CANCELLED', 'CUSTOMER_CANCELLED', 'Y', 'Y', 'Y', 'N', 28);
    seed_transaction('SPV1-SHOWCASE-104', 'rohan.mehta@gmail.com', 'Ananya Iyer', 75000.00, 'Vendor invoice', 'SETTLED', NULL, 'Y', 'Y', 'Y', 'N', 27);
    seed_transaction('SPV1-SHOWCASE-105', 'ananya.iyer@gmail.com', 'Vivek Reddy', 110000.00, 'Medical support', 'VERIFICATION_REQUIRED', NULL, 'Y', 'Y', 'Y', 'N', 1);
    seed_transaction('SPV1-SHOWCASE-106', 'vivek.reddy@gmail.com', 'Sneha Kulkarni', 125000.00, 'Property booking advance', 'CANCELLED', 'CUSTOMER_CANCELLED', 'Y', 'Y', 'Y', 'Y', 25);
    seed_transaction('SPV1-SHOWCASE-107', 'sneha.kulkarni@gmail.com', 'Rahul Verma', 130000.00, 'Investment transfer', 'CANCELLED', 'OTP_POLICY_EXHAUSTED', 'Y', 'Y', 'Y', 'N', 24);
    seed_transaction('SPV1-SHOWCASE-108', 'rahul.verma@gmail.com', 'Ishita Banerjee', 140000.00, 'Education payment', 'CANCELLED', 'RISK_REVIEW_REJECTED', 'Y', 'Y', 'Y', 'Y', 23);
    seed_transaction('SPV1-SHOWCASE-109', 'ishita.banerjee@gmail.com', 'Karthik Subramanian', 125000.00, 'Professional services', 'SETTLED', NULL, 'Y', 'Y', 'Y', 'Y', 22);
    seed_transaction('SPV1-SHOWCASE-110', 'karthik.subramanian@gmail.com', 'Neha Gupta', 120000.00, 'Home renovation', 'VERIFICATION_REQUIRED', NULL, 'Y', 'Y', 'Y', 'N', 2);
    seed_transaction('SPV1-SHOWCASE-111', 'neha.gupta@gmail.com', 'Aditya Joshi', 5000.01, 'Boundary transfer', 'SETTLED', NULL, 'Y', 'Y', 'Y', 'N', 20);
    seed_transaction('SPV1-SHOWCASE-112', 'aditya.joshi@gmail.com', 'Pooja Deshmukh', 25000.01, 'Boundary transfer', 'SETTLED', NULL, 'Y', 'Y', 'Y', 'N', 19);
    seed_transaction('SPV1-SHOWCASE-113', 'pooja.deshmukh@gmail.com', 'Sanjay Patel', 5000.00, 'Supplier payment', 'SETTLED', NULL, 'Y', 'Y', 'Y', 'N', 18);
    seed_transaction('SPV1-SHOWCASE-114', 'sanjay.patel@gmail.com', 'Divya Rao', 25000.00, 'Expense settlement', 'SETTLED', NULL, 'Y', 'Y', 'Y', 'N', 17);
    seed_transaction('SPV1-SHOWCASE-115', 'divya.rao@gmail.com', 'Manish Agarwal', 100000.00, 'Family support', 'SETTLED', NULL, 'Y', 'Y', 'Y', 'N', 16);
    seed_transaction('SPV1-SHOWCASE-116', 'manish.agarwal@gmail.com', 'Aisha Khan', 100000.01, 'Boundary verification case', 'VERIFICATION_REQUIRED', NULL, 'Y', 'Y', 'Y', 'N', 1);
    seed_transaction('SPV1-SHOWCASE-117', 'aisha.khan@gmail.com', 'Nikhil Chawla', 4000.00, 'Course material', 'CREATED', NULL, 'N', 'N', 'N', 'N', 5);
    seed_transaction('SPV1-SHOWCASE-118', 'nikhil.chawla@gmail.com', 'Meera Pillai', 8000.00, 'Shared purchase', 'AUTHORIZED', NULL, 'Y', 'N', 'N', 'N', 5);
    seed_transaction('SPV1-SHOWCASE-119', 'meera.pillai@gmail.com', 'Varun Kapoor', 1000.00, 'Emergency support', 'FAILED', 'INSUFFICIENT_AVAILABLE_BALANCE', 'Y', 'Y', 'N', 'N', 14);
    seed_transaction('SPV1-SHOWCASE-120', 'varun.kapoor@gmail.com', 'Ritu Singh', 3000.00, 'Trip settlement', 'CANCELLED', 'CUSTOMER_CANCELLED', 'N', 'N', 'N', 'N', 13);
    seed_transaction('SPV1-SHOWCASE-121', 'ritu.singh@gmail.com', 'Harish Gowda', 15000.00, 'Housing payment', 'CANCELLED', 'CUSTOMER_CANCELLED', 'Y', 'Y', 'Y', 'N', 12);
    seed_transaction('SPV1-SHOWCASE-122', 'harish.gowda@gmail.com', 'Tanvi Shah', 75000.00, 'Office collection', 'CANCELLED', 'CUSTOMER_CANCELLED', 'Y', 'Y', 'Y', 'N', 11);
    seed_transaction('SPV1-SHOWCASE-123', 'tanvi.shah@gmail.com', 'Suresh Yadav', 4500.00, 'Equipment purchase', 'RELEASED', NULL, 'Y', 'Y', 'Y', 'N', 3);
    seed_transaction('SPV1-SHOWCASE-124', 'suresh.yadav@gmail.com', 'Nandini Bose', 60000.00, 'Household transfer', 'RELEASED', NULL, 'Y', 'Y', 'Y', 'N', 3);
    seed_transaction('SPV1-SHOWCASE-128', 'mohit.sethi@gmail.com', 'Shruti Das', 1.00, 'Minimum supported amount', 'SETTLED', NULL, 'Y', 'Y', 'Y', 'N', 10);

    ---------------------------------------------------------------------------
    -- 2.7 Terminal OTP histories and review rounds. Transactions 105 and 116
    --     deliberately have no challenge so fresh end-to-end issuance is possible.
    ---------------------------------------------------------------------------

    seed_otp('SPV1-SHOWCASE-101', 'SHA-256$hjX7Ua4dNjQq0ZYZ7F4unA==$EfIxldqD+i9niKubsiDPWMB7mNw7acHD5Y415vO7Eoo=', 'VERIFIED', 1);
    seed_otp('SPV1-SHOWCASE-106', 'SHA-256$8pTEO6EF3kpRTPGB12CNsw==$1+Pi1iGF5M8iC3n2gNkxjEejxrra1GyHQhLYskKHFNM=', 'VERIFIED', 1);
    seed_otp('SPV1-SHOWCASE-107', 'SHA-256$EKVmOj0H8oiFuA/a+tGqFA==$b3OykWf//zu+njHQSYqFEO3U1xvkV2obudKIkJ1IDd0=', 'LOCKED', 3);
    seed_otp('SPV1-SHOWCASE-108', 'SHA-256$mWdJjpV2Fw6WKLjW+305dQ==$P8KV7juVxRwRCVpHAEJ0Pk2TCydR08cKnyKvEqAQGwU=', 'VERIFIED', 1);
    seed_otp('SPV1-SHOWCASE-109', 'SHA-256$/NRzBPvRJ6UpCMR+mlJ3AA==$7sNu7GnZHFezh6infQwfg3pNjEbOfm94qCYeeyHGAjI=', 'VERIFIED', 1);
    seed_otp('SPV1-SHOWCASE-110', 'SHA-256$kNHWMDfqKbGmRqRjy7Z4EQ==$ZUQKMyOLhe3HNrgGFqZPEoIzZxAMuyYpZ2KWfEvmchQ=', 'CANCELLED', 0);

    seed_review('SPV1-SHOWCASE-101', 'PENDING', NULL, 1);
    seed_review('SPV1-SHOWCASE-106', 'CANCELLED', NULL, 1);
    seed_review('SPV1-SHOWCASE-108', 'REJECTED', 'Beneficiary and purpose require additional independent confirmation.', 1);
    seed_review('SPV1-SHOWCASE-109', 'APPROVED', 'Payment purpose and customer confirmation were independently verified.', 1);
    seed_review('SPV1-SHOWCASE-110', 'REVERIFICATION_REQUESTED', 'Fresh customer verification is required before another review round.', 1);

    ---------------------------------------------------------------------------
    -- 2.8 Balanced settlement postings. The clearing account receives only
    --     the simulated external-submission side of settled transactions.
    ---------------------------------------------------------------------------

    seed_settlement_posting('SPV1-SHOWCASE-102');
    seed_settlement_posting('SPV1-SHOWCASE-104');
    seed_settlement_posting('SPV1-SHOWCASE-109');
    seed_settlement_posting('SPV1-SHOWCASE-111');
    seed_settlement_posting('SPV1-SHOWCASE-112');
    seed_settlement_posting('SPV1-SHOWCASE-113');
    seed_settlement_posting('SPV1-SHOWCASE-114');
    seed_settlement_posting('SPV1-SHOWCASE-115');
    seed_settlement_posting('SPV1-SHOWCASE-128');

    ---------------------------------------------------------------------------
    -- 2.9 Exception evidence: one resolved definitive case and one exhausted
    --     retry case retained safely in RELEASED for controlled intervention.
    ---------------------------------------------------------------------------

    seed_exception('SPV1-SHOWCASE-119', 'RESOLVED', 'INSUFFICIENT_AVAILABLE_BALANCE', 'Available funds were below the requested payment amount.', 'N', 0, 'Y');
    seed_exception('SPV1-SHOWCASE-124', 'MANUAL_REVIEW', 'TEMPORARY_SETTLEMENT_FAILURE', 'Automatic settlement retries were exhausted without a partial posting.', 'Y', 3, 'N');

    ---------------------------------------------------------------------------
    -- 2.10 Immutable audit and durable in-app notification examples.
    ---------------------------------------------------------------------------

    seed_audit('SPV1-SHOWCASE-101', 'priya.nair@gmail.com', 'CUSTOMER', 'OTP_VERIFIED', 'VERIFICATION_REQUIRED', 'PENDING_RISK_REVIEW', 'SUCCESS', NULL, '101-OTP');
    seed_audit('SPV1-SHOWCASE-101', NULL, NULL, 'RISK_REVIEW_PENDING', 'VERIFICATION_REQUIRED', 'PENDING_RISK_REVIEW', 'SUCCESS', NULL, '101-REVIEW');
    seed_audit('SPV1-SHOWCASE-102', NULL, NULL, 'PAYMENT_SETTLED', 'RELEASED', 'SETTLED', 'SUCCESS', NULL, '102-SETTLED');
    seed_audit('SPV1-SHOWCASE-103', 'kavya.sharma@gmail.com', 'CUSTOMER', 'PAYMENT_CANCELLED', 'PROTECTED', 'CANCELLED', 'SUCCESS', 'CUSTOMER_CANCELLED', '103-CANCELLED');
    seed_audit('SPV1-SHOWCASE-107', 'sneha.kulkarni@gmail.com', 'CUSTOMER', 'OTP_VERIFICATION_DENIED', 'VERIFICATION_REQUIRED', 'CANCELLED', 'DENIED', 'OTP_POLICY_EXHAUSTED', '107-OTP');
    seed_audit('SPV1-SHOWCASE-108', 'rhea.malhotra@gmail.com', 'RISK_OFFICER', 'RISK_REVIEW_REJECTED', 'PENDING_RISK_REVIEW', 'CANCELLED', 'SUCCESS', 'RISK_REVIEW_REJECTED', '108-REJECTED');
    seed_audit('SPV1-SHOWCASE-109', 'rhea.malhotra@gmail.com', 'RISK_OFFICER', 'RISK_REVIEW_APPROVED', 'PENDING_RISK_REVIEW', 'RELEASED', 'SUCCESS', NULL, '109-APPROVED');
    seed_audit('SPV1-SHOWCASE-109', NULL, NULL, 'PAYMENT_SETTLED', 'RELEASED', 'SETTLED', 'SUCCESS', NULL, '109-SETTLED');
    seed_audit('SPV1-SHOWCASE-110', 'rhea.malhotra@gmail.com', 'RISK_OFFICER', 'RISK_REVIEW_REVERIFICATION_REQUESTED', 'PENDING_RISK_REVIEW', 'VERIFICATION_REQUIRED', 'SUCCESS', NULL, '110-REVERIFY');
    seed_audit('SPV1-SHOWCASE-119', NULL, NULL, 'PAYMENT_FAILED', 'RISK_ASSESSED', 'FAILED', 'FAILED', 'INSUFFICIENT_AVAILABLE_BALANCE', '119-FAILED');
    seed_audit('SPV1-SHOWCASE-123', NULL, NULL, 'PAYMENT_RELEASED', 'RISK_ASSESSED', 'RELEASED', 'SUCCESS', NULL, '123-RELEASED');
    seed_audit('SPV1-SHOWCASE-124', NULL, NULL, 'SETTLEMENT_MANUAL_REVIEW_REQUIRED', 'RELEASED', 'RELEASED', 'FAILED', 'TEMPORARY_SETTLEMENT_FAILURE', '124-MANUAL');

    seed_notification('SPV1-SHOWCASE-101', 'priya.nair@gmail.com', 'RISK_REVIEW_PENDING', 'WARNING', 'Payment awaiting review', 'Your verified payment is awaiting an independent SafePay Risk Officer decision.', 'PENDING', 'N', '101-REVIEW');
    seed_notification('SPV1-SHOWCASE-102', 'arjun.menon@gmail.com', 'PAYMENT_SETTLED', 'INFO', 'Payment settled', 'Your payment completed SafePay simulated settlement.', 'DELIVERED', 'Y', '102-SETTLED');
    seed_notification('SPV1-SHOWCASE-103', 'kavya.sharma@gmail.com', 'PAYMENT_CANCELLED', 'INFO', 'Payment cancelled', 'Your payment was stopped before simulated settlement.', 'DELIVERED', 'Y', '103-CANCELLED');
    seed_notification('SPV1-SHOWCASE-105', 'ananya.iyer@gmail.com', 'OTP_REQUIRED', 'CRITICAL', 'Verification required', 'Enter the current verification code before this payment can move to Risk Officer review.', 'DELIVERED', 'N', '105-OTP');
    seed_notification('SPV1-SHOWCASE-107', 'sneha.kulkarni@gmail.com', 'PAYMENT_CANCELLED', 'WARNING', 'Payment cancelled', 'The payment was cancelled after the verification-attempt policy was exhausted.', 'DELIVERED', 'N', '107-CANCELLED');
    seed_notification('SPV1-SHOWCASE-108', 'rahul.verma@gmail.com', 'RISK_REVIEW_REJECTED', 'CRITICAL', 'Payment rejected', 'The Risk Officer rejected this held payment before settlement.', 'DELIVERED', 'N', '108-REJECTED');
    seed_notification('SPV1-SHOWCASE-109', 'ishita.banerjee@gmail.com', 'PAYMENT_SETTLED', 'INFO', 'Payment settled', 'The approved payment completed SafePay simulated settlement.', 'DELIVERED', 'Y', '109-SETTLED');
    seed_notification('SPV1-SHOWCASE-110', 'karthik.subramanian@gmail.com', 'OTP_REQUIRED', 'CRITICAL', 'Fresh verification required', 'The Risk Officer requested a fresh verification code before review can continue.', 'DELIVERED', 'N', '110-REVERIFY');
    seed_notification('SPV1-SHOWCASE-119', 'meera.pillai@gmail.com', 'PAYMENT_FAILED', 'WARNING', 'Payment failed safely', 'The payment did not reserve or move funds because the available balance was insufficient.', 'DELIVERED', 'N', '119-FAILED');
    seed_notification('SPV1-SHOWCASE-123', 'tanvi.shah@gmail.com', 'PAYMENT_RELEASED', 'INFO', 'Payment released', 'Your payment is ready for SafePay simulated settlement processing.', 'FAILED', 'N', '123-RELEASED');
END;
/

--------------------------------------------------------------------------------
-- 3. Migration-local acceptance checks. Any mismatch aborts V12.
--------------------------------------------------------------------------------

DECLARE
    l_count PLS_INTEGER;
BEGIN
    SELECT COUNT(*)
      INTO l_count
      FROM APP_USER seeded_user
      JOIN USER_ROLE assignment
        ON assignment.user_id = seeded_user.user_id
      JOIN APP_ROLE role
        ON role.role_id = assignment.role_id
     WHERE role.role_code = 'CUSTOMER'
       AND seeded_user.mobile_number BETWEEN
           '+919000000101' AND '+919000000130';

    IF l_count <> 30 THEN
        RAISE_APPLICATION_ERROR(
            -20310,
            'V12 verification failed: expected 30 customer personas'
        );
    END IF;

    SELECT COUNT(*)
      INTO l_count
      FROM APP_USER seeded_user
      JOIN USER_ROLE assignment
        ON assignment.user_id = seeded_user.user_id
      JOIN APP_ROLE role
        ON role.role_id = assignment.role_id
     WHERE seeded_user.mobile_number BETWEEN
           '+919000000131' AND '+919000000133'
       AND role.role_code IN (
           'RISK_OFFICER',
           'SYSTEM_ADMIN',
           'AUDITOR'
       );

    IF l_count <> 3 THEN
        RAISE_APPLICATION_ERROR(
            -20311,
            'V12 verification failed: expected three staff personas'
        );
    END IF;

    SELECT COUNT(*)
      INTO l_count
      FROM ACCOUNT account_row
     WHERE account_row.account_number IN (
        'SAFEPAY_OUTBOUND_CLEARING',
        'SAFEPAY_OPENING_BALANCE_CONTROL'
     )
       AND account_row.owner_user_id IS NULL
       AND account_row.status = 'ACTIVE'
       AND account_row.currency_code = 'INR';

    IF l_count <> 2 THEN
        RAISE_APPLICATION_ERROR(
            -20312,
            'V12 verification failed: system-account contract mismatch'
        );
    END IF;

    SELECT COUNT(*)
      INTO l_count
      FROM PAYMENT_TRANSACTION
     WHERE transaction_reference LIKE 'SPV1-SHOWCASE-%';

    IF l_count <> 25 THEN
        RAISE_APPLICATION_ERROR(
            -20313,
            'V12 verification failed: expected 25 transaction scenarios'
        );
    END IF;

    SELECT COUNT(*)
      INTO l_count
      FROM LEDGER_POSTING posting
     WHERE posting.posting_reference LIKE 'SPV1-%'
       AND posting.status = 'POSTED'
       AND NOT EXISTS (
            SELECT 1
              FROM (
                    SELECT
                        entry.posting_id,
                        COUNT(*) AS entry_count,
                        SUM(CASE WHEN entry.entry_type = 'DEBIT'
                                 THEN entry.amount ELSE 0 END)
                            AS debit_total,
                        SUM(CASE WHEN entry.entry_type = 'CREDIT'
                                 THEN entry.amount ELSE 0 END)
                            AS credit_total
                      FROM LEDGER_ENTRY entry
                     GROUP BY entry.posting_id
                   ) totals
             WHERE totals.posting_id = posting.posting_id
               AND totals.entry_count = 2
               AND totals.debit_total = posting.amount
               AND totals.credit_total = posting.amount
       );

    IF l_count <> 0 THEN
        RAISE_APPLICATION_ERROR(
            -20314,
            'V12 verification failed: a seeded posting is not balanced'
        );
    END IF;

    SELECT COUNT(*)
      INTO l_count
      FROM ACCOUNT account_row
      JOIN APP_USER owner_user
        ON owner_user.user_id = account_row.owner_user_id
     WHERE owner_user.mobile_number BETWEEN
           '+919000000101' AND '+919000000130'
       AND account_row.current_balance <>
           NVL((
                SELECT SUM(
                    CASE entry.entry_type
                        WHEN 'CREDIT' THEN entry.amount
                        WHEN 'DEBIT' THEN -entry.amount
                    END)
                  FROM LEDGER_ENTRY entry
                 WHERE entry.account_id = account_row.account_id
           ), 0);

    IF l_count <> 0 THEN
        RAISE_APPLICATION_ERROR(
            -20315,
            'V12 verification failed: customer balance does not reconcile to ledger'
        );
    END IF;

    SELECT COUNT(*)
      INTO l_count
      FROM ACCOUNT account_row
      JOIN APP_USER owner_user
        ON owner_user.user_id = account_row.owner_user_id
     WHERE owner_user.mobile_number BETWEEN
           '+919000000101' AND '+919000000130'
       AND account_row.reserved_amount <>
           NVL((
                SELECT SUM(transaction_row.reserved_amount)
                  FROM PAYMENT_TRANSACTION transaction_row
                 WHERE transaction_row.source_account_id =
                       account_row.account_id
                   AND transaction_row.state IN (
                       'PROTECTED',
                       'VERIFICATION_REQUIRED',
                       'PENDING_RISK_REVIEW',
                       'RELEASED'
                   )
           ), 0);

    IF l_count <> 0 THEN
        RAISE_APPLICATION_ERROR(
            -20316,
            'V12 verification failed: reserved balances do not reconcile'
        );
    END IF;

    SELECT COUNT(*)
      INTO l_count
      FROM PAYMENT_OTP_CHALLENGE challenge
      JOIN PAYMENT_TRANSACTION transaction_row
        ON transaction_row.transaction_id = challenge.transaction_id
     WHERE transaction_row.transaction_reference LIKE 'SPV1-SHOWCASE-%'
       AND (
            challenge.status = 'PENDING'
            OR challenge.otp_hash NOT LIKE 'SHA-256$%$%'
       );

    IF l_count <> 0 THEN
        RAISE_APPLICATION_ERROR(
            -20317,
            'V12 verification failed: OTP seed safety contract mismatch'
        );
    END IF;

    SELECT COUNT(*)
      INTO l_count
      FROM ACCOUNT clearing
     WHERE clearing.account_number = 'SAFEPAY_OUTBOUND_CLEARING'
       AND clearing.current_balance = (
            SELECT NVL(SUM(entry.amount), 0)
              FROM LEDGER_ENTRY entry
             WHERE entry.account_id = clearing.account_id
               AND entry.entry_type = 'CREDIT'
       );

    IF l_count <> 1 THEN
        RAISE_APPLICATION_ERROR(
            -20318,
            'V12 verification failed: outbound-clearing balance mismatch'
        );
    END IF;
END;
/
