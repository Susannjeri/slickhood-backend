ALTER TABLE pms_sp_service
    ADD COLUMN title VARCHAR(120) NULL,
    ADD COLUMN description VARCHAR(2000) NULL;

ALTER TABLE pms_sp_profile
    ADD COLUMN description VARCHAR(1000) NULL,
    ADD COLUMN service_area VARCHAR(255) NULL,
    ADD COLUMN reviewed_by_user_id BIGINT NULL,
    ADD COLUMN reviewed_at DATETIME(6) NULL,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    MODIFY COLUMN admin_notes VARCHAR(1000) NULL;

ALTER TABLE pms_sp_booking
    ADD COLUMN client_request_id VARCHAR(100) NULL,
    ADD COLUMN service_address VARCHAR(500) NULL,
    ADD COLUMN dispute_deadline_at DATETIME(6) NULL,
    ADD UNIQUE INDEX ux_sp_booking_customer_request (created_by, client_request_id),
    MODIFY COLUMN notes VARCHAR(2000) NULL,
    MODIFY COLUMN cancellation_reason VARCHAR(1000) NULL,
    MODIFY COLUMN completion_evidence_reference VARCHAR(500) NULL;

-- Existing completed work predates the customer dispute window. Mark it as
-- already elapsed so rollout does not leave historical settlements blocked.
UPDATE pms_sp_booking
SET dispute_deadline_at = COALESCE(completed_at, last_modified_date, UTC_TIMESTAMP(6))
WHERE status = 'COMPLETED'
  AND dispute_deadline_at IS NULL;

ALTER TABLE pms_sp_complaint
    MODIFY COLUMN description VARCHAR(2000) NULL,
    MODIFY COLUMN admin_notes VARCHAR(2000) NULL,
    MODIFY COLUMN resolution VARCHAR(2000) NULL,
    ADD COLUMN public_resolution VARCHAR(2000) NULL;

-- Older WARN handling appended internal complaint evidence to the provider's
-- visible onboarding note. Preserve the original review prefix and remove only
-- the exact legacy suffix; the complaint row remains the internal source.
UPDATE pms_sp_profile
SET admin_notes = NULLIF(TRIM(SUBSTRING_INDEX(admin_notes, ' | Warning: ', 1)), '')
WHERE admin_notes LIKE '% | Warning: %';

-- Existing upheld complaints must enter the same refund hold as new decisions.
-- Do not disturb an in-flight or confirmed (including partial) refund.
UPDATE pms_sp_booking booking
JOIN pms_sp_complaint complaint
  ON complaint.booking_id = booking.id
 AND complaint.active = 1
 AND complaint.status = 'RESOLVED'
 AND complaint.resolution = 'UPHELD'
SET booking.refund_status = 'REQUESTED',
    booking.refunded_amount = COALESCE(booking.refunded_amount, booking.quoted_amount)
WHERE booking.payment_status = 'PAID'
  AND COALESCE(booking.refund_status, 'NOT_REQUIRED') NOT IN ('REQUESTED', 'PROCESSING', 'CONFIRMED');

-- A useful launch catalogue for real services. Administrators can refine or
-- deactivate these entries later; grocery retail deliberately belongs to Soko.
INSERT INTO pms_sp_category
    (uuid, created_on, active, created_by, last_modified_date, name, description, required_number_of_referees, version)
SELECT UUID_TO_BIN(UUID()), UTC_TIMESTAMP(6), 1, NULL, UTC_TIMESTAMP(6), catalogue.name, catalogue.description, 0, 0
FROM (
    SELECT 'Home cleaning' name, 'Routine, deep, move-in and move-out cleaning for homes and offices.' description
    UNION ALL SELECT 'Plumbing', 'Leak repairs, installations, drainage and general plumbing work.'
    UNION ALL SELECT 'Electrical', 'Electrical installation, fault diagnosis, repairs and maintenance.'
    UNION ALL SELECT 'Carpentry & furniture', 'Furniture assembly, cabinetry, joinery and wood repairs.'
    UNION ALL SELECT 'Painting & decorating', 'Interior and exterior painting, finishing and decorating.'
    UNION ALL SELECT 'Appliance repair', 'Diagnosis, servicing and repair of household and office appliances.'
    UNION ALL SELECT 'Gardening & landscaping', 'Garden care, landscaping, pruning and outdoor maintenance.'
    UNION ALL SELECT 'Moving & delivery', 'Household or office moving, collection and local delivery services.'
    UNION ALL SELECT 'Pest control', 'Inspection and treatment for common household and commercial pests.'
    UNION ALL SELECT 'Laundry & ironing', 'Laundry, dry-cleaning coordination, ironing and collection services.'
    UNION ALL SELECT 'Beauty & grooming', 'Hair, barbering, nails, makeup and personal grooming services.'
    UNION ALL SELECT 'Event services', 'Event planning, catering support, decor, photography and entertainment.'
    UNION ALL SELECT 'ICT & device support', 'Computer, phone, network, software and technical support services.'
    UNION ALL SELECT 'Professional & business services', 'Accounting, legal, design, consulting and other professional support.'
) catalogue
WHERE NOT EXISTS (
    SELECT 1 FROM pms_sp_category existing WHERE LOWER(existing.name)=LOWER(catalogue.name)
);

