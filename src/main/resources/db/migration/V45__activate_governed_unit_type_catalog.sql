-- Existing unit-type mappings predate active-state governance and were created
-- with the Java boolean default (false). Activate them once, then allow the
-- super-admin catalogue to manage active state from this point forward.
UPDATE pms_unit_type_mapping SET active = 1;

CREATE INDEX idx_unit_type_mapping_property_active
    ON pms_unit_type_mapping (property_type, active);
