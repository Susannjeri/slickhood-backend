package org.pms.silverocean.service.invites;

import org.pms.silverocean.database.pms.entities.Invite;

import java.time.LocalDate;

public record InviteDTO(long id, String link, String type, long validDays, int visits,
                        String maskedRecipient, LocalDate leaseStartDate, LocalDate leaseEndDate) {
    public InviteDTO(Invite invite, String link, long validDays) {
        this(invite.getId(), link, invite.getType(), validDays, invite.getVisits(),
                mask(invite.getRecipient()), invite.getLeaseStartDate(), invite.getLeaseEndDate());
    }

    private static String mask(String value) {
        if (value == null || value.isBlank()) return null;
        int at = value.indexOf('@');
        if (at < 1) return "***";
        return value.substring(0, 1) + "***" + value.substring(at);
    }
}