UPDATE pms_sp_service
SET title = LEFT(category_name, 120)
WHERE title IS NULL OR TRIM(title) = '';

UPDATE pms_sp_service s
JOIN pms_sp_category c ON c.id = s.category_id
SET s.description = COALESCE(
    NULLIF(TRIM(c.description), ''),
    CONCAT('Professional ', LOWER(c.name), ' service. Contact the provider to confirm the scope and timing.')
)
WHERE s.description IS NULL OR TRIM(s.description) = '';

UPDATE pms_sp_service s
JOIN pms_sp_category c ON c.id = s.category_id
SET s.status = 'HIDDEN'
WHERE s.active = 1
  AND s.status = 'LISTED'
  AND (LOWER(c.name) LIKE '%grocer%' OR LOWER(c.name) LIKE '%supermarket%');

UPDATE pms_sp_category
SET active = 0,
    version = version + 1
WHERE active = 1
  AND (LOWER(name) LIKE '%grocer%' OR LOWER(name) LIKE '%supermarket%');

-- A customer gets one durable dispute record per booking. Keep the oldest
-- active case if historical concurrency created duplicates, while retaining
-- the later rows as inactive audit history.
UPDATE pms_sp_complaint duplicate_complaint
JOIN pms_sp_complaint original_complaint
  ON original_complaint.booking_id = duplicate_complaint.booking_id
 AND original_complaint.active = 1
 AND duplicate_complaint.active = 1
 AND original_complaint.id < duplicate_complaint.id
SET duplicate_complaint.active = 0;

ALTER TABLE pms_sp_complaint
    ADD COLUMN active_booking_guard BIGINT
        GENERATED ALWAYS AS (CASE WHEN active = 1 THEN booking_id ELSE NULL END) STORED,
    ADD UNIQUE INDEX ux_sp_complaint_active_booking (active_booking_guard);

DELETE duplicate_rating
FROM pms_sp_rating duplicate_rating
JOIN pms_sp_rating original_rating
  ON original_rating.booking_id = duplicate_rating.booking_id
 AND original_rating.rated_by_user_id = duplicate_rating.rated_by_user_id
 AND original_rating.id < duplicate_rating.id;

ALTER TABLE pms_sp_rating
    MODIFY COLUMN comment VARCHAR(1000) NULL,
    ADD UNIQUE INDEX ux_sp_rating_booking_rater (booking_id, rated_by_user_id);

-- Rating de-duplication must also repair the latest persisted reputation
-- snapshot. A listing cannot retain TRUSTED status solely because of rows
-- removed above.
UPDATE pms_sp_risk_score risk_score
JOIN (
    SELECT service_id, MAX(id) AS latest_id
    FROM pms_sp_risk_score
    WHERE active = 1
    GROUP BY service_id
) latest ON latest.latest_id = risk_score.id
SET risk_score.highly_rated_completed_count = (
    SELECT COUNT(*)
    FROM pms_sp_rating rating
    WHERE rating.active = 1
      AND rating.service_id = risk_score.service_id
      AND rating.stars >= COALESCE((
          SELECT MAX(config.int_value)
          FROM pms_config config
          WHERE config.name = 'SP Trusted Min Stars (qualifying star floor)'
      ), 4)
);

UPDATE pms_sp_risk_score risk_score
JOIN (
    SELECT service_id, MAX(id) AS latest_id
    FROM pms_sp_risk_score
    WHERE active = 1
    GROUP BY service_id
) latest ON latest.latest_id = risk_score.id
SET risk_score.label = 'VERIFIED'
WHERE risk_score.label = 'TRUSTED'
  AND risk_score.highly_rated_completed_count < COALESCE((
      SELECT MAX(config.int_value)
      FROM pms_config config
      WHERE config.name = 'SP Trusted Threshold (min highly-rated bookings)'
  ), 10);
