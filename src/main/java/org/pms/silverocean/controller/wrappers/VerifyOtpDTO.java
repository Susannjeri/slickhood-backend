package org.pms.silverocean.controller.wrappers;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.pms.silverocean.service.auth.totp.impl.OtpType;

import java.io.Serializable;
@Getter @Setter @JsonInclude(JsonInclude.Include.NON_NULL)
public class VerifyOtpDTO implements Serializable {
    @NotBlank(message = "OTP Code is required")
    private String code;
    private String email;
    private OtpType channel;
    @Size(min = 8, message = "Password must be at least 8 characters")
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$",
            message = "Password must include uppercase, lowercase, number and special character"
    )
    private String password;
}
