-- Manual prerequisite for registration with the PASSWORD_HASH Java mapping.
-- Stop the backend and take your usual backup before applying this once.
-- Requires 08_user_account_foundation.sql to be completed and verified.
-- Keeps the legacy column and all existing values; no trigger or dual writes.
-- Oracle DDL commits implicitly. Do not rerun the earlier migration.
DECLARE
    v_missing NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_missing FROM users WHERE password_hash IS NULL;
    IF v_missing > 0 THEN
        RAISE_APPLICATION_ERROR(-20001, 'Backfill PASSWORD_HASH before this transition.');
    END IF;
    EXECUTE IMMEDIATE 'ALTER TABLE users MODIFY (password NULL)';
END;
/

SELECT column_name, nullable
FROM user_tab_columns
WHERE table_name = 'USERS'
  AND column_name IN ('PASSWORD', 'PASSWORD_HASH');
-- Expected: PASSWORD = Y; PASSWORD_HASH = N.
-- Old application versions writing only PASSWORD are no longer compatible.
