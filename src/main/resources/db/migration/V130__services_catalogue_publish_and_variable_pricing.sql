-- Variable-price bookings retain the exact commercial terms accepted by the
-- buyer. Existing bookings were created under the one-unit fixed-price model.
ALTER TABLE pms_sp_booking
    ADD COLUMN pricing_unit_rate DECIMAL(19,2) NULL,
    ADD COLUMN unit_quantity DECIMAL(19,4) NOT NULL DEFAULT 1.0000,
    ADD COLUMN scheduled_end_at DATETIME(6) NULL;

UPDATE pms_sp_booking
SET pricing_unit_rate = COALESCE(quoted_amount, 0.00)
WHERE pricing_unit_rate IS NULL;

ALTER TABLE pms_sp_booking
    MODIFY COLUMN pricing_unit_rate DECIMAL(19,2) NOT NULL;

-- The API permits useful category copy up to 1,000 characters.
ALTER TABLE pms_sp_category
    MODIFY COLUMN description VARCHAR(1000) NULL;

-- Prefer an existing "Plumbing services" record. If only the earlier
-- "Plumbing" entry exists, retain that record and give it the canonical name.
SET @plumbing_category_id = COALESCE(
    (SELECT MIN(id) FROM pms_sp_category WHERE LOWER(TRIM(name)) = 'plumbing services'),
    (SELECT MIN(id) FROM pms_sp_category WHERE LOWER(TRIM(name)) IN ('plumbing', 'plumbing service'))
);

INSERT INTO pms_sp_category
    (uuid, created_on, active, created_by, last_modified_date, name, description,
     required_number_of_referees, version)
SELECT UUID_TO_BIN(UUID()), UTC_TIMESTAMP(6), 1, NULL, UTC_TIMESTAMP(6),
       'Plumbing services',
       'Installation, leak and drainage diagnosis, repairs, maintenance and replacement of plumbing fixtures and pipework.',
       0, 0
WHERE @plumbing_category_id IS NULL;

SET @plumbing_category_id = COALESCE(
    @plumbing_category_id,
    (SELECT MIN(id) FROM pms_sp_category WHERE LOWER(TRIM(name)) = 'plumbing services')
);
SET @plumbing_category_ids = (
    SELECT GROUP_CONCAT(id ORDER BY id) FROM pms_sp_category
    WHERE LOWER(TRIM(name)) IN ('plumbing', 'plumbing service', 'plumbing services')
);

INSERT INTO pms_sp_category_doc_types (category_id, document_type)
SELECT DISTINCT @plumbing_category_id, legacy.document_type
FROM pms_sp_category_doc_types legacy
LEFT JOIN pms_sp_category_doc_types existing
  ON existing.category_id = @plumbing_category_id
 AND existing.document_type = legacy.document_type
WHERE FIND_IN_SET(CAST(legacy.category_id AS CHAR), @plumbing_category_ids) > 0
  AND existing.category_id IS NULL;

SET @plumbing_required_referees = COALESCE((
    SELECT MAX(required_number_of_referees) FROM pms_sp_category
    WHERE FIND_IN_SET(CAST(id AS CHAR), @plumbing_category_ids) > 0
), 0);

-- Preserve category-specific KYC from every published or draft matrix release.
INSERT INTO pms_kyc_matrix_requirement
    (uuid, created_on, active, created_by, last_modified_date, release_id, scope_type, scope_key,
     scope_label, requirement_code, requirement_label, obligation, profile_scope,
     accepted_document_types, condition_description, condition_rule, condition_value,
     validity_days, renewal_lead_days)
SELECT UUID_TO_BIN(UUID()), UTC_TIMESTAMP(6), legacy.active, legacy.created_by, UTC_TIMESTAMP(6),
       legacy.release_id, 'SERVICE_CATEGORY', CAST(@plumbing_category_id AS CHAR), 'Plumbing services',
       legacy.requirement_code, legacy.requirement_label, legacy.obligation, legacy.profile_scope,
       legacy.accepted_document_types, legacy.condition_description, legacy.condition_rule, legacy.condition_value,
       legacy.validity_days, legacy.renewal_lead_days
