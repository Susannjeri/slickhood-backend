package org.pms.silverocean.controller.wrappers;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class GoogleLoginDTO {
    @NotBlank(message = "idToken is required")
    private String idToken;
    private Long roleId;
    private String token;
    private String referralCode;
    private String referralCampaign;
}
