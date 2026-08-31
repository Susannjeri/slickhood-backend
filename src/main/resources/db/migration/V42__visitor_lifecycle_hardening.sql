-- Build the visitor hot-path indexes online so the production table remains available.
ALTER TABLE pms_visitor
    ADD COLUMN decision_reason VARCHAR(250) NULL,
    ADD INDEX idx_visitor_unit_active_status_arrival (unit_id, active, status, expected_arrival_time),
    ADD INDEX idx_visitor_property_active_arrival (property_id, active, expected_arrival_time),
    ADD INDEX idx_visitor_active_phone (active, phone_number),
    ADD INDEX idx_visitor_expiry_scan (active, status, valid_until, expected_arrival_time),
    ALGORITHM=INPLACE,
    LOCK=NONE;