FROM pms_kyc_matrix_requirement legacy
JOIN (
    SELECT release_id, requirement_code,
           SUBSTRING_INDEX(GROUP_CONCAT(id ORDER BY
               CASE obligation WHEN 'MANDATORY' THEN 3 WHEN 'CONDITIONAL' THEN 2 ELSE 1 END DESC,
               id ASC), ',', 1) AS id
    FROM pms_kyc_matrix_requirement
    WHERE scope_type = 'SERVICE_CATEGORY'
      AND FIND_IN_SET(scope_key, @plumbing_category_ids) > 0
      AND scope_key <> CAST(@plumbing_category_id AS CHAR)
    GROUP BY release_id, requirement_code
) picked ON picked.id = legacy.id
LEFT JOIN pms_kyc_matrix_requirement existing
  ON existing.release_id = legacy.release_id
 AND existing.scope_type = 'SERVICE_CATEGORY'
 AND existing.scope_key = CAST(@plumbing_category_id AS CHAR)
 AND existing.requirement_code = legacy.requirement_code
WHERE existing.id IS NULL;

-- If the canonical category already had the same requirement code, retain the
-- strongest active rule rather than silently keeping a weaker alias. This is
-- important for published releases where duplicate category names evolved
-- independently before this catalogue consolidation.
UPDATE pms_kyc_matrix_requirement canonical
JOIN (
    SELECT release_id, requirement_code,
           SUBSTRING_INDEX(GROUP_CONCAT(id ORDER BY active DESC,
               CASE obligation WHEN 'MANDATORY' THEN 3 WHEN 'CONDITIONAL' THEN 2 ELSE 1 END DESC,
               id ASC), ',', 1) AS strongest_id
    FROM pms_kyc_matrix_requirement
    WHERE scope_type = 'SERVICE_CATEGORY'
      AND FIND_IN_SET(scope_key, @plumbing_category_ids) > 0
    GROUP BY release_id, requirement_code
) picked
  ON picked.release_id = canonical.release_id
 AND picked.requirement_code = canonical.requirement_code
JOIN pms_kyc_matrix_requirement strongest ON strongest.id = picked.strongest_id
SET canonical.active = GREATEST(canonical.active, strongest.active),
    canonical.requirement_label = strongest.requirement_label,
    canonical.obligation = strongest.obligation,
    canonical.profile_scope = strongest.profile_scope,
    canonical.accepted_document_types = strongest.accepted_document_types,
    canonical.condition_description = strongest.condition_description,
    canonical.condition_rule = strongest.condition_rule,
    canonical.condition_value = strongest.condition_value,
    canonical.validity_days = strongest.validity_days,
    canonical.renewal_lead_days = strongest.renewal_lead_days,
    canonical.last_modified_date = UTC_TIMESTAMP(6)
WHERE canonical.scope_type = 'SERVICE_CATEGORY'
  AND canonical.scope_key = CAST(@plumbing_category_id AS CHAR);

UPDATE pms_kyc_matrix_requirement
SET scope_label = 'Plumbing services', last_modified_date = UTC_TIMESTAMP(6)
WHERE scope_type = 'SERVICE_CATEGORY' AND scope_key = CAST(@plumbing_category_id AS CHAR);

UPDATE pms_sp_category
SET required_number_of_referees = GREATEST(required_number_of_referees, @plumbing_required_referees),
    name = 'Plumbing services',
    description = CASE
        WHEN description IS NULL OR CHAR_LENGTH(TRIM(description)) < 20
          OR LOWER(TRIM(description)) = LOWER(TRIM(name))
          OR description = 'Leak repairs, installations, drainage and general plumbing work.'
        THEN 'Installation, leak and drainage diagnosis, repairs, maintenance and replacement of plumbing fixtures and pipework.'
        ELSE description END,
    active = 1, last_modified_date = UTC_TIMESTAMP(6), version = version + 1
WHERE id = @plumbing_category_id;

UPDATE pms_sp_service
SET category_id = @plumbing_category_id,
    category_name = 'Plumbing services',
    title = CASE WHEN LOWER(TRIM(title)) IN ('plumbing', 'plumbing service')
                 THEN 'Plumbing services' ELSE title END,
    last_modified_date = UTC_TIMESTAMP(6)
WHERE FIND_IN_SET(CAST(category_id AS CHAR), @plumbing_category_ids) > 0;

