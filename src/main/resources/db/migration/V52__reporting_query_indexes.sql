-- Bounded reports filter by lifecycle state and a date range, then return newest rows first.
-- These indexes keep that path index-backed as operational tables grow.
CREATE INDEX idx_report_invoice_active_created ON pms_invoice(active, created_on);
CREATE INDEX idx_report_payment_created ON pms_payment(created_on);
CREATE INDEX idx_report_sale_active_created ON pms_sale_transaction(active, created_on);
CREATE INDEX idx_report_charge_active_created ON pms_estate_service_charge(active, created_on);
CREATE INDEX idx_report_booking_active_created ON pms_sp_booking(active, created_on);
CREATE INDEX idx_report_soko_active_created ON pms_soko_order(active, created_on);
CREATE INDEX idx_report_subscription_created ON pms_user_subscription(created_on);
CREATE INDEX idx_report_kyc_active_created ON pms_kyc_case(active, created_on);
CREATE INDEX idx_report_notification_created ON pms_notification(created_on);
CREATE INDEX idx_report_maintenance_active_created ON pms_maintenance_work_order(active, created_on);
CREATE INDEX idx_report_affiliate_active_created ON pms_affiliate_commission(active, created_on);
