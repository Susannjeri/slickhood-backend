package org.pms.silverocean.controller.wrappers;

import org.pms.silverocean.service.invites.InviteType;

public record InviteLinkDTO(InviteType inviteType, Long entityId) {
}