UPDATE pms_sp_category
SET active = 0, last_modified_date = UTC_TIMESTAMP(6), version = version + 1
WHERE FIND_IN_SET(CAST(id AS CHAR), @plumbing_category_ids) > 0
  AND id <> @plumbing_category_id;

-- "Cooking" is ambiguous retail language. Keep its historical evidence and
-- listings under the clear professional category "Chef services".
SET @chef_category_id = COALESCE(
    (SELECT MIN(id) FROM pms_sp_category WHERE LOWER(TRIM(name)) = 'chef services'),
    (SELECT MIN(id) FROM pms_sp_category
     WHERE LOWER(TRIM(name)) IN ('chef', 'chef service', 'cooking', 'cooking service', 'cooking services'))
);

INSERT INTO pms_sp_category
    (uuid, created_on, active, created_by, last_modified_date, name, description,
     required_number_of_referees, version)
SELECT UUID_TO_BIN(UUID()), UTC_TIMESTAMP(6), 1, NULL, UTC_TIMESTAMP(6),
       'Chef services',
       'Professional chefs for in-home meals, private dining, menu planning, meal preparation and special occasions.',
       0, 0
WHERE @chef_category_id IS NULL;

SET @chef_category_id = COALESCE(
    @chef_category_id,
    (SELECT MIN(id) FROM pms_sp_category WHERE LOWER(TRIM(name)) = 'chef services')
);
SET @chef_category_ids = (
    SELECT GROUP_CONCAT(id ORDER BY id) FROM pms_sp_category
    WHERE LOWER(TRIM(name)) IN ('chef', 'chef service', 'chef services',
                                'cooking', 'cooking service', 'cooking services')
);

INSERT INTO pms_sp_category_doc_types (category_id, document_type)
SELECT DISTINCT @chef_category_id, legacy.document_type
FROM pms_sp_category_doc_types legacy
LEFT JOIN pms_sp_category_doc_types existing
  ON existing.category_id = @chef_category_id
 AND existing.document_type = legacy.document_type
WHERE FIND_IN_SET(CAST(legacy.category_id AS CHAR), @chef_category_ids) > 0
  AND existing.category_id IS NULL;

SET @chef_required_referees = COALESCE((
    SELECT MAX(required_number_of_referees) FROM pms_sp_category
    WHERE FIND_IN_SET(CAST(id AS CHAR), @chef_category_ids) > 0
), 0);

INSERT INTO pms_kyc_matrix_requirement
    (uuid, created_on, active, created_by, last_modified_date, release_id, scope_type, scope_key,
     scope_label, requirement_code, requirement_label, obligation, profile_scope,
     accepted_document_types, condition_description, condition_rule, condition_value,
     validity_days, renewal_lead_days)
SELECT UUID_TO_BIN(UUID()), UTC_TIMESTAMP(6), legacy.active, legacy.created_by, UTC_TIMESTAMP(6),
       legacy.release_id, 'SERVICE_CATEGORY', CAST(@chef_category_id AS CHAR), 'Chef services',
       legacy.requirement_code, legacy.requirement_label, legacy.obligation, legacy.profile_scope,
       legacy.accepted_document_types, legacy.condition_description, legacy.condition_rule, legacy.condition_value,
       legacy.validity_days, legacy.renewal_lead_days
FROM pms_kyc_matrix_requirement legacy
JOIN (
    SELECT release_id, requirement_code,
           SUBSTRING_INDEX(GROUP_CONCAT(id ORDER BY
               CASE obligation WHEN 'MANDATORY' THEN 3 WHEN 'CONDITIONAL' THEN 2 ELSE 1 END DESC,
               id ASC), ',', 1) AS id
    FROM pms_kyc_matrix_requirement
    WHERE scope_type = 'SERVICE_CATEGORY'
      AND FIND_IN_SET(scope_key, @chef_category_ids) > 0
      AND scope_key <> CAST(@chef_category_id AS CHAR)
    GROUP BY release_id, requirement_code
) picked ON picked.id = legacy.id
LEFT JOIN pms_kyc_matrix_requirement existing
  ON existing.release_id = legacy.release_id
 AND existing.scope_type = 'SERVICE_CATEGORY'
 AND existing.scope_key = CAST(@chef_category_id AS CHAR)
 AND existing.requirement_code = legacy.requirement_code
WHERE existing.id IS NULL;

