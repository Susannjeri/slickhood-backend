package org.pms.silverocean.service.invites;

import org.pms.silverocean.database.pms.entities.Invite;

public record InviteDTO(long id, String link, String type, long validDays, int visits) {
    public InviteDTO(Invite invite, String link, long validDays) {
        this(invite.getId(), link, invite.getType(), validDays, invite.getVisits());
    }
}
