-- SafePay conditional purpose metadata. Forward-only; no legacy backfill.
-- Review and apply through the user's coordinated Flyway deployment.
ALTER TABLE PAYMENT_TRANSACTION ADD (
    payment_category VARCHAR2(20 CHAR)
);

ALTER TABLE PAYMENT_TRANSACTION ADD CONSTRAINT CK_PAY_CATEGORY_VALUES
    CHECK (payment_category IN ('MEDICAL', 'LOAN', 'FRIENDS_FAMILY', 'INVESTMENTS', 'OTHERS'));

ALTER TABLE PAYMENT_TRANSACTION ADD CONSTRAINT CK_PAY_CATEGORY_AMOUNT
    CHECK (payment_category IS NULL OR amount > 100000.00);

ALTER TABLE PAYMENT_TRANSACTION ADD CONSTRAINT CK_PAY_CATEGORY_PURPOSE
    CHECK (
        payment_category IS NULL
        OR payment_category <> 'OTHERS'
        OR (
            purpose IS NOT NULL
            AND LENGTH(purpose) BETWEEN 1 AND 140
            AND purpose = TRIM(purpose)
            AND REGEXP_LIKE(purpose, '[^[:space:]]')
        )
    );

-- High-value NULL remains legal for historical rows. New-payment validation
-- requires a category in the application; existing purpose/state/balances stay intact.
