package org.pms.silverocean.controller.wrappers;

import lombok.Getter;
import lombok.Setter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.AssertTrue;
import org.apache.commons.lang3.StringUtils;
import org.pms.silverocean.service.users.ProfileType;

@Getter
@Setter
public class RegistrationDTO extends EmailPasswordDTO {
    @NotBlank(message = "Full name is required")
    @Size(max = 120, message = "Full name must not exceed 120 characters")
    private String fullName;
    private ProfileType profileType = ProfileType.INDIVIDUAL;
    @Size(max = 160, message = "Organization name must not exceed 160 characters")
    private String organizationName;
    private Long roleId;
    @Size(max = 30)
    private String referralCode;
    @Size(max = 100)
    private String referralCampaign;

    @Override
    @Size(min = 8, message = "Password must be at least 8 characters")
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$",
            message = "Password must include uppercase, lowercase, number and special character"
    )
    public String getPassword() {
        return super.getPassword();
    }

    @AssertTrue(message = "Registered organization name is required for an organization account")
    public boolean isAccountIdentityValid() {
        return profileType != ProfileType.COMPANY || StringUtils.isNotBlank(organizationName);
    }

}
