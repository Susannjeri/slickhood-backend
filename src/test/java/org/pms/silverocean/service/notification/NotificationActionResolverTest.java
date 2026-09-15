package org.pms.silverocean.service.notification;
import org.junit.jupiter.api.Test;
import org.pms.silverocean.database.pms.*;
import org.pms.silverocean.database.pms.entities.*;
import java.time.LocalDateTime;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NotificationActionResolverTest {
    InviteRepo invites=mock(InviteRepo.class);WorkspaceInvitationRepo teams=mock(WorkspaceInvitationRepo.class);
    NotificationActionResolver resolver=new NotificationActionResolver(invites,teams);
    Notification notification=new Notification();Users user=new Users();
    NotificationActionResolverTest(){notification.setChannel("IN_APP");notification.setType("INVITE_RECEIVED");user.setEmail("member@example.test");}
    Invite invite(){Invite i=new Invite();i.setRecipient(user.getEmail());i.setActive(true);i.setExpiryDate(LocalDateTime.now().plusDays(1));return i;}
    String message="Review https://app.slickhood.com/lease/onboard?token=safe-token";
    @Test void validInviteIsActionableWithoutConsumption(){Invite i=invite();when(invites.findByTokenAndActive("safe-token",true)).thenReturn(Optional.of(i));assertEquals("/lease/onboard?token=safe-token",resolver.resolve(notification,message,user).path());verify(invites,never()).save(any());}
    @Test void expiredInviteKeepsHistoryButHasNoAction(){Invite i=invite();i.setExpiryDate(LocalDateTime.now().minusSeconds(1));when(invites.findByTokenAndActive("safe-token",true)).thenReturn(Optional.of(i));var result=resolver.resolve(notification,message,user);assertEquals("EXPIRED",result.status());assertNull(result.path());}
    @Test void cancelledAndConsumedInvitesAreNotReplayed(){Invite i=invite();i.setActive(false);when(invites.findByTokenAndActive("safe-token",false)).thenReturn(Optional.of(i));assertEquals("CANCELLED",resolver.resolve(notification,message,user).status());i.setVisits(1);assertEquals("ACCEPTED",resolver.resolve(notification,message,user).status());assertNull(resolver.resolve(notification,message,user).path());}
    @Test void anotherRecipientsTokenCannotBecomeAnAction(){Invite i=invite();i.setRecipient("other@example.test");when(invites.findByTokenAndActive("safe-token",true)).thenReturn(Optional.of(i));assertNull(resolver.resolve(notification,message,user).path());}
    @Test void externalStructuredActionsAreRejected(){notification.setType("ORDER_UPDATE");notification.setActionPath("https://evil.example/path");assertNull(resolver.resolve(notification,"Updated",user).path());}
    @Test void trustedStructuredActionsAreReturned(){notification.setType("ORDER_UPDATE");notification.setActionPath("/dashboard/soko-deliveries");assertEquals("AVAILABLE",resolver.resolve(notification,"Updated",user).status());assertEquals(notification.getActionPath(),resolver.resolve(notification,"Updated",user).path());}
    @Test void expiredTeamInvitationIsNotConsumed(){WorkspaceInvitation i=new WorkspaceInvitation();i.setRecipientEmail(user.getEmail());i.setStatus(org.pms.silverocean.service.teamaccess.TeamMembershipStatus.PENDING);i.setExpiresAt(LocalDateTime.now().minusMinutes(1));when(teams.findByTokenHashAndActiveTrue(NotificationService.digest("safe-token"))).thenReturn(Optional.of(i));assertEquals("EXPIRED",resolver.resolve(notification,message,user).status());verify(teams,never()).save(any());}
}
