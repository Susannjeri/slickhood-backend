package org.pms.silverocean.service.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.pms.silverocean.database.pms.DomainEventOutboxRepo;
import org.pms.silverocean.database.pms.entities.DomainEventOutbox;
import org.pms.silverocean.database.pms.entities.Users;
import org.pms.silverocean.service.architecture.events.DomainEventOutboxPublisher;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.notification.common.NotificationType;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class BusinessNotificationServiceTest {
    DomainEventOutboxPublisher publisher=mock(DomainEventOutboxPublisher.class);
    DomainEventOutboxRepo events=mock(DomainEventOutboxRepo.class);
    UserDao users=mock(UserDao.class);
    NotificationService notifications=mock(NotificationService.class);
    ObjectMapper mapper=new ObjectMapper();
    BusinessNotificationService service=new BusinessNotificationService(publisher,events,mapper,users,notifications);
    @Test void destinationsCannotBeExternalOrExecutable(){
        for(String path:new String[]{"https://maps.google.com/secret","//evil.example/path","/dashboard/../admin","/dashboard?x=%0A","javascript:alert(1)"})
            assertThrows(IllegalArgumentException.class,()->service.publish(9,"order:7:dispatched","SOKO_ORDER_STATUS","Order updated",path));
        verifyNoInteractions(publisher);
    }
    @Test void codesAndChallengesNeverBecomeBusinessAlerts(){
        for(String type:new String[]{"EMAIL_OTP","SOKO_DELIVERY_CODE_EMAIL","SOKO_DELIVERY_RECOVERY_EMAIL"})
            assertThrows(IllegalArgumentException.class,()->service.publish(9,"order:7",type,"Private challenge","/dashboard/soko"));
        verifyNoInteractions(publisher);
    }
    @Test void publishingUsesStableRecipientSpecificDedupeKeys(){
        service.publish(9,"order:7:dispatched","SOKO_ORDER_STATUS","Order updated","/dashboard/soko");
        service.publish(9,"order:7:dispatched","SOKO_ORDER_STATUS","Order updated","/dashboard/soko");
        verify(publisher,times(2)).publish(eq(BusinessNotificationService.TYPE),eq("BUSINESS_NOTIFICATION"),eq("9"),eq("business-alert:"+NotificationService.digest("order:7:dispatched:9")),any());
        verifyNoInteractions(notifications);
    }
    @Test void replayReusesDurableChannelDeliveryKeys()throws Exception{
        var alert=new BusinessNotificationService.Alert(9,"stable-event","SOKO_ORDER_STATUS","Order updated","/dashboard/soko");
        DomainEventOutbox event=new DomainEventOutbox();event.setId(17L);event.setPayload(mapper.writeValueAsString(alert));
        Users user=new Users();user.setId(9L);user.setActive(true);user.setEmail("buyer@example.test");
        when(events.lockForNotification(17L)).thenReturn(Optional.of(event));when(users.findById(9L)).thenReturn(Optional.of(user));
        service.handle(event);service.handle(event);
        verify(events,times(2)).lockForNotification(17L);
        verify(notifications,times(2)).queueEmailAndInAppOnce(eq("stable-event"),eq("buyer@example.test"),eq(NotificationType.BUSINESS_ALERT_EMAIL),anyString(),eq("SOKO_ORDER_STATUS"),eq("Order updated Open /dashboard/soko to review it securely."),eq("/dashboard/soko"));
    }
    @Test void deactivatedRecipientIsNotNotified()throws Exception{
        DomainEventOutbox event=new DomainEventOutbox();event.setId(1L);event.setPayload(mapper.writeValueAsString(new BusinessNotificationService.Alert(9,"key","SOKO_ORDER_STATUS","Updated","/dashboard/soko")));
        when(events.lockForNotification(1L)).thenReturn(Optional.of(event));when(users.findById(9L)).thenReturn(Optional.empty());service.handle(event);verifyNoInteractions(notifications);
    }
}
