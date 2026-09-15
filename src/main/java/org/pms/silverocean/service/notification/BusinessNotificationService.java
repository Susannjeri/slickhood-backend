package org.pms.silverocean.service.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.pms.silverocean.database.pms.DomainEventOutboxRepo;
import org.pms.silverocean.database.pms.entities.DomainEventOutbox;
import org.pms.silverocean.service.architecture.events.DomainEventHandler;
import org.pms.silverocean.service.architecture.events.DomainEventOutboxPublisher;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.notification.common.NotificationType;
import org.pms.silverocean.service.notification.common.NotificationVisibility;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Durable generic alerts: callers supply only non-sensitive event descriptions. */
@Service @RequiredArgsConstructor
public class BusinessNotificationService implements DomainEventHandler {
    public static final String TYPE = "BUSINESS_NOTIFICATION_REQUESTED";
    private final DomainEventOutboxPublisher outbox;
    private final DomainEventOutboxRepo events;
    private final ObjectMapper mapper;
    private final UserDao users;
    private final NotificationService notifications;
    public record Alert(long userId,String key,String type,String message,String actionPath) {}

    @Transactional("pmsDBTransactionManager")
    public void publish(long userId,String key,String type,String message,String actionPath) {
        if(userId<=0||key==null||key.isBlank()||!NotificationVisibility.personal(type)||message==null||message.isBlank())
            throw new IllegalArgumentException("A recipient and non-sensitive business event are required");
        if(!safePath(actionPath))throw new IllegalArgumentException("Business alerts require a safe internal destination");
        String eventKey=NotificationService.digest(key+":"+userId);
        outbox.publish(TYPE,"BUSINESS_NOTIFICATION",Long.toString(userId),"business-alert:"+eventKey,new Alert(userId,eventKey,type,message,actionPath));
    }
    @Override public String eventType(){return TYPE;}
    @Override @Transactional("pmsDBTransactionManager")
    public void handle(DomainEventOutbox event)throws Exception {
        // Serializes a replay with its original handler. Unique delivery keys also
        // protect the commit-to-processed gap after an application restart.
        var locked=events.lockForNotification(event.getId()).orElseThrow();
        Alert alert=mapper.readValue(locked.getPayload(),Alert.class);
        if(alert.userId()<=0||alert.key()==null||alert.key().isBlank()||alert.message()==null||alert.message().isBlank()||!safePath(alert.actionPath())||!NotificationVisibility.personal(alert.type()))throw new IllegalArgumentException("Invalid business alert");
        var user=users.findById(alert.userId()).filter(u->u.isActive()&&u.getEmail()!=null&&!u.getEmail().isBlank()).orElse(null);
        if(user==null)return;
        String text=alert.message()+" Open "+alert.actionPath()+" to review it securely.";
        String html="<p>"+org.springframework.web.util.HtmlUtils.htmlEscape(text)+"</p>";
        notifications.queueEmailAndInAppOnce(alert.key(),user.getEmail(),NotificationType.BUSINESS_ALERT_EMAIL,html,alert.type(),text,alert.actionPath());
    }
    public static boolean safePath(String path){
        return path!=null&&path.length()<=500&&path.matches("/dashboard(?:/[A-Za-z0-9_-]+)*(?:\\?[A-Za-z0-9_=&.-]+)?(?:#[A-Za-z0-9_-]+)?");
    }
}
