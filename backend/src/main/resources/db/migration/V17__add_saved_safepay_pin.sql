ALTER TABLE safepay_owner.app_user ADD (
    safe_pay_pin_hash VARCHAR2(255 CHAR),
    safe_pay_pin_updated_at TIMESTAMP WITH TIME ZONE
);
