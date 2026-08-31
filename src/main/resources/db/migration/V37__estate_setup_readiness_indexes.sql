CREATE INDEX idx_unit_property_active
    ON pms_unit (property_id, active);

CREATE INDEX idx_property_manager_property_active
    ON pms_property_manager (property_id, active);

CREATE INDEX idx_ownership_property_active
    ON pms_property_ownership (property_id, active);

CREATE INDEX idx_estate_budget_property_year_active
    ON pms_estate_budget (property_id, budget_year, active);
