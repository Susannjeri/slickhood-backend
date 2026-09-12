package org.pms.silverocean.service.invites;

import org.junit.jupiter.api.Test;
import org.pms.silverocean.database.pms.entities.Invite;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.teamaccess.TeamAccessModels;
import org.pms.silverocean.service.teamaccess.TeamAccessService;
import org.pms.silverocean.service.teamaccess.TeamMembershipStatus;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InviteTokenInspectionTest {
    private final InviteDao inviteDao = mock(InviteDao.class);
    private final TeamAccessService teamAccess = mock(TeamAccessService.class);
    private final InviteService service = new InviteService(inviteDao, null, null, null, null, null,
            null, null, teamAccess, null, null, null);

    @Test
    void activeUnexpiredInviteReturnsOnlyMinimalMetadata() {
        Invite invite = new Invite();
        invite.setType(InviteType.TENANT.name());
        invite.setExpiryDate(LocalDateTime.now().plusHours(2));
        when(teamAccess.isTeamToken("valid-token")).thenReturn(false);
        when(inviteDao.getInviteByToken("valid-token", true)).thenReturn(Optional.of(invite));

        InviteTokenInspection result = service.inspectToken("valid-token");

        assertEquals(InviteType.TENANT.name(), result.type());
        assertEquals(invite.getExpiryDate(), result.expiresAt());
        verify(teamAccess, never()).accept("valid-token");
    }

    @Test
    void expiredInviteIsRejected() {
        Invite invite = new Invite();
        invite.setType(InviteType.TENANT.name());
        invite.setExpiryDate(LocalDateTime.now().minusMinutes(1));
        when(teamAccess.isTeamToken("expired-token")).thenReturn(false);
        when(inviteDao.getInviteByToken("expired-token", true)).thenReturn(Optional.of(invite));

        assertThrows(PMSCustomException.class, () -> service.inspectToken("expired-token"));
    }

    @Test
    void teamInviteInspectionNeverAcceptsMembership() {
        LocalDateTime expiry = LocalDateTime.now().plusDays(1);
        when(teamAccess.isTeamToken("team-token")).thenReturn(true);
        when(teamAccess.inspect("team-token")).thenReturn(new TeamAccessModels.InviteInspection(
                "te***@example.test", "Rental workspace", "Rental", "Manager", expiry,
                TeamMembershipStatus.PENDING));

        InviteTokenInspection result = service.inspectToken("team-token");

        assertEquals("TEAM", result.type());
        assertEquals(expiry, result.expiresAt());
        verify(teamAccess, never()).accept("team-token");
        verify(inviteDao, never()).getInviteByToken("team-token", true);
    }
}
