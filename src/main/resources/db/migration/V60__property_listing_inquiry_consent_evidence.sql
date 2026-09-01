-- Preserve the notice version and timestamp accepted with each public enquiry.
-- Existing rows came through the required V58 consent control and are backfilled
-- with the release notice version at migration time.
ALTER TABLE pms_property_listing_inquiry
    ADD COLUMN consent_version VARCHAR(40) NOT NULL DEFAULT 'property-enquiry-2026-09',
    ADD COLUMN consented_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);
