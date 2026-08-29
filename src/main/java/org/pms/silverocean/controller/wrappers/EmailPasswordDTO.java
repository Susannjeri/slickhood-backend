package org.pms.silverocean.controller.wrappers;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter@Setter
public class EmailPasswordDTO {
    @NotBlank(message = "Email is required")
    @Email(message = "Email should be a valid email address")
    private String email;
    @NotBlank(message = "Password is required")
    private String password;
    private String token;

    public String getEmail() {
        return email.strip().toLowerCase();
    }
}
