-- New SafePay-internal payment instructions retain the verified recipient
-- account selected at creation. Existing historical instructions remain null.

ALTER TABLE PAYMENT_TRANSACTION ADD (
    destination_account_id NUMBER(19,0)
);

ALTER TABLE PAYMENT_TRANSACTION ADD CONSTRAINT FK_TX_DEST_ACCOUNT
    FOREIGN KEY (destination_account_id)
    REFERENCES ACCOUNT (account_id);

CREATE INDEX IX_TX_DEST_ACCOUNT
    ON PAYMENT_TRANSACTION (destination_account_id);
