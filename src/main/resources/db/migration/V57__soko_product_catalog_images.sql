CREATE TABLE pms_soko_product_image (
 id BIGINT NOT NULL AUTO_INCREMENT, uuid BINARY(16) NOT NULL, created_on DATETIME(6), active BIT NOT NULL,
 created_by BIGINT, last_modified_date DATETIME(6), product_id BIGINT NOT NULL, file_ref VARCHAR(500) NOT NULL,
 content_type VARCHAR(50) NOT NULL, file_size BIGINT NOT NULL, display_order INT NOT NULL,
 PRIMARY KEY(id), UNIQUE KEY uk_soko_product_image_uuid(uuid),
 KEY idx_soko_product_image_product(product_id,active,display_order),
 CONSTRAINT fk_soko_product_image_product FOREIGN KEY(product_id) REFERENCES pms_soko_product(id)
);

-- Identity is already verified by account KYC. Do not ask service providers to upload it again.
DELETE FROM pms_sp_category_doc_types WHERE document_type IN ('NATIONAL_ID','PASSPORT');

-- Referees are not a default onboarding gate. Categories can opt in later if a measured risk requires it.
UPDATE pms_sp_category SET required_number_of_referees=0 WHERE required_number_of_referees>0;
