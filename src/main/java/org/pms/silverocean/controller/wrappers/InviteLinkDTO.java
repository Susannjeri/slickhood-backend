package org.pms.silverocean.controller.wrappers;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.pms.silverocean.service.invites.InviteType;

public record InviteLinkDTO(@NotNull InviteType inviteType, @Positive Long entityId) {
}
