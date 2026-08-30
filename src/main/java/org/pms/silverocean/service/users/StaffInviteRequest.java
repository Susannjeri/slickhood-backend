package org.pms.silverocean.service.users;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;

public record StaffInviteRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotNull PMSRole role
) { }
