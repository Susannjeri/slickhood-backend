package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.pms.silverocean.database.pms.entities.base.BaseActiveEntity;

import java.time.ZonedDateTime;

@Entity
@Table(name = "pms_kyc_case", indexes = @Index(name = "idx_kyc_case_user", columnList = "userId", unique = true))
@Getter @Setter
public class KycCase extends BaseActiveEntity {
    private long userId;
    private String status;
    private String consentVersion;
    private ZonedDateTime consentAt;
    private boolean phoneVerified;
    private String registryStatus;
    private ZonedDateTime submittedAt;
    private ZonedDateTime reviewedAt;
    private Long reviewedBy;
    private String reviewNotes;
}
