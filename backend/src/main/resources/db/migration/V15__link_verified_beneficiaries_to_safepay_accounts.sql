-- A verified BANK_ACCOUNT beneficiary is linked to the internal SafePay
-- customer account that matched its account number, owner name, bank and IFSC.
-- The column remains nullable so existing beneficiaries remain valid but
-- unverified until they are recreated through the verification flow.

ALTER TABLE BENEFICIARY ADD (
    destination_account_id NUMBER(19,0)
);

ALTER TABLE BENEFICIARY ADD CONSTRAINT FK_BEN_DEST_ACCOUNT
    FOREIGN KEY (destination_account_id)
    REFERENCES ACCOUNT (account_id);

CREATE INDEX IX_BEN_DEST_ACCOUNT
    ON BENEFICIARY (destination_account_id);
