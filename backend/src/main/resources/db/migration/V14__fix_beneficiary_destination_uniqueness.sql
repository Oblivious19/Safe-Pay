-- Oracle treats NULL values in a composite unique constraint as comparable when
-- another key column is populated. The former UPI constraint therefore prevented
-- an owner from having more than one BANK_ACCOUNT beneficiary (all have UPI_ID NULL).
-- Use method-specific function-based unique indexes so each destination type is
-- checked only against other destinations of the same type.

ALTER TABLE BENEFICIARY DROP CONSTRAINT UK_BEN_OWNER_BANK;

ALTER TABLE BENEFICIARY DROP CONSTRAINT UK_BEN_OWNER_UPI;

CREATE UNIQUE INDEX UX_BEN_OWNER_BANK_DEST
    ON BENEFICIARY (
        CASE WHEN payment_method = 'BANK_ACCOUNT' THEN owner_user_id END,
        CASE WHEN payment_method = 'BANK_ACCOUNT' THEN bank_account_number END,
        CASE WHEN payment_method = 'BANK_ACCOUNT' THEN ifsc_code END
    );

CREATE UNIQUE INDEX UX_BEN_OWNER_UPI_DEST
    ON BENEFICIARY (
        CASE WHEN payment_method = 'UPI' THEN owner_user_id END,
        CASE WHEN payment_method = 'UPI' THEN upi_id END
    );
