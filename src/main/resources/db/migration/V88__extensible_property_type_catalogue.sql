-- Additive only. Built-in identifiers, mapping rows, units and properties remain intact.
CREATE TABLE pms_custom_property_type (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 uuid BINARY(16) NOT NULL UNIQUE,
 created_on DATETIME(6),created_by BIGINT,last_modified_date DATETIME(6),
 code VARCHAR(64) NOT NULL UNIQUE,
 name VARCHAR(160) NOT NULL,
 description VARCHAR(1000) NULL,
 category VARCHAR(32) NOT NULL,
 active BIT NOT NULL DEFAULT 1,
 version BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE pms_custom_property_unit_type (
 property_type_id BIGINT NOT NULL,
 unit_type VARCHAR(64) NOT NULL,
 PRIMARY KEY(property_type_id,unit_type),
 CONSTRAINT fk_custom_property_unit_type FOREIGN KEY(property_type_id) REFERENCES pms_custom_property_type(id)
);
ALTER TABLE pms_property ADD COLUMN type_category VARCHAR(32) NULL;
