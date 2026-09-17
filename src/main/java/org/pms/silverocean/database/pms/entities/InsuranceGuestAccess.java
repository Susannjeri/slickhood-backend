package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.pms.silverocean.database.pms.entities.base.BaseCreatorEntity;

import java.time.LocalDateTime;

@Entity
@Table(name = "pms_insurance_guest_access")
@Getter @Setter @NoArgsConstructor
public class InsuranceGuestAccess extends BaseCreatorEntity {
    @Column(nullable = false, unique = true, length = 36)
    private String challengeId;
    @Column(nullable = false, length = 160)
    private String fullName;
    @Column(nullable = false, length = 254)
    private String email;
    @Column(nullable = false, length = 40)
    private String phone;
    @Column(nullable = false, length = 60)
    private String otpHash;
    @Column(nullable = false)
    private LocalDateTime otpExpiresAt;
    @Column(nullable = false)
    private int otpAttempts;
    private LocalDateTime verifiedAt;
    @Column(length = 64, unique = true, columnDefinition = "CHAR(64)")
    private String accessTokenHash;
    @Lob @Column(columnDefinition = "LONGBLOB")
    private byte[] encryptedAccessToken;
    private LocalDateTime accessExpiresAt;
    private Long caseId;
    private LocalDateTime claimedAt;
    private Long claimedByUserId;
}
