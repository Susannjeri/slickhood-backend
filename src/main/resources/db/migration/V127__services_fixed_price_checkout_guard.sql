-- Services checkout currently captures one immutable whole-job price. Variable
-- unit listings (hour, item, distance, area, etc.) need a separate quantity
-- and customer quote-acceptance workflow before they can be sold safely.
-- Return legacy public listings to an editable state instead of silently
-- charging one unit or leaving them apparently live but unbookable.
UPDATE pms_sp_service
SET status = 'DRAFT',
    last_modified_date = UTC_TIMESTAMP(6)
WHERE active = 1
  AND status = 'LISTED'
  AND (pricing_unit IS NULL OR pricing_unit NOT IN ('PER_JOB', 'FIXED'));