UPDATE pms_kyc_matrix_requirement canonical
JOIN (
    SELECT release_id, requirement_code,
           SUBSTRING_INDEX(GROUP_CONCAT(id ORDER BY active DESC,
               CASE obligation WHEN 'MANDATORY' THEN 3 WHEN 'CONDITIONAL' THEN 2 ELSE 1 END DESC,
               id ASC), ',', 1) AS strongest_id
    FROM pms_kyc_matrix_requirement
    WHERE scope_type = 'SERVICE_CATEGORY'
      AND FIND_IN_SET(scope_key, @chef_category_ids) > 0
    GROUP BY release_id, requirement_code
) picked
  ON picked.release_id = canonical.release_id
 AND picked.requirement_code = canonical.requirement_code
JOIN pms_kyc_matrix_requirement strongest ON strongest.id = picked.strongest_id
SET canonical.active = GREATEST(canonical.active, strongest.active),
    canonical.requirement_label = strongest.requirement_label,
    canonical.obligation = strongest.obligation,
    canonical.profile_scope = strongest.profile_scope,
    canonical.accepted_document_types = strongest.accepted_document_types,
    canonical.condition_description = strongest.condition_description,
    canonical.condition_rule = strongest.condition_rule,
    canonical.condition_value = strongest.condition_value,
    canonical.validity_days = strongest.validity_days,
    canonical.renewal_lead_days = strongest.renewal_lead_days,
    canonical.last_modified_date = UTC_TIMESTAMP(6)
WHERE canonical.scope_type = 'SERVICE_CATEGORY'
  AND canonical.scope_key = CAST(@chef_category_id AS CHAR);

UPDATE pms_kyc_matrix_requirement
SET scope_label = 'Chef services', last_modified_date = UTC_TIMESTAMP(6)
WHERE scope_type = 'SERVICE_CATEGORY' AND scope_key = CAST(@chef_category_id AS CHAR);

UPDATE pms_sp_category
SET required_number_of_referees = GREATEST(required_number_of_referees, @chef_required_referees),
    name = 'Chef services',
    description = CASE
        WHEN description IS NULL OR CHAR_LENGTH(TRIM(description)) < 20
          OR LOWER(TRIM(description)) = LOWER(TRIM(name))
        THEN 'Professional chefs for in-home meals, private dining, menu planning, meal preparation and special occasions.'
        ELSE description END,
    active = 1, last_modified_date = UTC_TIMESTAMP(6), version = version + 1
WHERE id = @chef_category_id;

UPDATE pms_sp_service
SET category_id = @chef_category_id,
    category_name = 'Chef services',
    title = CASE WHEN LOWER(TRIM(title)) IN ('chef', 'chef service', 'cooking', 'cooking service', 'cooking services')
                 THEN 'Chef services' ELSE title END,
    last_modified_date = UTC_TIMESTAMP(6)
WHERE FIND_IN_SET(CAST(category_id AS CHAR), @chef_category_ids) > 0;

UPDATE pms_sp_category
SET active = 0, last_modified_date = UTC_TIMESTAMP(6), version = version + 1
WHERE FIND_IN_SET(CAST(id AS CHAR), @chef_category_ids) > 0
  AND id <> @chef_category_id;

-- Catering remains separate because event food preparation, staffing and
-- service logistics are materially different from booking a private chef.
SET @catering_category_id = COALESCE(
    (SELECT MIN(id) FROM pms_sp_category WHERE LOWER(TRIM(name)) = 'catering services'),
    (SELECT MIN(id) FROM pms_sp_category WHERE LOWER(TRIM(name)) IN ('catering', 'catering service'))
);

INSERT INTO pms_sp_category
    (uuid, created_on, active, created_by, last_modified_date, name, description,
     required_number_of_referees, version)
SELECT UUID_TO_BIN(UUID()), UTC_TIMESTAMP(6), 1, NULL, UTC_TIMESTAMP(6),
       'Catering services',
       'Food and beverage preparation, delivery, setup and service staffing for meetings, celebrations and organised events.',
       0, 0
WHERE @catering_category_id IS NULL;

