package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.pms.silverocean.common.PMSUtils;
import org.pms.silverocean.database.pms.entities.base.BaseActiveEntity;
import org.pms.silverocean.service.users.ProfileCompleteness;
import org.pms.silverocean.service.users.ProfileType;
import org.pms.silverocean.service.kyc.AccountStatus;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;

@Table(name = "pms_users", indexes = {
        @Index(name = "idx_users_email", columnList = "email", unique = true),
        @Index(name = "idx_users_phoneNumber", columnList = "phoneNumber", unique = true),
        @Index(name = "idx_users_refreshToken", columnList = "refreshToken", unique = true),
        @Index(name = "idx_users_profile", columnList = "country, identificationNumber, taxPin", unique = true),
})
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Users extends BaseActiveEntity {
    private String fullName;
    private String email;
    private String password;
    private String phoneNumber;
    private String source;
    private String registrationIP;
    private String googleId;
    @Lob
    private byte[] totpSecret;
    private boolean emailVerified = false;
    private boolean phoneVerified = false;
    private ZonedDateTime phoneVerifiedAt;
    private String country;
    private String city;
    private String countryCode;
    private String locale;
    private ZonedDateTime lastLogin;

    private Long inviteId;
    private boolean verified = false;
    private String accountStatus = AccountStatus.PENDING_EMAIL_VERIFICATION.name();

    private String profileType = "INDIVIDUAL";
    private String organizationName;
    private String identificationNumber;
    private String taxPin;
    private LocalDateTime lastModifiedDate;
    private String refreshToken;

    public String getProfileType() {
        return StringUtils.isBlank(profileType) ? ProfileType.INDIVIDUAL.name() : profileType;
    }

    public String getAccountStatus() {
        if (StringUtils.isNotBlank(accountStatus)) return accountStatus;
        if (verified) return AccountStatus.ACTIVE.name();
        return emailVerified ? AccountStatus.PENDING_KYC.name()
                : AccountStatus.PENDING_EMAIL_VERIFICATION.name();
    }

    public boolean isMfaSetup() { //TODO add phonenumber verified to this boolean
        return emailVerified || phoneVerified || !PMSUtils.isByteArrayEmpty(totpSecret);
    }

    public boolean isCompletedProfile() {
        return StringUtils.isNotBlank(fullName) && StringUtils.isNotBlank(identificationNumber) && StringUtils.isNotBlank(phoneNumber) && StringUtils.isNotBlank(taxPin);
    }

    public ProfileCompleteness getProfileCompletenessState() {
        return new ProfileCompleteness(StringUtils.isNotBlank(fullName), StringUtils.isNotBlank(identificationNumber),
                StringUtils.isNotBlank(phoneNumber), StringUtils.isNotBlank(taxPin));
    }
}
