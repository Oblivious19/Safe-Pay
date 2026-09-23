ALTER TABLE safepay_owner.app_user ADD (
    safe_pay_pin_reset_status VARCHAR2(20 CHAR) DEFAULT 'NONE' NOT NULL
);
