package org.pms.silverocean.service.notification;

import lombok.RequiredArgsConstructor;
import org.pms.silverocean.database.pms.InviteRepo;
import org.pms.silverocean.database.pms.WorkspaceInvitationRepo;
import org.pms.silverocean.database.pms.entities.Notification;
import org.pms.silverocean.database.pms.entities.Users;
import org.pms.silverocean.service.teamaccess.TeamMembershipStatus;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.regex.Pattern;

/** Read-only recipient-bound action resolution; opening an inbox never consumes an invite. */
@Service @RequiredArgsConstructor
public class NotificationActionResolver {
    private final InviteRepo invites;
    private final WorkspaceInvitationRepo teamInvites;
    private static final Pattern TOKEN=Pattern.compile("(?:[?&]|&amp;)token=([A-Za-z0-9_-]{1,255})(?:[\\s&#<\"']|$)");
    public record Action(String path,String status) {}
    public Action resolve(Notification notification,String message,Users user) {
        if(!"IN_APP".equals(notification.getChannel()))return new Action(null,"NOT_ACTIONABLE");
        if(notification.getType()!=null&&notification.getType().toUpperCase(java.util.Locale.ROOT).contains("INVITE")){
            var match=TOKEN.matcher(message==null?"":message);
            if(!match.find())return new Action(null,"UNAVAILABLE");
            String token=match.group(1);
            var invite=invites.findByTokenAndActive(token,true).or(()->invites.findByTokenAndActive(token,false)).orElse(null);
            if(invite!=null){
                if(user.getEmail()==null||!user.getEmail().equalsIgnoreCase(invite.getRecipient()))return new Action(null,"UNAVAILABLE");
                if(!invite.isActive())return new Action(null,invite.getVisits()>0?"ACCEPTED":"CANCELLED");
                if(invite.getExpiryDate()==null||!invite.getExpiryDate().isAfter(LocalDateTime.now()))return new Action(null,"EXPIRED");
                return new Action("/lease/onboard?token="+token,"AVAILABLE");
            }
            var team=teamInvites.findByTokenHashAndActiveTrue(NotificationService.digest(token)).orElse(null);
            if(team==null||user.getEmail()==null||!user.getEmail().equalsIgnoreCase(team.getRecipientEmail()))return new Action(null,"UNAVAILABLE");
            if(team.getStatus()!=TeamMembershipStatus.PENDING)return new Action(null,team.getStatus()==null?"UNAVAILABLE":team.getStatus().name());
            if(team.getExpiresAt()==null||!team.getExpiresAt().isAfter(LocalDateTime.now()))return new Action(null,"EXPIRED");
            return new Action("/lease/onboard?token="+token,"AVAILABLE");
        }
        return BusinessNotificationService.safePath(notification.getActionPath())?new Action(notification.getActionPath(),"AVAILABLE"):new Action(null,"LEGACY");
    }
}
