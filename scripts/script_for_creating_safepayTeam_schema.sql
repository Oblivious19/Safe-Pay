WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK
SET SERVEROUTPUT ON
SET VERIFY OFF
SET ECHO OFF
SET DEFINE ON

DECLARE
    n NUMBER;
BEGIN
    IF SYS_CONTEXT('USERENV','CON_NAME') <> 'FREEPDB1' THEN
        RAISE_APPLICATION_ERROR(-20001, 'Connect to FREEPDB1 first.');
    END IF;

    SELECT COUNT(*) INTO n
    FROM all_users
    WHERE username = 'SAFEPAY_TEAM';

    IF n <> 0 THEN
        RAISE_APPLICATION_ERROR(
            -20002, 'SAFEPAY_TEAM already exists. Stop and inspect; do not drop it.');
    END IF;

    SELECT COUNT(*) INTO n
    FROM dba_tablespaces
    WHERE tablespace_name IN ('USERS','TEMP');

    IF n <> 2 THEN
        RAISE_APPLICATION_ERROR(-20003, 'Expected USERS and TEMP tablespaces.');
    END IF;
END;
/

-- For this substitution prompt, use letters/numbers/#/$/_ only.
ACCEPT team_password CHAR PROMPT 'New SAFEPAY_TEAM password: ' HIDE

CREATE USER SAFEPAY_TEAM IDENTIFIED BY "&team_password"
    DEFAULT TABLESPACE USERS
    TEMPORARY TABLESPACE TEMP
    QUOTA 100M ON USERS;

UNDEFINE team_password

GRANT CREATE SESSION, CREATE TABLE, CREATE SEQUENCE, CREATE VIEW
TO SAFEPAY_TEAM;

SELECT username, account_status
FROM dba_users
WHERE username = 'SAFEPAY_TEAM';

SELECT privilege
FROM dba_sys_privs
WHERE grantee = 'SAFEPAY_TEAM'
ORDER BY privilege;