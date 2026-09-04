package org.pms.silverocean.controller.wrappers;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.pms.silverocean.service.invites.InviteType;

public record EmailOccupantInviteDTO(
        @NotNull InviteType inviteType,
        @Positive long entityId,
        @NotBlank @Email String email
) {
}
