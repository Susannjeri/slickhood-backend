-- Support the status-only feed, bounded expiry worker and cross-listing enquiry queue
-- without changing the V58 schema or weakening its uniqueness constraints.
CREATE INDEX idx_listing_status_published
    ON pms_property_listing (status, published_at, id);

CREATE INDEX idx_listing_expiry
    ON pms_property_listing (status, expires_at, id);

CREATE INDEX idx_listing_inquiry_active_created
    ON pms_property_listing_inquiry (active, created_on, id);