SET @catering_category_id = COALESCE(
    @catering_category_id,
    (SELECT MIN(id) FROM pms_sp_category WHERE LOWER(TRIM(name)) = 'catering services')
);
SET @catering_category_ids = (
    SELECT GROUP_CONCAT(id ORDER BY id) FROM pms_sp_category
    WHERE LOWER(TRIM(name)) IN ('catering', 'catering service', 'catering services')
);

INSERT INTO pms_sp_category_doc_types (category_id, document_type)
SELECT DISTINCT @catering_category_id, legacy.document_type
FROM pms_sp_category_doc_types legacy
LEFT JOIN pms_sp_category_doc_types existing
  ON existing.category_id = @catering_category_id
 AND existing.document_type = legacy.document_type
WHERE FIND_IN_SET(CAST(legacy.category_id AS CHAR), @catering_category_ids) > 0
  AND existing.category_id IS NULL;

SET @catering_required_referees = COALESCE((
    SELECT MAX(required_number_of_referees) FROM pms_sp_category
    WHERE FIND_IN_SET(CAST(id AS CHAR), @catering_category_ids) > 0
), 0);

INSERT INTO pms_kyc_matrix_requirement
    (uuid, created_on, active, created_by, last_modified_date, release_id, scope_type, scope_key,
     scope_label, requirement_code, requirement_label, obligation, profile_scope,
     accepted_document_types, condition_description, condition_rule, condition_value,
     validity_days, renewal_lead_days)
SELECT UUID_TO_BIN(UUID()), UTC_TIMESTAMP(6), legacy.active, legacy.created_by, UTC_TIMESTAMP(6),
       legacy.release_id, 'SERVICE_CATEGORY', CAST(@catering_category_id AS CHAR), 'Catering services',
       legacy.requirement_code, legacy.requirement_label, legacy.obligation, legacy.profile_scope,
       legacy.accepted_document_types, legacy.condition_description, legacy.condition_rule, legacy.condition_value,
       legacy.validity_days, legacy.renewal_lead_days
FROM pms_kyc_matrix_requirement legacy
JOIN (
    SELECT release_id, requirement_code,
           SUBSTRING_INDEX(GROUP_CONCAT(id ORDER BY
               CASE obligation WHEN 'MANDATORY' THEN 3 WHEN 'CONDITIONAL' THEN 2 ELSE 1 END DESC,
               id ASC), ',', 1) AS id
    FROM pms_kyc_matrix_requirement
    WHERE scope_type = 'SERVICE_CATEGORY'
      AND FIND_IN_SET(scope_key, @catering_category_ids) > 0
      AND scope_key <> CAST(@catering_category_id AS CHAR)
    GROUP BY release_id, requirement_code
) picked ON picked.id = legacy.id
LEFT JOIN pms_kyc_matrix_requirement existing
  ON existing.release_id = legacy.release_id
 AND existing.scope_type = 'SERVICE_CATEGORY'
 AND existing.scope_key = CAST(@catering_category_id AS CHAR)
 AND existing.requirement_code = legacy.requirement_code
WHERE existing.id IS NULL;

UPDATE pms_kyc_matrix_requirement canonical
JOIN (
    SELECT release_id, requirement_code,
           SUBSTRING_INDEX(GROUP_CONCAT(id ORDER BY active DESC,
               CASE obligation WHEN 'MANDATORY' THEN 3 WHEN 'CONDITIONAL' THEN 2 ELSE 1 END DESC,
               id ASC), ',', 1) AS strongest_id
    FROM pms_kyc_matrix_requirement
    WHERE scope_type = 'SERVICE_CATEGORY'
      AND FIND_IN_SET(scope_key, @catering_category_ids) > 0
    GROUP BY release_id, requirement_code
) picked
  ON picked.release_id = canonical.release_id
 AND picked.requirement_code = canonical.requirement_code
JOIN pms_kyc_matrix_requirement strongest ON strongest.id = picked.strongest_id
SET canonical.active = GREATEST(canonical.active, strongest.active),
    canonical.requirement_label = strongest.requirement_label,
    canonical.obligation = strongest.obligation,
    canonical.profile_scope = strongest.profile_scope,
    canonical.accepted_document_types = strongest.accepted_document_types,
    canonical.condition_description = strongest.condition_description,
    canonical.condition_rule = strongest.condition_rule,
    canonical.condition_value = strongest.condition_value,
    canonical.validity_days = strongest.validity_days,
    canonical.renewal_lead_days = strongest.renewal_lead_days,
    canonical.last_modified_date = UTC_TIMESTAMP(6)
