ALTER TABLE pms_sp_booking
    ADD COLUMN service_title_snapshot VARCHAR(120) NULL,
    ADD COLUMN service_category_snapshot VARCHAR(255) NULL,
    ADD COLUMN service_description_snapshot VARCHAR(2000) NULL,
    ADD COLUMN provider_business_name_snapshot VARCHAR(255) NULL;

-- Preserve what the buyer actually booked. Later catalogue/profile edits must
-- not rewrite completed work, invoice support context, or dispute evidence.
UPDATE pms_sp_booking booking
JOIN pms_sp_service service ON service.id = booking.service_id
LEFT JOIN pms_sp_profile profile ON profile.id = service.profile_id
SET booking.service_title_snapshot = COALESCE(NULLIF(TRIM(service.title), ''), service.category_name),
    booking.service_category_snapshot = service.category_name,
    booking.service_description_snapshot = service.description,
    booking.provider_business_name_snapshot = profile.business_name
WHERE booking.service_title_snapshot IS NULL
   OR booking.service_category_snapshot IS NULL
   OR booking.provider_business_name_snapshot IS NULL;
