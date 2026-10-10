CREATE INDEX idx_soko_order_buyer_store_status
    ON pms_soko_order(customer_user_id, store_id, status, active);
