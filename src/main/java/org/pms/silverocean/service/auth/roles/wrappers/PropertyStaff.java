package org.pms.silverocean.service.auth.roles.wrappers;

import org.pms.silverocean.service.invites.InviteDTO;

import java.util.List;

public record PropertyStaff(List<StaffProjection> staff, List<InviteDTO> invites) {
}
