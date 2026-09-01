CREATE TABLE pms_property_listing (
    id BIGINT NOT NULL AUTO_INCREMENT,
    uuid BINARY(16) NOT NULL,
    created_on DATETIME(6),
    active BIT NOT NULL,
    created_by BIGINT,
    last_modified_date DATETIME(6),
    unit_id BIGINT NOT NULL,
    public_slug VARCHAR(180) NOT NULL,
    listing_type VARCHAR(12) NOT NULL,
    status VARCHAR(20) NOT NULL,
    headline VARCHAR(180) NOT NULL,
    description VARCHAR(2000),
    image_manifest TEXT,
    publisher_user_id BIGINT NOT NULL,
    published_at DATETIME(6),
    unpublished_at DATETIME(6),
    expires_at DATETIME(6),
    suspended_at DATETIME(6),
    suspended_by BIGINT,
    suspension_reason VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_property_listing_uuid (uuid),
    UNIQUE KEY uk_property_listing_unit (unit_id),
    UNIQUE KEY uk_property_listing_slug (public_slug),
    KEY idx_listing_public (status, listing_type, published_at),
    KEY idx_listing_publisher (publisher_user_id, status),
    CONSTRAINT fk_property_listing_unit FOREIGN KEY (unit_id) REFERENCES pms_unit(id),
    CONSTRAINT fk_property_listing_publisher FOREIGN KEY (publisher_user_id) REFERENCES pms_users(id)
);

CREATE TABLE pms_property_listing_inquiry (
    id BIGINT NOT NULL AUTO_INCREMENT,
    uuid BINARY(16) NOT NULL,
    created_on DATETIME(6),
    active BIT NOT NULL,
    listing_id BIGINT NOT NULL,
    name VARCHAR(120) NOT NULL,
    email VARCHAR(180) NOT NULL,
    phone VARCHAR(40),
    message VARCHAR(1000) NOT NULL,
    status VARCHAR(20) NOT NULL,
    fingerprint_hash CHAR(64) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_property_listing_inquiry_uuid (uuid),
    KEY idx_listing_inquiry_queue (listing_id, status, created_on),
    CONSTRAINT fk_property_listing_inquiry_listing FOREIGN KEY (listing_id) REFERENCES pms_property_listing(id)
);

CREATE INDEX idx_unit_public_listing ON pms_unit (advertise, occupied, active, property_id);

-- Preserve eligible selections made before this release. New publications use non-sequential random slugs.
INSERT INTO pms_property_listing
    (uuid, created_on, active, created_by, last_modified_date, unit_id, public_slug, listing_type, status,
     headline, description, publisher_user_id, published_at, expires_at, version)
SELECT UUID_TO_BIN(UUID()), NOW(6), 1, p.created_by, NOW(6), u.id,
       CONCAT('property-', LOWER(HEX(RANDOM_BYTES(6)))),
       CASE WHEN p.management_mode='SALE' THEN 'SALE' ELSE 'RENT' END,
       'PUBLISHED',
       CONCAT(REPLACE(LOWER(u.unit_type), '_', ' '), ' at ', p.name,
              CASE WHEN p.management_mode='SALE' THEN ' for sale' ELSE ' to rent' END),
       CONCAT('Discover this property in ', SUBSTRING_INDEX(p.address, ',', 2),
              '. Contact the owner or appointed agent through Slickhood to arrange a viewing.'),
       p.created_by, NOW(6), DATE_ADD(NOW(6), INTERVAL 90 DAY), 0
FROM pms_unit u
JOIN pms_property p ON p.id=u.property_id
WHERE u.advertise=1 AND u.occupied=0 AND u.active=1 AND p.active=1
  AND u.price>0 AND COALESCE(u.currency,'')<>'' AND COALESCE(u.unit_type,'')<>''
  AND ((COALESCE(u.image_path,'')<>'' AND COALESCE(u.thumbnail,'')<>'')
    OR (COALESCE(p.image_path,'')<>'' AND COALESCE(p.thumbnail,'')<>''));
