/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_affiliate_commission` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `uuid` binary(16) NOT NULL,
  `created_on` datetime(6) DEFAULT NULL,
  `active` bit(1) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `last_modified_date` datetime(6) DEFAULT NULL,
  `affiliate_user_id` bigint NOT NULL,
  `referred_user_id` bigint NOT NULL,
  `invoice_id` bigint NOT NULL,
  `invoice_ref` varchar(255) DEFAULT NULL,
  `qualifying_amount` decimal(38,2) DEFAULT NULL,
  `commission_rate` decimal(38,2) DEFAULT NULL,
  `commission_amount` decimal(38,2) DEFAULT NULL,
  `currency` varchar(255) DEFAULT NULL,
  `status` varchar(255) DEFAULT NULL,
  `provider_reference` varchar(255) DEFAULT NULL,
  `earned_at` datetime(6) DEFAULT NULL,
  `payout_id` bigint DEFAULT NULL,
  `eligible_sequence` int NOT NULL,
  `reversed_at` datetime(6) DEFAULT NULL,
  `reversal_reason` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_commission_uuid` (`uuid`),
  UNIQUE KEY `idx_commission_invoice` (`invoice_id`),
  UNIQUE KEY `uk_affiliate_referred_sequence` (`referred_user_id`,`eligible_sequence`),
  KEY `idx_commission_affiliate` (`affiliate_user_id`,`status`),
  KEY `fk_commission_payout` (`payout_id`),
  CONSTRAINT `fk_commission_affiliate` FOREIGN KEY (`affiliate_user_id`) REFERENCES `pms_users` (`id`),
  CONSTRAINT `fk_commission_invoice` FOREIGN KEY (`invoice_id`) REFERENCES `pms_invoice` (`id`),
  CONSTRAINT `fk_commission_payout` FOREIGN KEY (`payout_id`) REFERENCES `pms_affiliate_payout` (`id`),
  CONSTRAINT `fk_commission_referred` FOREIGN KEY (`referred_user_id`) REFERENCES `pms_users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_affiliate_payout` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `uuid` binary(16) NOT NULL,
  `created_on` datetime(6) DEFAULT NULL,
  `active` bit(1) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `last_modified_date` datetime(6) DEFAULT NULL,
  `payout_number` varchar(255) DEFAULT NULL,
  `affiliate_user_id` bigint NOT NULL,
  `payment_account_id` bigint NOT NULL,
  `amount` decimal(38,2) DEFAULT NULL,
  `currency` varchar(255) DEFAULT NULL,
  `status` varchar(255) DEFAULT NULL,
  `requested_at` datetime(6) DEFAULT NULL,
  `processed_at` datetime(6) DEFAULT NULL,
  `payment_reference` varchar(255) DEFAULT NULL,
  `notes` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payout_uuid` (`uuid`),
  UNIQUE KEY `uk_payout_number` (`payout_number`),
  KEY `idx_payout_affiliate` (`affiliate_user_id`,`status`),
  KEY `fk_payout_account` (`payment_account_id`),
  CONSTRAINT `fk_payout_account` FOREIGN KEY (`payment_account_id`) REFERENCES `pms_payment_account` (`id`),
  CONSTRAINT `fk_payout_affiliate` FOREIGN KEY (`affiliate_user_id`) REFERENCES `pms_users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_affiliate_profile` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `uuid` binary(16) NOT NULL,
  `created_on` datetime(6) DEFAULT NULL,
  `active` bit(1) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `last_modified_date` datetime(6) DEFAULT NULL,
  `user_id` bigint NOT NULL,
  `referral_code` varchar(255) DEFAULT NULL,
  `status` varchar(255) DEFAULT NULL,
  `commission_rate` decimal(38,2) DEFAULT NULL,
  `minimum_payout` decimal(38,2) DEFAULT NULL,
  `currency` varchar(255) DEFAULT NULL,
  `payout_account_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_affiliate_uuid` (`uuid`),
  UNIQUE KEY `idx_affiliate_user` (`user_id`),
  UNIQUE KEY `idx_affiliate_code` (`referral_code`),
  KEY `fk_affiliate_account` (`payout_account_id`),
  CONSTRAINT `fk_affiliate_account` FOREIGN KEY (`payout_account_id`) REFERENCES `pms_payment_account` (`id`),
  CONSTRAINT `fk_affiliate_user` FOREIGN KEY (`user_id`) REFERENCES `pms_users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_affiliate_referral` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `uuid` binary(16) NOT NULL,
  `created_on` datetime(6) DEFAULT NULL,
  `active` bit(1) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `last_modified_date` datetime(6) DEFAULT NULL,
  `affiliate_user_id` bigint NOT NULL,
  `referred_user_id` bigint NOT NULL,
  `referral_code` varchar(255) DEFAULT NULL,
  `status` varchar(255) DEFAULT NULL,
  `campaign` varchar(255) DEFAULT NULL,
  `registered_at` datetime(6) DEFAULT NULL,
  `converted_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_referral_uuid` (`uuid`),
  UNIQUE KEY `idx_referral_referred` (`referred_user_id`),
  KEY `idx_referral_affiliate` (`affiliate_user_id`,`status`),
  CONSTRAINT `fk_referral_affiliate` FOREIGN KEY (`affiliate_user_id`) REFERENCES `pms_users` (`id`),
  CONSTRAINT `fk_referral_user` FOREIGN KEY (`referred_user_id`) REFERENCES `pms_users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_bulk_unit_job` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_on` datetime(6) DEFAULT NULL,
  `active` bit(1) NOT NULL,
  `created_by` bigint NOT NULL,
  `last_modified_date` datetime(6) DEFAULT NULL,
  `completed` bit(1) NOT NULL,
  `count` int NOT NULL,
  `description` varchar(255) DEFAULT NULL,
  `email` varchar(255) DEFAULT NULL,
  `unit_id` bigint NOT NULL,
  `uuid` binary(16) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_charge_type` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_on` datetime(6) DEFAULT NULL,
  `active` bit(1) NOT NULL,
  `name` varchar(255) DEFAULT NULL,
  `uuid` binary(16) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_community_fund` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `uuid` binary(16) NOT NULL,
  `property_id` bigint NOT NULL,
  `name` varchar(180) NOT NULL,
  `fund_type` varchar(30) NOT NULL,
  `contributor_scope` varchar(30) NOT NULL,
  `description` varchar(2000) NOT NULL,
  `currency` varchar(3) NOT NULL,
  `target_amount` decimal(19,2) NOT NULL,
  `default_contribution` decimal(19,2) NOT NULL,
  `opens_on` date NOT NULL,
  `due_date` date NOT NULL,
  `closes_on` date DEFAULT NULL,
  `status` varchar(24) NOT NULL,
  `payment_account_id` bigint NOT NULL,
  `custodian_user_id` bigint NOT NULL,
  `dual_approval_required` bit(1) NOT NULL DEFAULT b'1',
  `active` bit(1) NOT NULL DEFAULT b'1',
  `created_by` bigint DEFAULT NULL,
  `created_on` datetime(6) DEFAULT NULL,
  `last_modified_date` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_community_fund_uuid` (`uuid`),
  KEY `fk_community_fund_payment_account` (`payment_account_id`),
  KEY `idx_community_fund_property` (`property_id`,`active`,`status`),
  KEY `idx_community_fund_creator` (`created_by`,`active`),
  CONSTRAINT `fk_community_fund_payment_account` FOREIGN KEY (`payment_account_id`) REFERENCES `pms_payment_account` (`id`),
  CONSTRAINT `fk_community_fund_property` FOREIGN KEY (`property_id`) REFERENCES `pms_property` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_community_fund_contribution` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `uuid` binary(16) NOT NULL,
  `fund_id` bigint NOT NULL,
  `contributor_user_id` bigint NOT NULL,
  `unit_id` bigint NOT NULL,
  `assessed_amount` decimal(19,2) NOT NULL,
  `paid_amount` decimal(19,2) NOT NULL DEFAULT '0.00',
  `invoice_id` bigint DEFAULT NULL,
  `status` varchar(24) NOT NULL,
  `paid_at` datetime(6) DEFAULT NULL,
  `payment_reference` varchar(120) DEFAULT NULL,
  `active` bit(1) NOT NULL DEFAULT b'1',
  `created_by` bigint DEFAULT NULL,
  `created_on` datetime(6) DEFAULT NULL,
  `last_modified_date` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fund_contribution_uuid` (`uuid`),
  UNIQUE KEY `uk_fund_contributor_unit` (`fund_id`,`contributor_user_id`,`unit_id`),
  KEY `fk_fund_contribution_unit` (`unit_id`),
  KEY `idx_fund_contribution_user` (`contributor_user_id`,`active`),
  KEY `idx_fund_contribution_invoice` (`invoice_id`),
  CONSTRAINT `fk_fund_contribution_fund` FOREIGN KEY (`fund_id`) REFERENCES `pms_community_fund` (`id`),
  CONSTRAINT `fk_fund_contribution_invoice` FOREIGN KEY (`invoice_id`) REFERENCES `pms_invoice` (`id`),
  CONSTRAINT `fk_fund_contribution_unit` FOREIGN KEY (`unit_id`) REFERENCES `pms_unit` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_community_fund_expenditure` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `uuid` binary(16) NOT NULL,
  `fund_id` bigint NOT NULL,
  `purpose` varchar(1000) NOT NULL,
  `category` varchar(40) NOT NULL,
  `amount` decimal(19,2) NOT NULL,
  `beneficiary_type` varchar(40) NOT NULL,
  `beneficiary_user_id` bigint DEFAULT NULL,
  `beneficiary_name` varchar(200) NOT NULL,
  `beneficiary_reference` varchar(120) DEFAULT NULL,
  `status` varchar(24) NOT NULL,
  `approved_by` bigint DEFAULT NULL,
  `approved_at` datetime(6) DEFAULT NULL,
  `paid_by` bigint DEFAULT NULL,
  `paid_at` datetime(6) DEFAULT NULL,
  `payment_reference` varchar(120) DEFAULT NULL,
  `evidence_file_ref` varchar(800) DEFAULT NULL,
  `rejection_reason` varchar(1000) DEFAULT NULL,
  `active` bit(1) NOT NULL DEFAULT b'1',
  `created_by` bigint DEFAULT NULL,
  `created_on` datetime(6) DEFAULT NULL,
  `last_modified_date` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fund_expenditure_uuid` (`uuid`),
  KEY `idx_fund_expenditure_fund` (`fund_id`,`active`,`status`),
  KEY `idx_fund_expenditure_beneficiary` (`beneficiary_user_id`),
  CONSTRAINT `fk_fund_expenditure_fund` FOREIGN KEY (`fund_id`) REFERENCES `pms_community_fund` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_community_fund_transaction` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `uuid` binary(16) NOT NULL,
  `fund_id` bigint NOT NULL,
  `event_key` varchar(190) NOT NULL,
  `transaction_type` varchar(30) NOT NULL,
  `amount` decimal(19,2) NOT NULL,
  `currency` varchar(3) NOT NULL,
  `description` varchar(1000) NOT NULL,
  `source_type` varchar(40) DEFAULT NULL,
  `source_id` bigint DEFAULT NULL,
  `contributor_user_id` bigint DEFAULT NULL,
  `beneficiary_user_id` bigint DEFAULT NULL,
  `beneficiary_name` varchar(200) DEFAULT NULL,
  `external_reference` varchar(120) DEFAULT NULL,
  `occurred_at` datetime(6) NOT NULL,
  `active` bit(1) NOT NULL DEFAULT b'1',
  `created_by` bigint DEFAULT NULL,
  `created_on` datetime(6) DEFAULT NULL,
  `last_modified_date` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fund_transaction_uuid` (`uuid`),
  UNIQUE KEY `uk_fund_transaction_event` (`event_key`),
  UNIQUE KEY `idx_fund_transaction_event` (`event_key`),
  KEY `idx_fund_transaction_fund` (`fund_id`,`occurred_at`),
  CONSTRAINT `fk_fund_transaction_fund` FOREIGN KEY (`fund_id`) REFERENCES `pms_community_fund` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_config` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_on` datetime(6) DEFAULT NULL,
  `int_value` int NOT NULL,
  `name` varchar(255) DEFAULT NULL,
  `string_value` longblob,
  `encrypted` bit(1) NOT NULL,
  `updated_on` datetime(6) DEFAULT NULL,
  `uuid` binary(16) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_conversion_rates` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_on` datetime(6) DEFAULT NULL,
  `uuid` binary(16) NOT NULL,
  `currency` varchar(255) DEFAULT NULL,
  `last_modified_date` datetime(6) DEFAULT NULL,
  `rate` decimal(38,2) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKe1pynkrryylxvj64b6f5a5t1a` (`uuid`),
  KEY `idx_conversion_rate_currency` (`currency`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_customer_workspace` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `uuid` binary(16) NOT NULL,
  `created_on` datetime(6) DEFAULT NULL,
  `active` bit(1) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `last_modified_date` datetime(6) DEFAULT NULL,
  `owner_user_id` bigint NOT NULL,
  `business_area` varchar(40) NOT NULL,
  `name` varchar(160) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_customer_workspace_uuid` (`uuid`),
  UNIQUE KEY `uk_customer_workspace_owner_area` (`owner_user_id`,`business_area`),
  UNIQUE KEY `uk_workspace_owner_area` (`owner_user_id`,`business_area`),
  KEY `idx_customer_workspace_owner` (`owner_user_id`,`active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_document_branding` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `uuid` binary(16) NOT NULL,
  `created_on` datetime(6) DEFAULT NULL,
  `active` bit(1) NOT NULL DEFAULT b'1',
  `created_by` bigint NOT NULL,
  `last_modified_date` datetime(6) DEFAULT NULL,
  `owner_user_id` bigint NOT NULL,
  `logo_mime_type` varchar(40) NOT NULL,
  `logo_sha256` varchar(64) NOT NULL,
  `logo_content` mediumblob NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_document_branding_uuid` (`uuid`),
  UNIQUE KEY `uk_document_branding_owner` (`owner_user_id`),
  CONSTRAINT `fk_document_branding_owner` FOREIGN KEY (`owner_user_id`) REFERENCES `pms_users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_domain_event_outbox` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `uuid` binary(16) NOT NULL,
  `created_on` datetime(6) DEFAULT NULL,
  `active` bit(1) NOT NULL,
  `event_id` varchar(36) NOT NULL,
  `dedupe_key` varchar(180) NOT NULL,
  `event_type` varchar(80) NOT NULL,
  `aggregate_type` varchar(80) NOT NULL,
  `aggregate_id` varchar(120) NOT NULL,
  `payload` longtext NOT NULL,
  `status` varchar(20) NOT NULL,
  `attempts` int NOT NULL,
  `next_attempt_at` datetime(6) NOT NULL,
  `processing_started_at` datetime(6) DEFAULT NULL,
  `processed_at` datetime(6) DEFAULT NULL,
  `correlation_id` varchar(36) DEFAULT NULL,
  `last_error` varchar(1000) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_outbox_uuid` (`uuid`),
  UNIQUE KEY `uk_outbox_event_id` (`event_id`),
  UNIQUE KEY `uk_outbox_dedupe_key` (`dedupe_key`),
  KEY `idx_outbox_dispatch` (`status`,`next_attempt_at`),
  KEY `idx_outbox_aggregate` (`aggregate_type`,`aggregate_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_estate_budget` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `uuid` binary(16) NOT NULL,
  `created_on` datetime(6) DEFAULT NULL,
  `active` bit(1) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `last_modified_date` datetime(6) DEFAULT NULL,
  `property_id` bigint NOT NULL,
  `budget_year` int NOT NULL,
  `name` varchar(255) DEFAULT NULL,
  `currency` varchar(255) DEFAULT NULL,
  `status` varchar(255) DEFAULT NULL,
  `approved_at` datetime(6) DEFAULT NULL,
  `approved_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_estate_budget_uuid` (`uuid`),
  UNIQUE KEY `uk_estate_budget_property_year_name` (`property_id`,`budget_year`,`name`),
  KEY `idx_estate_budget_property_year_active` (`property_id`,`budget_year`,`active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_estate_budget_line` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `uuid` binary(16) NOT NULL,
  `created_on` datetime(6) DEFAULT NULL,
  `active` bit(1) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `last_modified_date` datetime(6) DEFAULT NULL,
  `budget_id` bigint NOT NULL,
  `category` varchar(255) DEFAULT NULL,
  `description` varchar(255) DEFAULT NULL,
  `planned_amount` decimal(38,2) DEFAULT NULL,
  `actual_amount` decimal(38,2) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_estate_budget_line_uuid` (`uuid`),
  KEY `idx_estate_budget_line_budget` (`budget_id`,`active`),
  CONSTRAINT `fk_estate_budget_line_budget` FOREIGN KEY (`budget_id`) REFERENCES `pms_estate_budget` (`id`),
  CONSTRAINT `chk_estate_budget_line_amounts` CHECK (((`planned_amount` >= 0) and (`actual_amount` >= 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_estate_meeting` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `uuid` binary(16) NOT NULL,
  `created_on` datetime(6) DEFAULT NULL,
  `active` bit(1) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `last_modified_date` datetime(6) DEFAULT NULL,
  `property_id` bigint NOT NULL,
  `title` varchar(255) DEFAULT NULL,
  `scheduled_at` datetime(6) NOT NULL,
  `venue` varchar(255) DEFAULT NULL,
  `status` varchar(255) DEFAULT NULL,
  `quorum_required` int NOT NULL DEFAULT '0',
  `attendee_count` int NOT NULL DEFAULT '0',
  `minutes` varchar(5000) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_estate_meeting_uuid` (`uuid`),
  KEY `idx_estate_meeting_property` (`property_id`,`scheduled_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_estate_resolution` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `uuid` binary(16) NOT NULL,
  `created_on` datetime(6) DEFAULT NULL,
  `active` bit(1) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `last_modified_date` datetime(6) DEFAULT NULL,
  `meeting_id` bigint NOT NULL,
  `title` varchar(255) DEFAULT NULL,
  `decision` varchar(3000) DEFAULT NULL,
  `status` varchar(255) DEFAULT NULL,
  `votes_for` int NOT NULL DEFAULT '0',
  `votes_against` int NOT NULL DEFAULT '0',
  `votes_abstain` int NOT NULL DEFAULT '0',
  `due_date` date DEFAULT NULL,
  `implemented_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_estate_resolution_uuid` (`uuid`),
  KEY `idx_estate_resolution_meeting` (`meeting_id`,`status`),
  CONSTRAINT `fk_estate_resolution_meeting` FOREIGN KEY (`meeting_id`) REFERENCES `pms_estate_meeting` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_estate_service_charge` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `uuid` binary(16) NOT NULL,
  `created_on` datetime(6) DEFAULT NULL,
  `active` bit(1) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `last_modified_date` datetime(6) DEFAULT NULL,
  `property_id` bigint NOT NULL,
  `unit_id` bigint NOT NULL,
  `homeowner_user_id` bigint NOT NULL,
  `invoice_id` bigint NOT NULL,
  `amount` decimal(19,2) NOT NULL,
  `currency` varchar(255) DEFAULT NULL,
  `due_date` date NOT NULL,
  `description` varchar(255) DEFAULT NULL,
  `pre_due_reminder_queued_at` datetime(6) DEFAULT NULL,
  `overdue_notice_queued_at` datetime(6) DEFAULT NULL,
  `last_overdue_reminder_queued_at` datetime(6) DEFAULT NULL,
  `overdue_reminder_count` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_service_charge_uuid` (`uuid`),
  KEY `idx_charge_homeowner` (`homeowner_user_id`,`active`),
  KEY `idx_charge_property` (`property_id`,`active`),
  KEY `fk_service_charge_invoice` (`invoice_id`),
  KEY `idx_charge_reminder_scan` (`active`,`due_date`,`pre_due_reminder_queued_at`,`overdue_notice_queued_at`),
  KEY `idx_charge_overdue_scan` (`active`,`due_date`,`overdue_reminder_count`,`last_overdue_reminder_queued_at`),
  CONSTRAINT `fk_service_charge_invoice` FOREIGN KEY (`invoice_id`) REFERENCES `pms_invoice` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_estate_work_order` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `uuid` binary(16) NOT NULL,
  `created_on` datetime(6) DEFAULT NULL,
  `active` bit(1) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `last_modified_date` datetime(6) DEFAULT NULL,
  `work_order_number` varchar(255) DEFAULT NULL,
  `property_id` bigint NOT NULL,
  `area_name` varchar(255) DEFAULT NULL,
  `title` varchar(255) DEFAULT NULL,
  `description` varchar(2000) NOT NULL,
  `category` varchar(255) DEFAULT NULL,
  `priority` varchar(255) DEFAULT NULL,
  `status` varchar(255) DEFAULT NULL,
  `assigned_provider_service_id` bigint DEFAULT NULL,
  `scheduled_at` datetime(6) DEFAULT NULL,
  `completed_at` datetime(6) DEFAULT NULL,
  `estimated_cost` decimal(38,2) DEFAULT NULL,
  `actual_cost` decimal(38,2) DEFAULT NULL,
  `currency` varchar(255) DEFAULT NULL,
  `resolution_notes` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_estate_work_order_uuid` (`uuid`),
  UNIQUE KEY `uk_estate_work_order_number` (`work_order_number`),
  KEY `idx_estate_work_order_property` (`property_id`,`status`,`active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_events` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_on` datetime(6) DEFAULT NULL,
  `event` longblob,
  `event_type` varchar(255) DEFAULT NULL,
  `http_status_code` int NOT NULL,
  `t_id` bigint DEFAULT NULL,
  `uuid` binary(16) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_event_type` (`event_type`),
  KEY `idx_event_type_date` (`event_type`,`created_on`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_financial_journal` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `uuid` binary(16) NOT NULL,
  `created_on` datetime(6) DEFAULT NULL,
  `event_key` varchar(190) NOT NULL,
  `event_type` varchar(50) NOT NULL,
  `source_type` varchar(50) NOT NULL,
  `source_id` varchar(120) NOT NULL,
  `provider_reference` varchar(120) DEFAULT NULL,
  `occurred_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_financial_journal_uuid` (`uuid`),
  UNIQUE KEY `uk_financial_journal_event_key` (`event_key`),
  KEY `idx_financial_journal_source` (`source_type`,`source_id`),
  KEY `idx_financial_journal_occurred` (`occurred_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_financial_ledger_line` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `uuid` binary(16) NOT NULL,
  `created_on` datetime(6) DEFAULT NULL,
  `journal_id` bigint NOT NULL,
  `line_number` int NOT NULL,
  `account_code` varchar(50) NOT NULL,
  `user_id` bigint DEFAULT NULL,
  `property_id` bigint DEFAULT NULL,
  `unit_id` bigint DEFAULT NULL,
  `currency` varchar(12) NOT NULL,
  `debit` decimal(19,2) NOT NULL DEFAULT '0.00',
  `credit` decimal(19,2) NOT NULL DEFAULT '0.00',
  `description` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_financial_ledger_line_uuid` (`uuid`),
  UNIQUE KEY `uk_financial_ledger_line_number` (`journal_id`,`line_number`),
  KEY `idx_financial_ledger_user_date` (`user_id`,`created_on`),
  KEY `idx_financial_ledger_property_date` (`property_id`,`created_on`),
  CONSTRAINT `fk_financial_ledger_journal` FOREIGN KEY (`journal_id`) REFERENCES `pms_financial_journal` (`id`),
  CONSTRAINT `chk_financial_ledger_side` CHECK ((((`debit` > 0) and (`credit` = 0)) or ((`credit` > 0) and (`debit` = 0))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_gate_device` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `uuid` binary(16) NOT NULL,
  `created_on` datetime(6) DEFAULT NULL,
  `active` bit(1) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `last_modified_date` datetime(6) DEFAULT NULL,
  `device_code` varchar(255) DEFAULT NULL,
  `property_id` bigint NOT NULL,
  `display_name` varchar(255) DEFAULT NULL,
  `gate_name` varchar(255) DEFAULT NULL,
  `lane_name` varchar(255) DEFAULT NULL,
  `public_key` varchar(255) DEFAULT NULL,
  `enabled` bit(1) NOT NULL,
  `last_seen_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_gate_device_uuid` (`uuid`),
  UNIQUE KEY `uk_gate_device_code` (`device_code`),
  UNIQUE KEY `idx_gate_device_code` (`device_code`),
  KEY `idx_gate_device_property` (`property_id`,`active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_gate_request_nonce` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `uuid` binary(16) NOT NULL,
  `created_on` datetime(6) DEFAULT NULL,
  `device_id` bigint NOT NULL,
  `nonce` varchar(255) DEFAULT NULL,
  `expires_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_gate_nonce_uuid` (`uuid`),
  UNIQUE KEY `uk_gate_nonce_device` (`device_id`,`nonce`),
  KEY `idx_gate_nonce_expiry` (`expires_at`),
  CONSTRAINT `fk_gate_nonce_device` FOREIGN KEY (`device_id`) REFERENCES `pms_gate_device` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_help_article` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `uuid` binary(16) NOT NULL,
  `created_on` datetime(6) DEFAULT NULL,
  `active` bit(1) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `last_modified_date` datetime(6) DEFAULT NULL,
  `slug` varchar(255) DEFAULT NULL,
  `title` varchar(255) DEFAULT NULL,
  `category` varchar(255) DEFAULT NULL,
  `body` text NOT NULL,
  `keywords` varchar(255) DEFAULT NULL,
  `audience_roles` varchar(255) DEFAULT NULL,
  `published` bit(1) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_help_article_uuid` (`uuid`),
  UNIQUE KEY `uk_help_article_slug` (`slug`),
  KEY `idx_help_article_category` (`category`,`published`,`active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_help_conversation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `uuid` binary(16) NOT NULL,
  `created_on` datetime(6) DEFAULT NULL,
  `active` bit(1) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `last_modified_date` datetime(6) DEFAULT NULL,
  `user_id` bigint NOT NULL,
  `active_role` varchar(255) DEFAULT NULL,
  `subject` varchar(255) DEFAULT NULL,
  `status` varchar(255) DEFAULT NULL,
  `priority` varchar(255) DEFAULT NULL,
  `assigned_to_user_id` bigint DEFAULT NULL,
  `last_message_at` datetime(6) DEFAULT NULL,
  `escalated_at` datetime(6) DEFAULT NULL,
  `resolved_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_help_conversation_uuid` (`uuid`),
  KEY `idx_help_conversation_user` (`user_id`,`last_message_at`),
  KEY `idx_help_conversation_queue` (`status`,`priority`,`last_message_at`),
  KEY `fk_help_conversation_assignee` (`assigned_to_user_id`),
  CONSTRAINT `fk_help_conversation_assignee` FOREIGN KEY (`assigned_to_user_id`) REFERENCES `pms_users` (`id`),
  CONSTRAINT `fk_help_conversation_user` FOREIGN KEY (`user_id`) REFERENCES `pms_users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_help_message` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `uuid` binary(16) NOT NULL,
  `created_on` datetime(6) DEFAULT NULL,
  `active` bit(1) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `last_modified_date` datetime(6) DEFAULT NULL,
  `conversation_id` bigint NOT NULL,
  `sender_type` varchar(255) DEFAULT NULL,
  `content` text NOT NULL,
  `model` varchar(255) DEFAULT NULL,
  `provider_response_id` varchar(255) DEFAULT NULL,
  `source_article_ids` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_help_message_uuid` (`uuid`),
  KEY `idx_help_message_conversation` (`conversation_id`,`created_on`),
  CONSTRAINT `fk_help_message_conversation` FOREIGN KEY (`conversation_id`) REFERENCES `pms_help_conversation` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_insurance_company` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `uuid` binary(16) NOT NULL,
  `created_on` datetime(6) DEFAULT NULL,
  `active` bit(1) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `last_modified_date` datetime(6) DEFAULT NULL,
  `code` varchar(50) NOT NULL,
  `name` varchar(160) NOT NULL,
  `logo_url` varchar(800) DEFAULT NULL,
  `description` varchar(1000) DEFAULT NULL,
  `quotation_email` varchar(254) DEFAULT NULL,
  `claims_email` varchar(254) DEFAULT NULL,
  `renewals_email` varchar(254) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_insurance_company_uuid` (`uuid`),
  UNIQUE KEY `uk_insurance_company_code` (`code`),
  KEY `idx_insurance_company_active_name` (`active`,`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_insurance_email_exchange` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `uuid` binary(16) NOT NULL,
  `created_on` datetime(6) DEFAULT NULL,
  `active` bit(1) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `last_modified_date` datetime(6) DEFAULT NULL,
  `company_id` bigint NOT NULL,
  `case_reference` varchar(80) NOT NULL,
  `correlation_id` varchar(36) NOT NULL,
  `message_type` varchar(30) NOT NULL,
  `direction` varchar(12) NOT NULL,
  `status` varchar(24) NOT NULL,
  `sender_address` varchar(254) NOT NULL,
  `recipient_address` varchar(254) NOT NULL,
  `subject` varchar(400) NOT NULL,
  `encrypted_body` longblob NOT NULL,
  `body_hash` varchar(64) NOT NULL,
  `external_message_id` varchar(500) DEFAULT NULL,
  `in_reply_to` varchar(500) DEFAULT NULL,
  `sent_at` datetime(6) DEFAULT NULL,
  `received_at` datetime(6) DEFAULT NULL,
  `last_error` varchar(1000) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_insurance_email_uuid` (`uuid`),
  UNIQUE KEY `uk_insurance_email_correlation` (`correlation_id`),
  KEY `idx_insurance_email_case` (`case_reference`,`created_on`),
  KEY `idx_insurance_email_status` (`status`,`created_on`),
  KEY `fk_insurance_email_company` (`company_id`),
  KEY `idx_insurance_email_correlation` (`correlation_id`),
  CONSTRAINT `fk_insurance_email_company` FOREIGN KEY (`company_id`) REFERENCES `pms_insurance_company` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_insurance_payment_configuration` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `uuid` binary(16) NOT NULL,
  `created_on` datetime(6) DEFAULT NULL,
  `active` bit(1) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `last_modified_date` datetime(6) DEFAULT NULL,
  `company_id` bigint NOT NULL,
  `payment_account_id` bigint NOT NULL,
  `payment_channel` varchar(30) NOT NULL,
  `label` varchar(120) NOT NULL,
  `instructions` varchar(1500) NOT NULL,
  `reference_template` varchar(240) DEFAULT NULL,
  `version` int NOT NULL,
  `effective_from` date NOT NULL,
  `effective_to` date DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_insurance_payment_uuid` (`uuid`),
  UNIQUE KEY `uk_insurance_payment_version` (`company_id`,`payment_channel`,`version`),
  KEY `idx_insurance_payment_company` (`company_id`,`active`),
  KEY `idx_insurance_payment_account` (`payment_account_id`),
  CONSTRAINT `fk_insurance_payment_account` FOREIGN KEY (`payment_account_id`) REFERENCES `pms_payment_account` (`id`),
  CONSTRAINT `fk_insurance_payment_company` FOREIGN KEY (`company_id`) REFERENCES `pms_insurance_company` (`id`),
  CONSTRAINT `chk_insurance_payment_dates` CHECK (((`effective_to` is null) or (`effective_to` >= `effective_from`))),
  CONSTRAINT `chk_insurance_payment_version` CHECK ((`version` > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_invite` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_on` datetime(6) DEFAULT NULL,
  `active` bit(1) NOT NULL,
  `created_by` bigint NOT NULL,
  `last_modified_date` datetime(6) DEFAULT NULL,
  `expiry_date` datetime(6) DEFAULT NULL,
  `token` varchar(255) DEFAULT NULL,
  `type` varchar(255) DEFAULT NULL,
  `visits` int NOT NULL,
  `entity_id` bigint DEFAULT NULL,
  `role_id` bigint DEFAULT NULL,
  `uuid` binary(16) NOT NULL,
  `recipient` varchar(254) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_invite_token` (`token`),
  KEY `idx_invite_recipient` (`recipient`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_invoice` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_on` datetime(6) DEFAULT NULL,
  `active` bit(1) NOT NULL,
  `amount` double NOT NULL,
  `billed_user_id` bigint NOT NULL,
  `currency` varchar(255) DEFAULT NULL,
  `description` longblob,
  `paid` bit(1) NOT NULL,
  `pay_to_user_id` bigint NOT NULL,
  `pending_amount` double NOT NULL,
  `property_id` bigint NOT NULL,
  `ref` varchar(255) DEFAULT NULL,
  `unit_id` bigint NOT NULL,
  `customer_email` varchar(255) DEFAULT NULL,
  `customer_phone_number` varchar(255) DEFAULT NULL,
  `transaction_in_progress` bit(1) NOT NULL,
  `uuid` binary(16) NOT NULL,
  `html_description` longblob,
  `subscription_plan_code` varchar(255) DEFAULT NULL,
  `billing_type` varchar(255) DEFAULT NULL,
  `due_date` date DEFAULT NULL,
  `payment_account_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_invoice_unit_id` (`unit_id`),
  KEY `idx_invoice_ref` (`ref`),
  KEY `idx_invoice_billed_user_id` (`billed_user_id`),
  KEY `idx_invoice_property_id` (`property_id`),
  KEY `idx_invoice_pay_to_user_id` (`pay_to_user_id`),
  KEY `idx_filter` (`ref`,`billed_user_id`,`pay_to_user_id`,`property_id`),
  KEY `idx_invoice_billing_type` (`billing_type`),
  KEY `idx_invoice_payment_account` (`payment_account_id`),
  CONSTRAINT `fk_invoice_payment_account` FOREIGN KEY (`payment_account_id`) REFERENCES `pms_payment_account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_key` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_on` datetime(6) DEFAULT NULL,
  `active` bit(1) NOT NULL,
  `created_by` bigint NOT NULL,
  `last_modified_date` datetime(6) DEFAULT NULL,
  `value` longblob,
  `uuid` binary(16) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKrsdv58mieas4y78llfp3elbn0` (`uuid`),
  KEY `idx_key_active` (`active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_kyc_case` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `uuid` binary(16) NOT NULL,
  `created_on` datetime(6) DEFAULT NULL,
  `active` bit(1) NOT NULL,
  `user_id` bigint NOT NULL,
  `status` varchar(255) DEFAULT NULL,
  `consent_version` varchar(255) DEFAULT NULL,
  `consent_at` datetime(6) NOT NULL,
  `phone_verified` bit(1) NOT NULL DEFAULT b'0',
  `registry_status` varchar(255) DEFAULT NULL,
  `submitted_at` datetime(6) DEFAULT NULL,
  `reviewed_at` datetime(6) DEFAULT NULL,
  `reviewed_by` bigint DEFAULT NULL,
  `review_notes` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_kyc_case_uuid` (`uuid`),
  UNIQUE KEY `idx_kyc_case_user` (`user_id`),
  KEY `fk_kyc_case_reviewer` (`reviewed_by`),
  CONSTRAINT `fk_kyc_case_reviewer` FOREIGN KEY (`reviewed_by`) REFERENCES `pms_users` (`id`),
  CONSTRAINT `fk_kyc_case_user` FOREIGN KEY (`user_id`) REFERENCES `pms_users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_kyc_document` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `uuid` binary(16) NOT NULL,
  `created_on` datetime(6) DEFAULT NULL,
  `active` bit(1) NOT NULL,
  `case_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `document_type` varchar(255) DEFAULT NULL,
  `original_file_name` varchar(255) NOT NULL,
  `content_type` varchar(255) DEFAULT NULL,
  `file_ref` varchar(255) DEFAULT NULL,
  `file_size` bigint NOT NULL,
  `sha256` varchar(255) DEFAULT NULL,
  `width` int DEFAULT NULL,
  `height` int DEFAULT NULL,
  `quality_score` double DEFAULT NULL,
  `quality_status` varchar(255) DEFAULT NULL,
  `status` varchar(255) DEFAULT NULL,
  `ocr_provider` varchar(255) DEFAULT NULL,
  `ocr_confidence` double DEFAULT NULL,
  `encrypted_extracted_data` longblob,
  `rejection_reason` varchar(255) DEFAULT NULL,
  `supersedes_document_id` bigint DEFAULT NULL,
  `reviewed_at` datetime(6) DEFAULT NULL,
  `reviewed_by` bigint DEFAULT NULL,
  `version_no` int NOT NULL DEFAULT '1',
  `issued_at` datetime(6) DEFAULT NULL,
  `expires_at` datetime(6) DEFAULT NULL,
  `reverification_due_at` datetime(6) DEFAULT NULL,
  `maintenance_reason` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_kyc_document_uuid` (`uuid`),
  KEY `idx_kyc_document_case` (`case_id`,`active`),
  KEY `idx_kyc_document_hash` (`user_id`,`sha256`),
  KEY `fk_kyc_document_reviewer` (`reviewed_by`),
  KEY `fk_kyc_document_supersedes` (`supersedes_document_id`),
  CONSTRAINT `fk_kyc_document_case` FOREIGN KEY (`case_id`) REFERENCES `pms_kyc_case` (`id`),
  CONSTRAINT `fk_kyc_document_reviewer` FOREIGN KEY (`reviewed_by`) REFERENCES `pms_users` (`id`),
  CONSTRAINT `fk_kyc_document_supersedes` FOREIGN KEY (`supersedes_document_id`) REFERENCES `pms_kyc_document` (`id`),
  CONSTRAINT `fk_kyc_document_user` FOREIGN KEY (`user_id`) REFERENCES `pms_users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_late_fee_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `uuid` binary(16) NOT NULL,
  `created_on` datetime(6) DEFAULT NULL,
  `active` bit(1) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `last_modified_date` datetime(6) DEFAULT NULL,
  `lease_id` bigint NOT NULL,
  `flat_amount` decimal(19,2) NOT NULL DEFAULT '0.00',
  `percentage_rate` decimal(8,4) NOT NULL DEFAULT '0.0000',
  `grace_days` int NOT NULL DEFAULT '0',
  `maximum_amount` decimal(19,2) DEFAULT NULL,
  `enabled` bit(1) NOT NULL DEFAULT b'1',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_late_fee_rule_uuid` (`uuid`),
  UNIQUE KEY `uk_late_fee_rule_lease` (`lease_id`),
  CONSTRAINT `fk_late_fee_rule_lease` FOREIGN KEY (`lease_id`) REFERENCES `pms_lease` (`id`),
  CONSTRAINT `chk_late_fee_rule_values` CHECK (((`flat_amount` >= 0) and (`percentage_rate` >= 0) and (`grace_days` >= 0) and ((`maximum_amount` is null) or (`maximum_amount` > 0))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_lease` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_on` datetime(6) DEFAULT NULL,
  `active` bit(1) NOT NULL,
  `created_by` bigint NOT NULL,
  `last_modified_date` datetime(6) DEFAULT NULL,
  `tenant_id` bigint NOT NULL,
  `uuid` binary(16) NOT NULL,
  `charges` bit(1) NOT NULL,
  `currency` varchar(255) DEFAULT NULL,
  `deposit_return_days` int DEFAULT NULL,
  `entry_notice_days` int DEFAULT NULL,
  `lease_date` date DEFAULT NULL,
  `lease_duration_in_months` int DEFAULT NULL,
  `lease_mode` varchar(255) DEFAULT NULL,
  `move_in_date` date DEFAULT NULL,
  `move_out_date` date DEFAULT NULL,
  `name` varchar(255) DEFAULT NULL,
  `notice_period_in_months` int DEFAULT NULL,
  `pets_policy` longblob,
  `price` double NOT NULL,
  `rent_due_day_of_month` int DEFAULT NULL,
  `repair_threshold` double DEFAULT NULL,
  `self_renew` bit(1) NOT NULL,
  `signed` bit(1) NOT NULL,
  `manager_signed_date` datetime(6) DEFAULT NULL,
  `signed_by_manager_id` bigint DEFAULT NULL,
  `tenant_signed_date` datetime(6) DEFAULT NULL,
  `next_payment_date` date DEFAULT NULL,
  `payment_due` bit(1) NOT NULL,
  `lifecycle_status` varchar(255) DEFAULT NULL,
  `termination_effective_date` date DEFAULT NULL,
  `termination_reason` varchar(255) DEFAULT NULL,
  `termination_requested_by` bigint DEFAULT NULL,
  `termination_requested_at` datetime(6) DEFAULT NULL,
  `governed_document_required` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK5yn5rc5e14ctu2ais7y7nfwds` (`uuid`),
  KEY `idx_lease_tenantId` (`tenant_id`),
  KEY `idx_lease_active` (`active`),
  KEY `idx_lease_signedByManagerId` (`signed_by_manager_id`),
  KEY `idx_lease_termination_scan` (`active`,`lifecycle_status`,`termination_effective_date`),
  KEY `idx_lease_expiry_scan` (`active`,`signed`,`lifecycle_status`,`move_out_date`),
  KEY `idx_lease_payment_scan` (`active`,`payment_due`,`next_payment_date`),
  CONSTRAINT `FK1m5vwb7ypoo3i35jxj0m8aiik` FOREIGN KEY (`tenant_id`) REFERENCES `pms_unit_tenant` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_lease_charge` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_on` datetime(6) DEFAULT NULL,
  `active` bit(1) NOT NULL,
  `amount` double NOT NULL,
  `lease_id` bigint NOT NULL,
  `period` varchar(255) DEFAULT NULL,
  `uuid` binary(16) NOT NULL,
  `charge_id` bigint NOT NULL,
  `next_payment_date` date DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKshce5j5w2uhwrxvvn3bqqdly3` (`uuid`),
  KEY `idx_lease_charge_leaseId` (`lease_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_lease_document` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `uuid` binary(16) NOT NULL,
  `created_on` datetime(6) DEFAULT NULL,
  `active` bit(1) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `last_modified_date` datetime(6) DEFAULT NULL,
  `lease_id` bigint DEFAULT NULL,
  `sale_id` bigint DEFAULT NULL,
  `property_id` bigint DEFAULT NULL,
  `unit_id` bigint DEFAULT NULL,
  `template_id` bigint NOT NULL,
  `template_version` int NOT NULL,
  `document_type` varchar(60) NOT NULL,
  `status` varchar(30) NOT NULL,
  `name` varchar(255) NOT NULL,
  `rendered_html` longtext NOT NULL,
  `issuer_user_id` bigint NOT NULL,
  `recipient_user_id` bigint NOT NULL,
  `effective_date` date DEFAULT NULL,
  `response_due_date` date DEFAULT NULL,
  `amount` decimal(38,2) DEFAULT NULL,
  `currency` varchar(255) DEFAULT NULL,
  `reason` varchar(1000) DEFAULT NULL,
  `delivery_channel` varchar(255) DEFAULT NULL,
  `legal_review_required` bit(1) NOT NULL,
  `issued_at` datetime(6) DEFAULT NULL,
  `acknowledged_at` datetime(6) DEFAULT NULL,
  `issuer_signed_at` datetime(6) DEFAULT NULL,
  `recipient_signed_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_lease_document_uuid` (`uuid`),
  KEY `idx_document_lease` (`lease_id`,`active`),
  KEY `idx_document_property` (`property_id`,`active`),
  KEY `idx_document_parties` (`issuer_user_id`,`recipient_user_id`,`active`),
  KEY `fk_document_template` (`template_id`),
  KEY `idx_document_lease_type_status` (`lease_id`,`document_type`,`status`,`active`),
  KEY `idx_document_sale` (`sale_id`,`document_type`,`status`,`active`),
  CONSTRAINT `fk_document_sale` FOREIGN KEY (`sale_id`) REFERENCES `pms_sale_transaction` (`id`),
  CONSTRAINT `fk_document_template` FOREIGN KEY (`template_id`) REFERENCES `pms_lease_document_template` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_lease_document_template` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `uuid` binary(16) NOT NULL,
  `created_on` datetime(6) DEFAULT NULL,
  `active` bit(1) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `last_modified_date` datetime(6) DEFAULT NULL,
  `document_type` varchar(60) NOT NULL,
  `display_name` varchar(255) NOT NULL,
  `version` int NOT NULL,
  `body_html` mediumtext NOT NULL,
  `content_sha256` varchar(64) DEFAULT NULL,
  `legal_review_required` bit(1) NOT NULL,
  `legal_reviewed_at` datetime(6) DEFAULT NULL,
  `legal_reviewed_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_document_template_uuid` (`uuid`),
  KEY `idx_document_template_type_active` (`document_type`,`active`),
  KEY `idx_document_template_reviewed_by` (`legal_reviewed_by`),
  CONSTRAINT `fk_document_template_reviewed_by` FOREIGN KEY (`legal_reviewed_by`) REFERENCES `pms_users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_lease_financial_event` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `uuid` binary(16) NOT NULL,
  `created_on` datetime(6) DEFAULT NULL,
  `idempotency_key` varchar(190) NOT NULL,
  `lease_id` bigint NOT NULL,
  `invoice_id` bigint DEFAULT NULL,
  `event_type` varchar(40) NOT NULL,
  `amount` decimal(19,2) NOT NULL,
  `currency` varchar(12) NOT NULL,
  `external_reference` varchar(120) DEFAULT NULL,
  `reason` varchar(1000) DEFAULT NULL,
  `occurred_at` datetime(6) NOT NULL,
  `initiated_by` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_lease_fin_event_uuid` (`uuid`),
  UNIQUE KEY `uk_lease_fin_event_idempotency` (`idempotency_key`),
  KEY `idx_lease_fin_event_lease` (`lease_id`,`occurred_at`),
  KEY `idx_lease_fin_event_invoice` (`invoice_id`),
  CONSTRAINT `fk_lease_fin_event_invoice` FOREIGN KEY (`invoice_id`) REFERENCES `pms_invoice` (`id`),
  CONSTRAINT `fk_lease_fin_event_lease` FOREIGN KEY (`lease_id`) REFERENCES `pms_lease` (`id`),
  CONSTRAINT `chk_lease_fin_event_amount` CHECK ((`amount` > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_lease_invite` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_on` datetime(6) DEFAULT NULL,
  `active` bit(1) NOT NULL,
  `created_by` bigint NOT NULL,
  `last_modified_date` datetime(6) DEFAULT NULL,
  `expiry_date` datetime(6) DEFAULT NULL,
  `token` varchar(255) DEFAULT NULL,
  `unit_id` bigint NOT NULL,
  `visits` int NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_lease_message` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_on` datetime(6) DEFAULT NULL,
  `uuid` binary(16) NOT NULL,
  `active` bit(1) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `last_modified_date` datetime(6) DEFAULT NULL,
  `lease_id` bigint NOT NULL,
  `message` longblob,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK17epallqs4dtcodxqt2q0upyl` (`uuid`),
  KEY `idx_lease_message_leaseId` (`lease_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_lease_template` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_on` datetime(6) DEFAULT NULL,
  `uuid` binary(16) NOT NULL,
  `active` bit(1) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `last_modified_date` datetime(6) DEFAULT NULL,
  `deposit_return_days` int DEFAULT NULL,
  `entry_notice_days` int DEFAULT NULL,
  `lease_duration_in_months` int DEFAULT NULL,
  `lease_mode` varchar(255) DEFAULT NULL,
  `name` varchar(255) DEFAULT NULL,
  `notice_period_in_months` int DEFAULT NULL,
  `pets_policy` longblob,
  `rent_due_day_of_month` int DEFAULT NULL,
  `repair_threshold` double DEFAULT NULL,
  `self_renew` bit(1) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKd2xdxfsa23drj8cfcjget5e46` (`uuid`),
  KEY `idx_lease_template_userid` (`created_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_login_per_ip_logs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `attempts_count` int NOT NULL,
  `blocked` bit(1) NOT NULL,
  `city` varchar(255) DEFAULT NULL,
  `country` varchar(255) DEFAULT NULL,
  `ipaddress` varchar(255) DEFAULT NULL,
  `last_login_attempt` datetime(6) DEFAULT NULL,
  `username` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `IDXt6e4fsyoodetoe7mhfmv5d060` (`ipaddress`,`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_maintenance_work_order` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `uuid` binary(16) NOT NULL,
  `created_on` datetime(6) DEFAULT NULL,
  `active` bit(1) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `last_modified_date` datetime(6) DEFAULT NULL,
  `work_order_number` varchar(40) NOT NULL,
  `property_id` bigint NOT NULL,
  `unit_id` bigint NOT NULL,
  `requested_by_user_id` bigint NOT NULL,
  `assigned_provider_service_id` bigint DEFAULT NULL,
  `title` varchar(120) NOT NULL,
  `description` varchar(2000) NOT NULL,
  `category` varchar(40) NOT NULL,
  `priority` varchar(20) NOT NULL,
  `status` varchar(30) NOT NULL,
  `scheduled_at` datetime(6) DEFAULT NULL,
  `completed_at` datetime(6) DEFAULT NULL,
  `estimated_cost` decimal(19,2) DEFAULT NULL,
  `actual_cost` decimal(19,2) DEFAULT NULL,
  `currency` varchar(255) DEFAULT NULL,
  `resolution_notes` varchar(1000) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_maintenance_uuid` (`uuid`),
  UNIQUE KEY `uk_maintenance_number` (`work_order_number`),
  KEY `idx_maintenance_unit_status` (`unit_id`,`status`,`active`),
  KEY `idx_maintenance_property_status` (`property_id`,`status`,`active`),
  KEY `idx_maintenance_requester` (`requested_by_user_id`,`created_on`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_notification` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_on` datetime(6) DEFAULT NULL,
  `active` bit(1) NOT NULL,
  `channel` varchar(255) DEFAULT NULL,
  `delivered` bit(1) NOT NULL,
  `message` varbinary(255) DEFAULT NULL,
  `recipient` varchar(255) DEFAULT NULL,
  `retries` int NOT NULL,
  `retry` bit(1) NOT NULL,
  `type` varchar(255) DEFAULT NULL,
  `updated_on` datetime(6) DEFAULT NULL,
  `uuid` binary(16) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_notification_recipient` (`recipient`),
  KEY `idx_notification_channel` (`channel`),
  KEY `idx_notification_delivered` (`delivered`),
  KEY `idx_notification_retry_scan` (`active`,`delivered`,`retry`,`channel`,`updated_on`,`retries`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_params` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_on` datetime(6) DEFAULT NULL,
  `active` bit(1) NOT NULL,
  `created_by` bigint NOT NULL,
  `name` varchar(255) DEFAULT NULL,
  `type` varchar(255) DEFAULT NULL,
  `value` longblob,
  `verified` bit(1) NOT NULL,
  `last_modified_date` datetime(6) DEFAULT NULL,
  `encrypted` bit(1) NOT NULL,
  `updated_on` datetime(6) DEFAULT NULL,
  `uuid` binary(16) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_param_created_by` (`created_by`,`active`),
  KEY `idx_param_name` (`name`),
  CONSTRAINT `FKdydqlte373uc08jrf0yexu0li` FOREIGN KEY (`created_by`) REFERENCES `pms_users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_payment` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_on` datetime(6) DEFAULT NULL,
  `amount` double DEFAULT NULL,
  `bill_reference` varchar(255) DEFAULT NULL,
  `category` varchar(255) DEFAULT NULL,
  `channel` varchar(255) DEFAULT NULL,
  `customer_account_number` varchar(255) DEFAULT NULL,
  `customer_name` varchar(255) DEFAULT NULL,
  `flag` bit(1) NOT NULL,
  `pay_to_user_id` bigint NOT NULL,
  `receiving_account_number` varchar(255) DEFAULT NULL,
  `source_ip` varchar(255) DEFAULT NULL,
  `status` varchar(255) DEFAULT NULL,
  `status_desc` varchar(255) DEFAULT NULL,
  `third_party_trans_id` varchar(255) DEFAULT NULL,
  `updated_on` datetime(6) DEFAULT NULL,
  `verification_retries` int NOT NULL,
  `uuid` binary(16) NOT NULL,
  `in_progress` bit(1) NOT NULL,
  `account_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_payment_customer_account_number` (`customer_account_number`),
  KEY `idx_payment_receiving_account_number` (`receiving_account_number`),
  KEY `idx_payment_transid` (`third_party_trans_id`),
  KEY `idx_payment_bill_reference` (`bill_reference`),
  KEY `idx_payment_category_status` (`category`,`status`),
  KEY `idx_filter` (`bill_reference`,`category`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_payment_account` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_on` datetime(6) DEFAULT NULL,
  `uuid` binary(16) NOT NULL,
  `active` bit(1) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `last_modified_date` datetime(6) DEFAULT NULL,
  `category` varchar(40) NOT NULL,
  `channel` enum('AIRTEL_MONEY','FLUTTER_WAVE','MPESA','PESA_LINK') NOT NULL,
  `name` varchar(255) NOT NULL,
  `verified` bit(1) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKbcy7i4j397trulsgoacjy8v95` (`uuid`),
  KEY `idx_payment_account_created_by` (`created_by`),
  KEY `idx_payment_account_category` (`category`),
  KEY `idx_payment_account_channel` (`channel`),
  KEY `idx_payment_account_category_active` (`category`,`active`),
  KEY `idx_payment_account_category_active_createdBy` (`category`,`active`,`created_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_payment_account_property` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_on` datetime(6) DEFAULT NULL,
  `uuid` binary(16) NOT NULL,
  `account_id` bigint NOT NULL,
  `encrypted` bit(1) NOT NULL,
  `last_modified_date` datetime(6) DEFAULT NULL,
  `property_key` varchar(255) NOT NULL,
  `value` tinyblob NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_acc_prop_account_key` (`account_id`,`property_key`),
  UNIQUE KEY `UKkhs3co5tl3bb1yd2971owilkj` (`uuid`),
  KEY `idx_acc_prop_account_id` (`account_id`),
  KEY `idx_acc_prop_key` (`account_id`,`property_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_payment_operation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `uuid` binary(16) NOT NULL,
  `created_on` datetime(6) DEFAULT NULL,
  `idempotency_key` varchar(190) NOT NULL,
  `case_reference` varchar(120) NOT NULL,
  `payment_id` bigint NOT NULL,
  `invoice_id` bigint NOT NULL,
  `operation_type` varchar(40) NOT NULL,
  `status` varchar(30) NOT NULL,
  `amount` decimal(19,2) NOT NULL,
  `currency` varchar(12) NOT NULL,
  `provider` varchar(50) DEFAULT NULL,
  `provider_reference` varchar(120) DEFAULT NULL,
  `reason` varchar(1000) DEFAULT NULL,
  `occurred_at` datetime(6) NOT NULL,
  `initiated_by` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_operation_uuid` (`uuid`),
  UNIQUE KEY `uk_payment_operation_idempotency` (`idempotency_key`),
  KEY `idx_payment_operation_case` (`case_reference`,`occurred_at`),
  KEY `idx_payment_operation_payment` (`payment_id`,`occurred_at`),
  KEY `idx_payment_operation_invoice` (`invoice_id`,`occurred_at`),
  CONSTRAINT `fk_payment_operation_invoice` FOREIGN KEY (`invoice_id`) REFERENCES `pms_invoice` (`id`),
  CONSTRAINT `fk_payment_operation_payment` FOREIGN KEY (`payment_id`) REFERENCES `pms_payment` (`id`),
  CONSTRAINT `chk_payment_operation_amount` CHECK ((`amount` > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_permission` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_on` datetime(6) DEFAULT NULL,
  `name` varchar(255) DEFAULT NULL,
  `uuid` binary(16) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_plan_feature` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_on` datetime(6) DEFAULT NULL,
  `uuid` binary(16) NOT NULL,
  `active` bit(1) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `last_modified_date` datetime(6) DEFAULT NULL,
  `enabled` bit(1) NOT NULL,
  `feature_key` varchar(255) DEFAULT NULL,
  `subscription_plan_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKgfhyoapnhuqx9wa6fxkm368q4` (`uuid`),
  KEY `idx_plan_feature_lookup` (`subscription_plan_id`,`feature_key`),
  CONSTRAINT `FKq1m2aywqds76dr3q0m0n0gki0` FOREIGN KEY (`subscription_plan_id`) REFERENCES `pms_subscription_plan` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_plan_quota` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_on` datetime(6) DEFAULT NULL,
  `uuid` binary(16) NOT NULL,
  `active` bit(1) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `last_modified_date` datetime(6) DEFAULT NULL,
  `limit_value` bigint DEFAULT NULL,
  `metric_key` varchar(255) DEFAULT NULL,
  `subscription_plan_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKsrivnij11ul1a3ihx8hnwft8p` (`uuid`),
  KEY `idx_plan_quota_lookup` (`subscription_plan_id`,`metric_key`),
  CONSTRAINT `FKds8ef6tjdl35p47kk6fa7wp27` FOREIGN KEY (`subscription_plan_id`) REFERENCES `pms_subscription_plan` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_privacy_request` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `uuid` binary(16) NOT NULL,
  `created_on` datetime(6) DEFAULT NULL,
  `active` bit(1) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `last_modified_date` datetime(6) DEFAULT NULL,
  `user_id` bigint NOT NULL,
  `request_type` varchar(255) DEFAULT NULL,
  `status` varchar(255) DEFAULT NULL,
  `reason` varchar(255) DEFAULT NULL,
  `due_at` datetime(6) NOT NULL,
  `legal_hold` bit(1) NOT NULL DEFAULT b'0',
  `retention_basis` varchar(255) DEFAULT NULL,
  `reviewer_notes` varchar(255) DEFAULT NULL,
  `result_reference` varchar(255) DEFAULT NULL,
  `reviewed_by` bigint DEFAULT NULL,
  `reviewed_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_privacy_request_uuid` (`uuid`),
  KEY `idx_privacy_request_user` (`user_id`,`status`,`active`),
  KEY `idx_privacy_request_due` (`status`,`due_at`,`active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_property` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_on` datetime(6) DEFAULT NULL,
  `active` bit(1) NOT NULL,
  `created_by` bigint NOT NULL,
  `address` varchar(255) DEFAULT NULL,
  `currency` varchar(255) DEFAULT NULL,
  `map_location` varchar(255) DEFAULT NULL,
  `name` varchar(255) DEFAULT NULL,
  `ref` varchar(255) DEFAULT NULL,
  `thumbnail` varchar(255) DEFAULT NULL,
  `type` varchar(255) DEFAULT NULL,
  `image_path` varchar(255) DEFAULT NULL,
  `has_units` bit(1) NOT NULL,
  `last_modified_date` datetime(6) DEFAULT NULL,
  `uuid` binary(16) NOT NULL,
  `management_mode` varchar(32) NOT NULL DEFAULT 'RENTAL',
  PRIMARY KEY (`id`),
  KEY `idx_created_by` (`created_by`),
  KEY `idx_image_path_created_by` (`created_by`),
  KEY `idx_has_units_and_active` (`has_units`,`active`,`created_on`),
  KEY `idx_created_by_id` (`created_by`,`id`),
  KEY `idx_property_owner_mode_active` (`created_by`,`management_mode`,`active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_property_account` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_on` datetime(6) DEFAULT NULL,
  `uuid` binary(16) NOT NULL,
  `active` bit(1) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `last_modified_date` datetime(6) DEFAULT NULL,
  `account_id` bigint NOT NULL,
  `property_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK7mvo571fuvk0vdb1jiog5fwst` (`uuid`),
  KEY `idx_property_account_unique_active` (`account_id`,`property_id`,`active`),
  KEY `idx_property_id_active` (`property_id`,`active`,`created_by`),
  KEY `idx_property_accountId` (`account_id`),
  KEY `idx_property_account_created_by` (`created_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_property_manager` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_on` datetime(6) DEFAULT NULL,
  `active` bit(1) NOT NULL,
  `invite_id` bigint NOT NULL,
  `property_id` bigint NOT NULL,
  `role_name` varchar(255) DEFAULT NULL,
  `user_id` bigint NOT NULL,
  `uuid` binary(16) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_property_manager_userId` (`user_id`),
  KEY `FKpq9bj5gqwt2rxp0uf7hew0h25` (`property_id`),
  KEY `idx_property_manager_userId_role_property` (`user_id`,`role_name`,`property_id`),
  KEY `idx_property_manager_userId_active_property` (`user_id`,`active`,`property_id`),
  KEY `idx_property_manager_userId_property` (`property_id`,`user_id`),
  KEY `idx_property_manager_property_active` (`property_id`,`active`),
  CONSTRAINT `FKpq9bj5gqwt2rxp0uf7hew0h25` FOREIGN KEY (`property_id`) REFERENCES `pms_property` (`id`),
  CONSTRAINT `FKrf19g1ogvtnq9rri1w7402qep` FOREIGN KEY (`user_id`) REFERENCES `pms_users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_property_ownership` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `uuid` binary(16) NOT NULL,
  `created_on` datetime(6) DEFAULT NULL,
  `active` bit(1) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `last_modified_date` datetime(6) DEFAULT NULL,
  `property_id` bigint NOT NULL,
  `unit_id` bigint DEFAULT NULL,
  `homeowner_user_id` bigint NOT NULL,
  `ownership_start` date NOT NULL,
  `ownership_end` date DEFAULT NULL,
  `source` varchar(255) DEFAULT NULL,
  `source_sale_transaction_id` bigint DEFAULT NULL,
  `termination_reason` varchar(500) DEFAULT NULL,
  `terminated_by` bigint DEFAULT NULL,
  `terminated_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ownership_uuid` (`uuid`),
  UNIQUE KEY `uk_ownership_source_sale` (`source_sale_transaction_id`),
  KEY `idx_ownership_homeowner` (`homeowner_user_id`,`active`),
  KEY `idx_ownership_property_unit` (`property_id`,`unit_id`,`active`),
  KEY `idx_ownership_terminated_by` (`terminated_by`,`terminated_at`),
  KEY `idx_ownership_property_active` (`property_id`,`active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_property_params` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_on` datetime(6) DEFAULT NULL,
  `active` bit(1) NOT NULL,
  `created_by` bigint NOT NULL,
  `param_id` bigint NOT NULL,
  `property_id` bigint NOT NULL,
  `last_modified_date` datetime(6) DEFAULT NULL,
  `uuid` binary(16) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKi6s772fxqt4psyb3n078ogrij` (`uuid`),
  KEY `idx_property_param_unique_active` (`param_id`,`property_id`,`active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_role` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_on` datetime(6) DEFAULT NULL,
  `active` bit(1) NOT NULL,
  `description` varchar(255) DEFAULT NULL,
  `name` varchar(255) DEFAULT NULL,
  `self_assignable` bit(1) NOT NULL,
  `uuid` binary(16) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_role_mapping` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_on` datetime(6) DEFAULT NULL,
  `permission_id` bigint NOT NULL,
  `role_id` bigint NOT NULL,
  `uuid` binary(16) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_sale_milestone` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `uuid` binary(16) NOT NULL,
  `created_on` datetime(6) DEFAULT NULL,
  `sale_id` bigint NOT NULL,
  `milestone_type` varchar(255) DEFAULT NULL,
  `status` varchar(255) DEFAULT NULL,
  `amount` decimal(38,2) DEFAULT NULL,
  `currency` varchar(255) DEFAULT NULL,
  `external_reference` varchar(255) DEFAULT NULL,
  `evidence_document_id` bigint DEFAULT NULL,
  `notes` varchar(255) DEFAULT NULL,
  `occurred_at` datetime(6) NOT NULL,
  `recorded_by` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sale_milestone_uuid` (`uuid`),
  KEY `idx_sale_milestone_sale` (`sale_id`,`milestone_type`,`status`),
  CONSTRAINT `fk_sale_milestone_sale` FOREIGN KEY (`sale_id`) REFERENCES `pms_sale_transaction` (`id`),
  CONSTRAINT `chk_sale_milestone_amount` CHECK (((`amount` is null) or (`amount` > 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_sale_transaction` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `uuid` binary(16) NOT NULL,
  `created_on` datetime(6) DEFAULT NULL,
  `active` bit(1) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `last_modified_date` datetime(6) DEFAULT NULL,
  `property_id` bigint NOT NULL,
  `unit_id` bigint DEFAULT NULL,
  `sales_agent_user_id` bigint NOT NULL,
  `buyer_user_id` bigint DEFAULT NULL,
  `invited_buyer_email` varchar(254) DEFAULT NULL,
  `status` varchar(30) NOT NULL,
  `asking_price` decimal(19,2) NOT NULL,
  `offer_amount` decimal(19,2) DEFAULT NULL,
  `currency` varchar(255) DEFAULT NULL,
  `offer_accepted_at` datetime(6) DEFAULT NULL,
  `completed_at` datetime(6) DEFAULT NULL,
  `notes` varchar(1000) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sale_uuid` (`uuid`),
  KEY `idx_sale_agent` (`sales_agent_user_id`,`active`),
  KEY `idx_sale_buyer` (`buyer_user_id`,`active`),
  KEY `idx_sale_property` (`property_id`,`unit_id`,`active`),
  KEY `idx_sale_property_active_created` (`property_id`,`active`,`created_on`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_sms` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_on` datetime(6) DEFAULT NULL,
  `active` bit(1) NOT NULL,
  `call_backip` varchar(255) DEFAULT NULL,
  `cost` double NOT NULL,
  `currency` varchar(255) DEFAULT NULL,
  `description` varchar(255) DEFAULT NULL,
  `network` varchar(255) DEFAULT NULL,
  `notification_id` bigint NOT NULL,
  `status` varchar(255) DEFAULT NULL,
  `third_party_id` varchar(255) DEFAULT NULL,
  `updated_on` datetime(6) DEFAULT NULL,
  `uuid` binary(16) NOT NULL,
  `channel` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_sms_notificationId` (`notification_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_soko_order` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `uuid` binary(16) NOT NULL,
  `created_on` datetime(6) DEFAULT NULL,
  `active` bit(1) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `last_modified_date` datetime(6) DEFAULT NULL,
  `order_number` varchar(255) DEFAULT NULL,
  `store_id` bigint NOT NULL,
  `customer_user_id` bigint NOT NULL,
  `status` varchar(255) DEFAULT NULL,
  `payment_status` varchar(255) DEFAULT NULL,
  `invoice_ref` varchar(255) DEFAULT NULL,
  `delivery_method` varchar(255) DEFAULT NULL,
  `delivery_address` varchar(255) DEFAULT NULL,
  `customer_phone` varchar(255) DEFAULT NULL,
  `notes` varchar(255) DEFAULT NULL,
  `destination_unit_id` bigint DEFAULT NULL,
  `subtotal` decimal(38,2) DEFAULT NULL,
  `delivery_fee` decimal(38,2) DEFAULT NULL,
  `total` decimal(38,2) DEFAULT NULL,
  `currency` varchar(255) DEFAULT NULL,
  `placed_at` datetime(6) DEFAULT NULL,
  `confirmed_at` datetime(6) DEFAULT NULL,
  `dispatched_at` datetime(6) DEFAULT NULL,
  `completed_at` datetime(6) DEFAULT NULL,
  `delivery_visitor_id` bigint DEFAULT NULL,
  `rider_id` bigint DEFAULT NULL,
  `courier_name` varchar(255) DEFAULT NULL,
  `courier_phone` varchar(255) DEFAULT NULL,
  `courier_vehicle_plate` varchar(255) DEFAULT NULL,
  `delivery_code` varchar(255) DEFAULT NULL,
  `delivery_code_verified` bit(1) NOT NULL DEFAULT b'0',
  `delivery_code_attempts` int NOT NULL DEFAULT '0',
  `reservation_expires_at` datetime(6) DEFAULT NULL,
  `stock_released` bit(1) NOT NULL DEFAULT b'0',
  `cancelled_at` datetime(6) DEFAULT NULL,
  `cancellation_reason` varchar(255) DEFAULT NULL,
  `refund_status` varchar(255) DEFAULT NULL,
  `refund_reference` varchar(255) DEFAULT NULL,
  `refunded_amount` decimal(38,2) DEFAULT NULL,
  `settlement_status` varchar(255) DEFAULT NULL,
  `settlement_reference` varchar(255) DEFAULT NULL,
  `settled_amount` decimal(38,2) DEFAULT NULL,
  `delivery_recipient_name` varchar(255) DEFAULT NULL,
  `delivery_proof_reference` varchar(255) DEFAULT NULL,
  `delivery_proof_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_soko_order_uuid` (`uuid`),
  UNIQUE KEY `uk_soko_order_number` (`order_number`),
  UNIQUE KEY `idx_soko_order_invoice` (`invoice_ref`),
  KEY `idx_soko_order_customer` (`customer_user_id`,`created_on`),
  KEY `idx_soko_order_store` (`store_id`,`status`),
  KEY `idx_soko_order_rider` (`rider_id`),
  KEY `idx_soko_order_reservation` (`status`,`reservation_expires_at`,`stock_released`),
  CONSTRAINT `fk_soko_order_rider` FOREIGN KEY (`rider_id`) REFERENCES `pms_soko_rider` (`id`),
  CONSTRAINT `fk_soko_order_store` FOREIGN KEY (`store_id`) REFERENCES `pms_soko_store` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_soko_order_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `uuid` binary(16) NOT NULL,
  `created_on` datetime(6) DEFAULT NULL,
  `active` bit(1) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `last_modified_date` datetime(6) DEFAULT NULL,
  `order_id` bigint NOT NULL,
  `product_id` bigint NOT NULL,
  `product_name` varchar(255) DEFAULT NULL,
  `unit` varchar(255) DEFAULT NULL,
  `unit_price` decimal(38,2) DEFAULT NULL,
  `quantity` int NOT NULL,
  `line_total` decimal(38,2) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_soko_order_item_uuid` (`uuid`),
  KEY `idx_soko_order_item_order` (`order_id`),
  CONSTRAINT `fk_soko_item_order` FOREIGN KEY (`order_id`) REFERENCES `pms_soko_order` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_soko_product` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `uuid` binary(16) NOT NULL,
  `created_on` datetime(6) DEFAULT NULL,
  `active` bit(1) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `last_modified_date` datetime(6) DEFAULT NULL,
  `store_id` bigint NOT NULL,
  `name` varchar(255) DEFAULT NULL,
  `description` varchar(255) DEFAULT NULL,
  `category` varchar(255) DEFAULT NULL,
  `unit` varchar(255) DEFAULT NULL,
  `price` decimal(38,2) DEFAULT NULL,
  `currency` varchar(255) DEFAULT NULL,
  `stock_quantity` int NOT NULL,
  `image_url` varchar(255) DEFAULT NULL,
  `status` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_soko_product_uuid` (`uuid`),
  KEY `idx_soko_product_store` (`store_id`,`active`),
  KEY `idx_soko_product_catalog` (`status`,`category`,`active`),
  CONSTRAINT `fk_soko_product_store` FOREIGN KEY (`store_id`) REFERENCES `pms_soko_store` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_soko_rider` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `uuid` binary(16) NOT NULL,
  `created_on` datetime(6) DEFAULT NULL,
  `active` bit(1) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `last_modified_date` datetime(6) DEFAULT NULL,
  `store_id` bigint NOT NULL,
  `rider_type` varchar(255) DEFAULT NULL,
  `display_name` varchar(255) DEFAULT NULL,
  `phone_number` varchar(255) DEFAULT NULL,
  `email` varchar(255) DEFAULT NULL,
  `vehicle_type` varchar(255) DEFAULT NULL,
  `vehicle_plate` varchar(255) DEFAULT NULL,
  `availability` varchar(255) DEFAULT NULL,
  `status` varchar(255) DEFAULT NULL,
  `verified` bit(1) NOT NULL,
  `completed_deliveries` int NOT NULL,
  `notes` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_soko_rider_uuid` (`uuid`),
  KEY `idx_soko_rider_store` (`store_id`,`active`),
  KEY `idx_soko_rider_availability` (`store_id`,`availability`,`active`),
  KEY `idx_soko_rider_phone` (`store_id`,`phone_number`),
  CONSTRAINT `fk_soko_rider_store` FOREIGN KEY (`store_id`) REFERENCES `pms_soko_store` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_soko_store` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `uuid` binary(16) NOT NULL,
  `created_on` datetime(6) DEFAULT NULL,
  `active` bit(1) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `last_modified_date` datetime(6) DEFAULT NULL,
  `owner_user_id` bigint NOT NULL,
  `name` varchar(255) DEFAULT NULL,
  `description` varchar(255) DEFAULT NULL,
  `phone_number` varchar(255) DEFAULT NULL,
  `address` varchar(255) DEFAULT NULL,
  `latitude` double DEFAULT NULL,
  `longitude` double DEFAULT NULL,
  `service_radius_km` decimal(38,2) DEFAULT NULL,
  `status` varchar(255) DEFAULT NULL,
  `pickup_enabled` bit(1) NOT NULL,
  `delivery_enabled` bit(1) NOT NULL,
  `delivery_fee` decimal(38,2) DEFAULT NULL,
  `currency` varchar(255) DEFAULT NULL,
  `payment_account_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_soko_store_uuid` (`uuid`),
  KEY `idx_soko_store_owner` (`owner_user_id`,`active`),
  KEY `idx_soko_store_status` (`status`,`active`),
  KEY `idx_soko_store_location` (`latitude`,`longitude`),
  KEY `fk_soko_store_account` (`payment_account_id`),
  CONSTRAINT `fk_soko_store_account` FOREIGN KEY (`payment_account_id`) REFERENCES `pms_payment_account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_sp_booking` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_on` datetime(6) DEFAULT NULL,
  `uuid` binary(16) NOT NULL,
  `active` bit(1) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `last_modified_date` datetime(6) DEFAULT NULL,
  `booked_by_user_id` bigint NOT NULL,
  `cancellation_reason` varchar(255) DEFAULT NULL,
  `completed_at` datetime(6) DEFAULT NULL,
  `notes` varchar(255) DEFAULT NULL,
  `scheduled_at` datetime(6) DEFAULT NULL,
  `service_id` bigint NOT NULL,
  `status` varchar(255) DEFAULT NULL,
  `quoted_amount` decimal(38,2) DEFAULT NULL,
  `currency` varchar(255) DEFAULT NULL,
  `pricing_unit` varchar(40) DEFAULT NULL,
  `property_id` bigint DEFAULT NULL,
  `unit_id` bigint DEFAULT NULL,
  `payment_account_id` bigint DEFAULT NULL,
  `payment_channel` varchar(255) DEFAULT NULL,
  `invoice_ref` varchar(255) DEFAULT NULL,
  `payment_status` varchar(255) DEFAULT NULL,
  `provider_reference` varchar(255) DEFAULT NULL,
  `refund_status` varchar(255) DEFAULT NULL,
  `refund_reference` varchar(255) DEFAULT NULL,
  `refunded_amount` decimal(38,2) DEFAULT NULL,
  `settlement_status` varchar(255) DEFAULT NULL,
  `settlement_reference` varchar(255) DEFAULT NULL,
  `settled_amount` decimal(38,2) DEFAULT NULL,
  `completion_evidence_reference` varchar(255) DEFAULT NULL,
  `started_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKfqcg8iv6sxfog4cwx8754x9ya` (`uuid`),
  UNIQUE KEY `uk_sp_booking_invoice` (`invoice_ref`),
  KEY `idx_sp_booking_serviceId` (`service_id`),
  KEY `idx_sp_booking_bookedByUserId` (`booked_by_user_id`),
  KEY `idx_sp_booking_status` (`status`),
  KEY `idx_sp_booking_createdBy` (`created_by`),
  KEY `idx_sp_booking_customer_status` (`created_by`,`status`,`scheduled_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_sp_category` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_on` datetime(6) DEFAULT NULL,
  `uuid` binary(16) NOT NULL,
  `active` bit(1) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `last_modified_date` datetime(6) DEFAULT NULL,
  `description` varchar(255) DEFAULT NULL,
  `name` varchar(255) DEFAULT NULL,
  `required_number_of_referees` int NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK6k4ef32cjspdca5aake6vn2uy` (`uuid`),
  KEY `idx_sp_category_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_sp_category_doc_types` (
  `category_id` bigint NOT NULL,
  `document_type` enum('ACADEMIC_CERTIFICATE','BUSINESS_REGISTRATION','GOOD_CONDUCT','HEALTH_CERTIFICATE','INSURANCE_CERTIFICATE','NATIONAL_ID','OTHER','PASSPORT','PROFESSIONAL_CERTIFICATE','TAX_CERTIFICATE','WORK_PERMIT') DEFAULT NULL,
  KEY `FK7u001bl8l0k1jaiisekehk08r` (`category_id`),
  CONSTRAINT `FK7u001bl8l0k1jaiisekehk08r` FOREIGN KEY (`category_id`) REFERENCES `pms_sp_category` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_sp_complaint` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_on` datetime(6) DEFAULT NULL,
  `uuid` binary(16) NOT NULL,
  `active` bit(1) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `last_modified_date` datetime(6) DEFAULT NULL,
  `admin_notes` varchar(255) DEFAULT NULL,
  `assigned_admin_id` bigint DEFAULT NULL,
  `description` varchar(255) DEFAULT NULL,
  `filed_by_user_id` bigint NOT NULL,
  `resolution` varchar(255) DEFAULT NULL,
  `service_id` bigint NOT NULL,
  `status` varchar(255) DEFAULT NULL,
  `booking_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK7s5fxi9kxsxf3oab5vwtjrwj3` (`uuid`),
  KEY `idx_sp_complaint_serviceId` (`service_id`),
  KEY `idx_sp_complaint_status` (`status`),
  KEY `idx_sp_complaint_assignedAdminId` (`assigned_admin_id`),
  KEY `idx_sp_complaint_bookingId` (`booking_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_sp_document` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_on` datetime(6) DEFAULT NULL,
  `uuid` binary(16) NOT NULL,
  `active` bit(1) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `last_modified_date` datetime(6) DEFAULT NULL,
  `admin_notes` varchar(255) DEFAULT NULL,
  `document_type` varchar(255) DEFAULT NULL,
  `expiry_date` datetime(6) DEFAULT NULL,
  `file_ref` varchar(255) DEFAULT NULL,
  `service_id` bigint NOT NULL,
  `verification_status` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK1g4x1h1lh2fnrctiuvaknh8ns` (`uuid`),
  KEY `idx_sp_document_serviceId` (`service_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_sp_profile` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_on` datetime(6) DEFAULT NULL,
  `uuid` binary(16) NOT NULL,
  `active` bit(1) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `last_modified_date` datetime(6) DEFAULT NULL,
  `admin_notes` varchar(255) DEFAULT NULL,
  `business_name` varchar(255) DEFAULT NULL,
  `consent_ip_address` varchar(255) DEFAULT NULL,
  `consent_timestamp` datetime(6) DEFAULT NULL,
  `latitude` double DEFAULT NULL,
  `longitude` double DEFAULT NULL,
  `status` varchar(255) DEFAULT NULL,
  `user_id` bigint NOT NULL,
  `payment_account_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKdu5hwum3aqmmv1b8buwge922r` (`uuid`),
  KEY `idx_sp_profile_userId` (`user_id`),
  KEY `idx_sp_profile_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_sp_rating` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_on` datetime(6) DEFAULT NULL,
  `uuid` binary(16) NOT NULL,
  `active` bit(1) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `last_modified_date` datetime(6) DEFAULT NULL,
  `booking_id` bigint NOT NULL,
  `comment` varchar(255) DEFAULT NULL,
  `rated_by_user_id` bigint NOT NULL,
  `service_id` bigint NOT NULL,
  `stars` int NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK1qjcl5noc2c64ik9upf5nuyf3` (`uuid`),
  KEY `idx_sp_rating_serviceId` (`service_id`),
  KEY `idx_sp_rating_bookingId` (`booking_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_sp_referee` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_on` datetime(6) DEFAULT NULL,
  `uuid` binary(16) NOT NULL,
  `active` bit(1) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `last_modified_date` datetime(6) DEFAULT NULL,
  `admin_notes` varchar(255) DEFAULT NULL,
  `contact` varchar(255) DEFAULT NULL,
  `name` varchar(255) DEFAULT NULL,
  `profile_id` bigint NOT NULL,
  `verification_status` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKam3yxbr4jls20i9hlp17v67dq` (`uuid`),
  KEY `idx_sp_referee_profileId` (`profile_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_sp_risk_score` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_on` datetime(6) DEFAULT NULL,
  `uuid` binary(16) NOT NULL,
  `active` bit(1) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `last_modified_date` datetime(6) DEFAULT NULL,
  `computed_at` datetime(6) DEFAULT NULL,
  `factors_json` varchar(255) DEFAULT NULL,
  `highly_rated_completed_count` int NOT NULL,
  `label` varchar(255) DEFAULT NULL,
  `service_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKkajm4ids983p1e50yos6wa6hr` (`uuid`),
  KEY `idx_sp_risk_serviceId` (`service_id`),
  KEY `idx_sp_risk_label` (`label`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_sp_service` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_on` datetime(6) DEFAULT NULL,
  `uuid` binary(16) NOT NULL,
  `active` bit(1) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `last_modified_date` datetime(6) DEFAULT NULL,
  `amount` decimal(38,2) DEFAULT NULL,
  `category_id` bigint NOT NULL,
  `category_name` varchar(255) DEFAULT NULL,
  `currency` varchar(255) DEFAULT NULL,
  `pricing_unit` enum('FIXED','PER_DAY','PER_HOUR','PER_ITEM','PER_JOB','PER_KM','PER_MONTH','PER_SQFT','PER_SQMT','PER_VISIT','PER_WEEK') DEFAULT NULL,
  `profile_id` bigint NOT NULL,
  `status` varchar(255) DEFAULT NULL,
  `tier` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKoj8yjvafu3ag9r906jxfrudyr` (`uuid`),
  KEY `idx_sp_service_profileId` (`profile_id`),
  KEY `idx_sp_service_categoryId` (`category_id`),
  KEY `idx_sp_service_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_sp_tier` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_on` datetime(6) DEFAULT NULL,
  `uuid` binary(16) NOT NULL,
  `active` bit(1) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `last_modified_date` datetime(6) DEFAULT NULL,
  `description` varchar(255) DEFAULT NULL,
  `name` varchar(255) DEFAULT NULL,
  `requirements` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK9t23m9r5r6p9n3m83g7vuqm7j` (`uuid`),
  KEY `idx_sp_tier_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_subscription_event` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_on` datetime(6) DEFAULT NULL,
  `uuid` binary(16) NOT NULL,
  `active` bit(1) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `last_modified_date` datetime(6) DEFAULT NULL,
  `event_type` varchar(64) DEFAULT NULL,
  `notes` varchar(255) DEFAULT NULL,
  `user_subscription_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKbjatwg74lidesyb6ex2rgr75g` (`uuid`),
  KEY `idx_subscription_event_type` (`event_type`,`active`),
  KEY `FKdwtturhu0tmpfhvcew3k41d89` (`user_subscription_id`),
  CONSTRAINT `FKdwtturhu0tmpfhvcew3k41d89` FOREIGN KEY (`user_subscription_id`) REFERENCES `pms_user_subscription` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_subscription_payment_completion` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_on` datetime(6) DEFAULT NULL,
  `uuid` binary(16) NOT NULL,
  `active` bit(1) NOT NULL,
  `invoice_id` bigint NOT NULL,
  `plan_code` varchar(64) NOT NULL,
  `provider_reference` varchar(256) DEFAULT NULL,
  `subscriber_user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sub_pay_completion_invoice` (`invoice_id`),
  UNIQUE KEY `UKrpef62y3tg1rsbnus1j35xq60` (`uuid`),
  KEY `idx_sub_pay_completion_subscriber` (`subscriber_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_subscription_plan` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_on` datetime(6) DEFAULT NULL,
  `uuid` binary(16) NOT NULL,
  `active` bit(1) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `last_modified_date` datetime(6) DEFAULT NULL,
  `billing_cycle` enum('LIFETIME','MONTHLY','QUARTERLY','YEARLY') DEFAULT NULL,
  `code` varchar(255) DEFAULT NULL,
  `currency` varchar(255) DEFAULT NULL,
  `product_key` varchar(64) NOT NULL,
  `purchase_mode` varchar(32) NOT NULL,
  `tier_rank` int NOT NULL,
  `display_name` varchar(255) DEFAULT NULL,
  `plan_category` varchar(64) DEFAULT NULL,
  `price` decimal(38,2) DEFAULT NULL,
  `role_family` varchar(64) DEFAULT NULL,
  `active_package_key` varchar(700) GENERATED ALWAYS AS ((case when (`active` = 0x01) then lower(concat(`product_key`,_utf8mb4'|',`billing_cycle`,_utf8mb4'|',trim(`display_name`))) else NULL end)) STORED,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKfdi700xc26mwrf50geljs99de` (`uuid`),
  UNIQUE KEY `idx_sub_plan_code` (`code`),
  UNIQUE KEY `uk_subscription_active_package` (`active_package_key`),
  KEY `idx_sub_plan_category` (`plan_category`,`active`),
  KEY `idx_subscription_product` (`product_key`,`active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_team_role_definition` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `uuid` binary(16) NOT NULL,
  `created_on` datetime(6) DEFAULT NULL,
  `active` bit(1) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `last_modified_date` datetime(6) DEFAULT NULL,
  `code` varchar(80) NOT NULL,
  `display_name` varchar(120) NOT NULL,
  `description` varchar(300) DEFAULT NULL,
  `business_area` varchar(40) NOT NULL,
  `permission_template` varchar(40) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_team_role_definition_uuid` (`uuid`),
  UNIQUE KEY `uk_team_role_definition_code` (`code`),
  KEY `idx_team_role_definition_area` (`business_area`,`active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_tenant` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_on` datetime(6) DEFAULT NULL,
  `active` bit(1) NOT NULL,
  `lease_id` bigint NOT NULL,
  `unit_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `uuid` binary(16) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKlsmfkvetc4sdrqm2u87415id1` (`uuid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_unit` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_on` datetime(6) DEFAULT NULL,
  `active` bit(1) NOT NULL,
  `created_by` bigint NOT NULL,
  `advertise` bit(1) NOT NULL,
  `currency` varchar(255) DEFAULT NULL,
  `image_path` varchar(255) DEFAULT NULL,
  `lease_mode` varchar(255) DEFAULT NULL,
  `measurement_units` int NOT NULL,
  `occupied` bit(1) NOT NULL,
  `price` double NOT NULL,
  `property_id` bigint NOT NULL,
  `ref` varchar(255) DEFAULT NULL,
  `size` double NOT NULL,
  `thumbnail` varchar(255) DEFAULT NULL,
  `utilities` varchar(255) DEFAULT NULL,
  `last_modified_date` datetime(6) DEFAULT NULL,
  `uuid` binary(16) NOT NULL,
  `template_id` bigint DEFAULT NULL,
  `unit_type` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_unit_created_by` (`created_by`,`active`),
  KEY `idx_unit_property_id` (`property_id`),
  KEY `idx_unit_property_active` (`property_id`,`active`),
  CONSTRAINT `FK4mjfwdw51rnykvcmctw6b191b` FOREIGN KEY (`property_id`) REFERENCES `pms_property` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_unit_charge` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_on` datetime(6) DEFAULT NULL,
  `amount` double NOT NULL,
  `charge_id` bigint NOT NULL,
  `period` varchar(255) DEFAULT NULL,
  `unit_id` bigint NOT NULL,
  `uuid` binary(16) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_unit_charge_unitId` (`unit_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_unit_tenant` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_on` datetime(6) DEFAULT NULL,
  `active` bit(1) NOT NULL,
  `invite_id` bigint NOT NULL,
  `lease_accepted` bit(1) NOT NULL,
  `unit_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `uuid` binary(16) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKjv62eyykcvyy8dmtod78oanj3` (`uuid`),
  KEY `idx_unit_tenant_userId` (`user_id`),
  KEY `FKlelcv98bk59kaj88t8a15dvik` (`unit_id`),
  KEY `idx_unit_tenant_inviteId` (`invite_id`),
  KEY `idx_unit_tenant_active` (`active`),
  KEY `idx_tenancy_unit_active_accepted` (`unit_id`,`active`,`lease_accepted`),
  CONSTRAINT `FKlelcv98bk59kaj88t8a15dvik` FOREIGN KEY (`unit_id`) REFERENCES `pms_unit` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_unit_type` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_on` datetime(6) DEFAULT NULL,
  `active` bit(1) NOT NULL,
  `description` longtext,
  `name` varchar(255) DEFAULT NULL,
  `uuid` binary(16) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_unit_type_mapping` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_on` datetime(6) DEFAULT NULL,
  `uuid` binary(16) NOT NULL,
  `active` bit(1) NOT NULL,
  `property_type` enum('AGRICULTURAL_LAND','AIRBNB_UNIT','APARTMENT_BLOCK','BOUTIQUE_HOTEL','BUNGALOW','BUSINESS_PARK','COLD_STORAGE','COMMERCIAL_AND_HOSTELS','COMMERCIAL_PLOT','CO_WORKING_SPACE','EPZ_FACILITY','FACTORY_HEAVY','FACTORY_LIGHT','GATED_ESTATE','GODOWN','GRADE_A_OFFICE','GUEST_HOUSE','HOTEL','INDUSTRIAL_PLOT','MAISONETTE','MALL_AND_APARTMENTS','MEDICAL_PLAZA','MIXED_RETAIL_PLAZA','OFFICE_AND_RESIDENTIAL','OFFICE_BLOCK_CBD','RESIDENTIAL_PLOT','RESORT','RETAIL_SHOP','SERVICED_APARTMENT','SERVICED_PLOT','SHOPPING_MALL','SHOPS_AND_APARTMENTS','STANDALONE_HOUSE','STUDENT_HOSTEL','TOWNHOUSE','WAREHOUSE') NOT NULL,
  `unit_type` enum('ANCHOR_TENANT','APARTMENT_UNIT','BEDSITTER','CLINIC_ROOM','COTTAGE','DELUXE_ROOM','DESK','ENSUITE_ROOM','ENTIRE_UNIT','EXECUTIVE_SUITE','FIVE_BEDROOM_PLUS','FOUR_BEDROOM','HALF_FLOOR','INLINE_SHOP','KIOSK','LAB_SPACE','MANUFACTURING_UNIT','MINI_SUPERMARKET','OFFICE_UNIT','ONE_BEDROOM','OPEN_PLAN','OPEN_WAREHOUSE','PARTITIONED_OFFICE','PENTHOUSE','PRIVATE_OFFICE','PRODUCTION_UNIT','RACKED_WAREHOUSE','RETAIL_UNIT','ROOM','SHARED_ROOM','SHOP','SINGLE_ROOM','STANDARD_ROOM','STORAGE_UNIT','STUDENT_ROOM','STUDIO','SUITE','TEMPERATURE_CONTROLLED_UNIT','THREE_BEDROOM','TOWNHOUSES','TWO_BEDROOM','VILLAS','WHOLE_FLOOR') NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_unit_type_property_type_unique` (`property_type`,`unit_type`),
  UNIQUE KEY `UKnvisgqj2ysbgquh6d154al16e` (`uuid`),
  KEY `idx_unit_type_mapping_property_active` (`property_type`,`active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_user_otp` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_on` datetime(6) DEFAULT NULL,
  `active` bit(1) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `last_modified_date` datetime(6) DEFAULT NULL,
  `attempts` int NOT NULL,
  `channel` varchar(255) DEFAULT NULL,
  `contact` varchar(255) DEFAULT NULL,
  `otp` longblob,
  `otp_expiry_time` datetime(6) DEFAULT NULL,
  `verified` bit(1) NOT NULL,
  `uuid` binary(16) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_user_role` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_on` datetime(6) DEFAULT NULL,
  `role_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `uuid` binary(16) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_user_subscription` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_on` datetime(6) DEFAULT NULL,
  `uuid` binary(16) NOT NULL,
  `active` bit(1) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `last_modified_date` datetime(6) DEFAULT NULL,
  `auto_renew` bit(1) NOT NULL,
  `end_at` datetime(6) DEFAULT NULL,
  `plan_code` varchar(255) DEFAULT NULL,
  `product_key` varchar(64) NOT NULL,
  `role` varchar(64) DEFAULT NULL,
  `source_payment_ref` varchar(255) DEFAULT NULL,
  `term_version` bigint NOT NULL DEFAULT '0',
  `start_at` datetime(6) DEFAULT NULL,
  `status` enum('ACTIVE','CANCELLED','EXPIRED','PENDING') DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK5k9ljamydnun2oo1bjkwkhpji` (`uuid`),
  KEY `idx_user_subscription_active` (`created_by`,`status`,`active`),
  KEY `idx_user_subscription_product` (`created_by`,`product_key`,`status`,`active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_on` datetime(6) DEFAULT NULL,
  `email` varchar(255) DEFAULT NULL,
  `full_name` varchar(255) DEFAULT NULL,
  `google_id` varchar(255) DEFAULT NULL,
  `password` varchar(255) DEFAULT NULL,
  `registrationip` varchar(255) DEFAULT NULL,
  `source` varchar(255) DEFAULT NULL,
  `surname` varchar(255) DEFAULT NULL,
  `active` bit(1) NOT NULL,
  `city` varchar(255) DEFAULT NULL,
  `country` varchar(255) DEFAULT NULL,
  `country_code` varchar(255) DEFAULT NULL,
  `totp_secret` longblob,
  `email_verified` bit(1) NOT NULL,
  `last_login` datetime(6) DEFAULT NULL,
  `locale` varchar(255) DEFAULT NULL,
  `otp_expiry_time` datetime(6) DEFAULT NULL,
  `temp_otp` longblob,
  `phone_number` varchar(255) DEFAULT NULL,
  `invite_id` bigint DEFAULT NULL,
  `verified` bit(1) NOT NULL,
  `identification_number` varchar(255) DEFAULT NULL,
  `last_modified_date` datetime(6) DEFAULT NULL,
  `profile_type` varchar(255) DEFAULT NULL,
  `organization_name` varchar(255) DEFAULT NULL,
  `tax_pin` varchar(255) DEFAULT NULL,
  `uuid` binary(16) NOT NULL,
  `refresh_token` varchar(255) DEFAULT NULL,
  `phone_verified` bit(1) NOT NULL DEFAULT b'0',
  `account_status` varchar(255) DEFAULT NULL,
  `phone_verified_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_users_phoneNumber` (`phone_number`),
  UNIQUE KEY `idx_users_refreshToken` (`refresh_token`),
  UNIQUE KEY `idx_users_email` (`email`),
  UNIQUE KEY `idx_users_profile` (`country`,`identification_number`,`tax_pin`),
  KEY `idx_users_account_status` (`account_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_utilities` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_on` datetime(6) DEFAULT NULL,
  `active` bit(1) NOT NULL,
  `name` varchar(255) DEFAULT NULL,
  `uuid` binary(16) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_visitor` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_on` datetime(6) DEFAULT NULL,
  `uuid` binary(16) NOT NULL,
  `active` bit(1) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `last_modified_date` datetime(6) DEFAULT NULL,
  `category` varchar(255) DEFAULT NULL,
  `chargeable` bit(1) NOT NULL,
  `check_in_guard_name` varchar(255) DEFAULT NULL,
  `check_out_guard_name` varchar(255) DEFAULT NULL,
  `expected_arrival_time` datetime(6) DEFAULT NULL,
  `parking_lot` varchar(255) DEFAULT NULL,
  `phone_number` varchar(255) DEFAULT NULL,
  `property_id` bigint NOT NULL,
  `property_name` varchar(255) DEFAULT NULL,
  `status` varchar(255) DEFAULT NULL,
  `unit_id` bigint NOT NULL,
  `unit_ref` varchar(255) DEFAULT NULL,
  `vehicle_plate` varchar(255) DEFAULT NULL,
  `visitor_name` varchar(255) DEFAULT NULL,
  `visit_type` varchar(255) DEFAULT NULL,
  `purpose` varchar(255) DEFAULT NULL,
  `company_name` varchar(255) DEFAULT NULL,
  `tracking_number` varchar(255) DEFAULT NULL,
  `credential_hash` varchar(255) DEFAULT NULL,
  `credential_hint` varchar(255) DEFAULT NULL,
  `valid_from` datetime(6) DEFAULT NULL,
  `valid_until` datetime(6) DEFAULT NULL,
  `approved_at` datetime(6) DEFAULT NULL,
  `approved_by` bigint DEFAULT NULL,
  `host_user_id` bigint DEFAULT NULL,
  `checked_in_at` datetime(6) DEFAULT NULL,
  `checked_out_at` datetime(6) DEFAULT NULL,
  `entry_count` int NOT NULL DEFAULT '0',
  `max_entries` int NOT NULL DEFAULT '1',
  `requires_approval` bit(1) NOT NULL DEFAULT b'0',
  `decision_reason` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKbksy8nihnx01wava2dg3yw0wi` (`uuid`),
  UNIQUE KEY `uk_visitor_credential_hash` (`credential_hash`),
  KEY `idx_visitor_createdBy` (`created_by`),
  KEY `idx_visitor_unitId` (`unit_id`),
  KEY `idx_visitor_propertyId` (`property_id`),
  KEY `idx_visitor_status` (`status`),
  KEY `idx_visitor_active` (`active`),
  KEY `idx_visitor_active_createdBy` (`active`,`created_by`),
  KEY `idx_visitor_active_status_createdBy` (`active`,`status`,`created_by`),
  KEY `idx_visitor_expectedArrivalTime` (`expected_arrival_time`),
  KEY `idx_visitor_access_window` (`property_id`,`status`,`valid_from`,`valid_until`),
  KEY `idx_visitor_unit_active_status_arrival` (`unit_id`,`active`,`status`,`expected_arrival_time`),
  KEY `idx_visitor_property_active_arrival` (`property_id`,`active`,`expected_arrival_time`),
  KEY `idx_visitor_active_phone` (`active`,`phone_number`),
  KEY `idx_visitor_expiry_scan` (`active`,`status`,`valid_until`,`expected_arrival_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_visitor_access_event` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `uuid` binary(16) NOT NULL,
  `created_on` datetime(6) DEFAULT NULL,
  `active` bit(1) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `last_modified_date` datetime(6) DEFAULT NULL,
  `visitor_id` bigint DEFAULT NULL,
  `property_id` bigint NOT NULL,
  `device_id` bigint DEFAULT NULL,
  `source` varchar(255) DEFAULT NULL,
  `direction` varchar(255) DEFAULT NULL,
  `outcome` varchar(255) DEFAULT NULL,
  `reason_code` varchar(255) DEFAULT NULL,
  `correlation_id` varchar(255) DEFAULT NULL,
  `vehicle_plate` varchar(255) DEFAULT NULL,
  `occurred_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_access_event_uuid` (`uuid`),
  UNIQUE KEY `idx_access_event_correlation` (`correlation_id`),
  KEY `idx_access_event_visitor` (`visitor_id`,`occurred_at`),
  KEY `idx_access_event_property` (`property_id`,`occurred_at`),
  KEY `fk_access_event_device` (`device_id`),
  CONSTRAINT `fk_access_event_device` FOREIGN KEY (`device_id`) REFERENCES `pms_gate_device` (`id`),
  CONSTRAINT `fk_access_event_visitor` FOREIGN KEY (`visitor_id`) REFERENCES `pms_visitor` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_wealth_asset` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `uuid` binary(16) NOT NULL,
  `created_on` datetime(6) DEFAULT NULL,
  `active` bit(1) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `last_modified_date` datetime(6) DEFAULT NULL,
  `owner_user_id` bigint NOT NULL,
  `property_id` bigint DEFAULT NULL,
  `asset_type` varchar(40) NOT NULL,
  `name` varchar(160) NOT NULL,
  `reference` varchar(120) DEFAULT NULL,
  `location` varchar(500) DEFAULT NULL,
  `currency` varchar(3) NOT NULL,
  `acquisition_cost` decimal(19,2) DEFAULT NULL,
  `acquisition_date` date DEFAULT NULL,
  `current_value` decimal(19,2) NOT NULL,
  `valuation_date` date NOT NULL,
  `status` varchar(30) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wealth_asset_uuid` (`uuid`),
  KEY `idx_wealth_asset_owner` (`owner_user_id`,`active`),
  KEY `idx_wealth_asset_property` (`property_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_wealth_cash_flow` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `uuid` binary(16) NOT NULL,
  `created_on` datetime(6) DEFAULT NULL,
  `active` bit(1) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `last_modified_date` datetime(6) DEFAULT NULL,
  `asset_id` bigint NOT NULL,
  `flow_type` varchar(20) NOT NULL,
  `category` varchar(60) NOT NULL,
  `amount` decimal(19,2) NOT NULL,
  `entry_date` date NOT NULL,
  `description` varchar(500) DEFAULT NULL,
  `recurring` bit(1) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wealth_cash_uuid` (`uuid`),
  KEY `idx_wealth_cash_asset_date` (`asset_id`,`entry_date`),
  CONSTRAINT `fk_wealth_cash_asset` FOREIGN KEY (`asset_id`) REFERENCES `pms_wealth_asset` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_wealth_goal` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `uuid` binary(16) NOT NULL,
  `created_on` datetime(6) DEFAULT NULL,
  `active` bit(1) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `last_modified_date` datetime(6) DEFAULT NULL,
  `owner_user_id` bigint NOT NULL,
  `goal_type` varchar(40) NOT NULL,
  `name` varchar(160) NOT NULL,
  `target_amount` decimal(19,2) NOT NULL,
  `currency` varchar(3) NOT NULL,
  `target_date` date NOT NULL,
  `status` varchar(30) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wealth_goal_uuid` (`uuid`),
  KEY `idx_wealth_goal_owner` (`owner_user_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_wealth_liability` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `uuid` binary(16) NOT NULL,
  `created_on` datetime(6) DEFAULT NULL,
  `active` bit(1) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `last_modified_date` datetime(6) DEFAULT NULL,
  `asset_id` bigint NOT NULL,
  `lender` varchar(100) NOT NULL,
  `currency` varchar(3) NOT NULL,
  `original_principal` decimal(19,2) NOT NULL,
  `outstanding_principal` decimal(19,2) NOT NULL,
  `annual_interest_rate` decimal(8,4) DEFAULT NULL,
  `monthly_payment` decimal(19,2) DEFAULT NULL,
  `start_date` date DEFAULT NULL,
  `maturity_date` date DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wealth_liability_uuid` (`uuid`),
  KEY `idx_wealth_liability_asset` (`asset_id`,`active`),
  CONSTRAINT `fk_wealth_liability_asset` FOREIGN KEY (`asset_id`) REFERENCES `pms_wealth_asset` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_wealth_obligation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `uuid` binary(16) NOT NULL,
  `created_on` datetime(6) DEFAULT NULL,
  `active` bit(1) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `last_modified_date` datetime(6) DEFAULT NULL,
  `asset_id` bigint NOT NULL,
  `obligation_type` varchar(40) NOT NULL,
  `title` varchar(160) NOT NULL,
  `effective_date` date DEFAULT NULL,
  `due_date` date DEFAULT NULL,
  `expiry_date` date DEFAULT NULL,
  `amount` decimal(19,2) DEFAULT NULL,
  `currency` varchar(3) DEFAULT NULL,
  `status` varchar(30) NOT NULL,
  `reminder_days` int NOT NULL,
  `notes` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wealth_obligation_uuid` (`uuid`),
  KEY `idx_wealth_obligation_due` (`asset_id`,`due_date`,`status`),
  CONSTRAINT `fk_wealth_obligation_asset` FOREIGN KEY (`asset_id`) REFERENCES `pms_wealth_asset` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_wealth_valuation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `uuid` binary(16) NOT NULL,
  `created_on` datetime(6) DEFAULT NULL,
  `active` bit(1) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `last_modified_date` datetime(6) DEFAULT NULL,
  `asset_id` bigint NOT NULL,
  `amount` decimal(19,2) NOT NULL,
  `valuation_date` date NOT NULL,
  `source` varchar(60) NOT NULL,
  `notes` varchar(1000) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wealth_valuation_uuid` (`uuid`),
  KEY `idx_wealth_valuation_asset` (`asset_id`,`valuation_date`),
  CONSTRAINT `fk_wealth_valuation_asset` FOREIGN KEY (`asset_id`) REFERENCES `pms_wealth_asset` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_wealth_vault_document` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `uuid` binary(16) NOT NULL,
  `created_on` datetime(6) DEFAULT NULL,
  `active` bit(1) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `last_modified_date` datetime(6) DEFAULT NULL,
  `asset_id` bigint NOT NULL,
  `category` varchar(50) NOT NULL,
  `display_name` varchar(255) NOT NULL,
  `file_ref` varchar(800) NOT NULL,
  `content_type` varchar(120) NOT NULL,
  `file_size` bigint NOT NULL,
  `checksum_sha256` varchar(64) NOT NULL,
  `document_date` date DEFAULT NULL,
  `expiry_date` date DEFAULT NULL,
  `notes` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wealth_vault_uuid` (`uuid`),
  KEY `idx_wealth_vault_asset` (`asset_id`,`active`),
  CONSTRAINT `fk_wealth_vault_asset` FOREIGN KEY (`asset_id`) REFERENCES `pms_wealth_asset` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_workspace_invitation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `uuid` binary(16) NOT NULL,
  `created_on` datetime(6) DEFAULT NULL,
  `active` bit(1) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `last_modified_date` datetime(6) DEFAULT NULL,
  `workspace_id` bigint NOT NULL,
  `recipient_email` varchar(254) NOT NULL,
  `role_definition_id` bigint NOT NULL,
  `membership_role` varchar(40) NOT NULL,
  `scope_type` varchar(30) NOT NULL,
  `resource_ids_json` text,
  `token_hash` varchar(64) NOT NULL,
  `status` varchar(30) NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `accepted_at` datetime(6) DEFAULT NULL,
  `last_sent_at` datetime(6) DEFAULT NULL,
  `resend_count` int NOT NULL DEFAULT '0',
  `membership_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_workspace_invitation_uuid` (`uuid`),
  UNIQUE KEY `uk_workspace_invite_token_hash` (`token_hash`),
  KEY `idx_workspace_invite_workspace_status` (`workspace_id`,`status`,`active`),
  KEY `idx_workspace_invite_email` (`recipient_email`,`status`),
  KEY `fk_workspace_invite_role` (`role_definition_id`),
  CONSTRAINT `fk_workspace_invite_role` FOREIGN KEY (`role_definition_id`) REFERENCES `pms_team_role_definition` (`id`),
  CONSTRAINT `fk_workspace_invite_workspace` FOREIGN KEY (`workspace_id`) REFERENCES `pms_customer_workspace` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pms_workspace_membership` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `uuid` binary(16) NOT NULL,
  `created_on` datetime(6) DEFAULT NULL,
  `active` bit(1) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `last_modified_date` datetime(6) DEFAULT NULL,
  `workspace_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `member_email` varchar(254) NOT NULL,
  `role_definition_id` bigint NOT NULL,
  `membership_role` varchar(40) NOT NULL,
  `scope_type` varchar(30) NOT NULL,
  `resource_ids_json` text,
  `status` varchar(30) NOT NULL,
  `accepted_at` datetime(6) DEFAULT NULL,
  `activated_at` datetime(6) DEFAULT NULL,
  `suspended_at` datetime(6) DEFAULT NULL,
  `revoked_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_workspace_membership_uuid` (`uuid`),
  UNIQUE KEY `uk_workspace_membership_user` (`workspace_id`,`user_id`),
  KEY `idx_workspace_membership_workspace_status` (`workspace_id`,`status`,`active`),
  KEY `idx_workspace_membership_user` (`user_id`,`status`,`active`),
  KEY `fk_workspace_member_role` (`role_definition_id`),
  CONSTRAINT `fk_workspace_member_role` FOREIGN KEY (`role_definition_id`) REFERENCES `pms_team_role_definition` (`id`),
  CONSTRAINT `fk_workspace_member_user` FOREIGN KEY (`user_id`) REFERENCES `pms_users` (`id`),
  CONSTRAINT `fk_workspace_member_workspace` FOREIGN KEY (`workspace_id`) REFERENCES `pms_customer_workspace` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
