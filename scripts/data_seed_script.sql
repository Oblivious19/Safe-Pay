SET DEFINE OFF
SET SERVEROUTPUT ON
WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK

DECLARE
    v_count NUMBER;

    -- Public demo password: SafePay@Test123
    c_password_hash CONSTANT VARCHAR2(255) :=
        '$2a$10$QJ4FNRJ0taidDKSBGtHduOlLipKseyrt1HF.E0ZWCSzXmKkRezL1i';

    PROCEDURE ensure_role(p_role_name VARCHAR2) IS
        v_exists NUMBER;
    BEGIN
        SELECT COUNT(*)
          INTO v_exists
          FROM roles
         WHERE role_name = p_role_name;

        IF v_exists = 0 THEN
            INSERT INTO roles (
                role_id,
                role_name
            ) VALUES (
                seq_role_id.NEXTVAL,
                p_role_name
            );
        END IF;
    END ensure_role;

    PROCEDURE add_demo_user(
        p_name      VARCHAR2,
        p_email     VARCHAR2,
        p_phone     VARCHAR2,
        p_role_name VARCHAR2
    ) IS
        v_user_id NUMBER;
        v_role_id NUMBER;
    BEGIN
        SELECT role_id
          INTO v_role_id
          FROM roles
         WHERE role_name = p_role_name;

        v_user_id := seq_user_id.NEXTVAL;

        INSERT INTO users (
            user_id,
            role_id,
            name,
            email,
            phone,
            password_hash,
            status,
            failed_login_attempts,
            locked_until,
            created_at,
            updated_at
        ) VALUES (
            v_user_id,
            v_role_id,
            p_name,
            p_email,
            p_phone,
            c_password_hash,
            'ACTIVE',
            0,
            NULL,
            LOCALTIMESTAMP,
            LOCALTIMESTAMP
        );

        IF p_role_name = 'CUSTOMER' THEN
            INSERT INTO account (
                account_id,
                user_id,
                account_number,
                account_type,
                status,
                balance,
                created_at,
                updated_at
            ) VALUES (
                seq_account_id.NEXTVAL,
                v_user_id,
                TO_CHAR(
                    seq_account_number.NEXTVAL,
                    'FM99999999999999999999'
                ),
                'SAVINGS',
                'ACTIVE',
                200000.00,
                LOCALTIMESTAMP,
                LOCALTIMESTAMP
            );
        END IF;
    END add_demo_user;

BEGIN
    IF SYS_CONTEXT('USERENV', 'SESSION_USER') <> 'SAFEPAY_TEAM'
       OR SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA') <> 'SAFEPAY_TEAM'
       OR SYS_CONTEXT('USERENV', 'CON_NAME') <> 'FREEPDB1'
    THEN
        RAISE_APPLICATION_ERROR(
            -20001,
            'Connect as SAFEPAY_TEAM in FREEPDB1 before running this seed.'
        );
    END IF;

    SELECT COUNT(*)
      INTO v_count
      FROM users;

    IF v_count <> 0 THEN
        RAISE_APPLICATION_ERROR(
            -20002,
            'USERS already contains data. Seed stopped without overwriting it.'
        );
    END IF;

    SELECT COUNT(*)
      INTO v_count
      FROM account;

    IF v_count <> 0 THEN
        RAISE_APPLICATION_ERROR(
            -20003,
            'ACCOUNT already contains data. Seed stopped without overwriting it.'
        );
    END IF;

    ensure_role('CUSTOMER');
    ensure_role('ADMIN');

    add_demo_user(
        'Vikram Bhat',
        'vikram.bhat@gmail.com',
        '9876543210',
        'ADMIN'
    );

    add_demo_user(
        'Priya Nair',
        'priya.nair@gmail.com',
        '9876543211',
        'CUSTOMER'
    );

    add_demo_user(
        'Kavya Sharma',
        'kavya.sharma@gmail.com',
        '9876543212',
        'CUSTOMER'
    );

    add_demo_user(
        'Arjun Menon',
        'arjun.menon@gmail.com',
        '9876543213',
        'CUSTOMER'
    );

    add_demo_user(
        'Neha Iyer',
        'neha.iyer@gmail.com',
        '9876543214',
        'CUSTOMER'
    );

    COMMIT;

    DBMS_OUTPUT.PUT_LINE(
        'Seed successful: 1 admin, 4 customers, 4 savings accounts.'
    );

EXCEPTION
    WHEN OTHERS THEN
        ROLLBACK;
        RAISE;
END;
/