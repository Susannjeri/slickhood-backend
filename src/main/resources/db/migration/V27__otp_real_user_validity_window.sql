UPDATE pms_config
SET int_value = 300,
    updated_on = CURRENT_TIMESTAMP
WHERE name = 'OTP(SMS & E-Mail) Validity In Seconds';
