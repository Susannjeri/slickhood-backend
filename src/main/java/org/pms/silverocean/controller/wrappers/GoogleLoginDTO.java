package org.pms.silverocean.controller.wrappers;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;
import org.apache.commons.lang3.StringUtils;
import org.pms.silverocean.service.users.ProfileType;

@Getter @Setter
public class GoogleLoginDTO {
    @NotBlank(message = "idToken is required")
    private String idToken;
    private Long roleId;
    private String token;
    private String referralCode;
    private String referralCampaign;
    private ProfileType profileType = ProfileType.INDIVIDUAL;
    @Size(max = 160, message = "Organization name must not exceed 160 characters")
    private String organizationName;

    @AssertTrue(message = "Registered organization name is required for an organization account")
    public boolean isAccountIdentityValid() {
        return profileType != ProfileType.COMPANY || StringUtils.isNotBlank(organizationName);
    }
}