WHERE canonical.scope_type = 'SERVICE_CATEGORY'
  AND canonical.scope_key = CAST(@catering_category_id AS CHAR);

UPDATE pms_kyc_matrix_requirement
SET scope_label = 'Catering services', last_modified_date = UTC_TIMESTAMP(6)
WHERE scope_type = 'SERVICE_CATEGORY' AND scope_key = CAST(@catering_category_id AS CHAR);

UPDATE pms_sp_category
SET required_number_of_referees = GREATEST(required_number_of_referees, @catering_required_referees),
    name = 'Catering services',
    description = CASE
        WHEN description IS NULL OR CHAR_LENGTH(TRIM(description)) < 20
          OR LOWER(TRIM(description)) = LOWER(TRIM(name))
        THEN 'Food and beverage preparation, delivery, setup and service staffing for meetings, celebrations and organised events.'
        ELSE description END,
    active = 1, last_modified_date = UTC_TIMESTAMP(6), version = version + 1
WHERE id = @catering_category_id;

UPDATE pms_sp_service
SET category_id = @catering_category_id,
    category_name = 'Catering services',
    title = CASE WHEN LOWER(TRIM(title)) IN ('catering', 'catering service')
                 THEN 'Catering services' ELSE title END,
    last_modified_date = UTC_TIMESTAMP(6)
WHERE FIND_IN_SET(CAST(category_id AS CHAR), @catering_category_ids) > 0;

UPDATE pms_sp_category
SET active = 0, last_modified_date = UTC_TIMESTAMP(6), version = version + 1
WHERE FIND_IN_SET(CAST(id AS CHAR), @catering_category_ids) > 0
  AND id <> @catering_category_id;

-- Event services no longer implies catering now that catering has its own
-- catalogue entry. Fill remaining weak catalogue and listing copy conservatively.
UPDATE pms_sp_category
SET description = 'Event planning, coordination, decor, photography, entertainment and event-day support.',
    last_modified_date = UTC_TIMESTAMP(6), version = version + 1
WHERE LOWER(TRIM(name)) = 'event services'
  AND (description IS NULL OR CHAR_LENGTH(TRIM(description)) < 20
       OR LOWER(TRIM(description)) = LOWER(TRIM(name))
       OR description = 'Event planning, catering support, decor, photography and entertainment.');

UPDATE pms_sp_category
SET description = CONCAT('Find and book verified providers offering ', LOWER(TRIM(name)),
                         '. Compare the scope, price, provider rating and availability before requesting the service.'),
    last_modified_date = UTC_TIMESTAMP(6), version = version + 1
WHERE active = 1
  AND (description IS NULL OR CHAR_LENGTH(TRIM(description)) < 20
       OR LOWER(TRIM(description)) = LOWER(TRIM(name)));

UPDATE pms_sp_service service
JOIN pms_sp_category category ON category.id = service.category_id
SET service.description = LEFT(COALESCE(NULLIF(TRIM(category.description), ''),
        CONCAT('Professional ', LOWER(TRIM(category.name)),
               ' service with a clear scope, price and booking arrangement.')), 2000),
    service.last_modified_date = UTC_TIMESTAMP(6)
WHERE service.description IS NULL OR CHAR_LENGTH(TRIM(service.description)) < 20
   OR LOWER(TRIM(service.description)) = LOWER(TRIM(service.category_name))
   OR LOWER(TRIM(service.description)) = LOWER(TRIM(service.title));

-- Grocery retail belongs in Soko. Preserve history but remove active retail
-- categories and listings from the Services catalogue.
UPDATE pms_sp_service service
JOIN pms_sp_category category ON category.id = service.category_id
SET service.status = 'HIDDEN', service.last_modified_date = UTC_TIMESTAMP(6)
WHERE service.active = 1
  AND service.status IN ('APPROVED', 'LISTED')
  AND LOWER(TRIM(category.name)) IN ('grocery shop', 'grocery shops');

UPDATE pms_sp_category
SET active = 0, last_modified_date = UTC_TIMESTAMP(6), version = version + 1
WHERE active = 1
  AND LOWER(TRIM(name)) IN ('grocery shop', 'grocery shops');
