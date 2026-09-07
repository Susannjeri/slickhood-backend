package org.pms.silverocean.service.notification.sms;

import org.junit.jupiter.api.Test;
import org.pms.silverocean.database.pms.entities.Notification;
import org.pms.silverocean.database.pms.entities.SMS;
import org.pms.silverocean.service.notification.common.NotificationDao;
import org.pms.silverocean.service.notification.sms.whatsapp.wrappers.callback.*;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WhatsAppCallbackTest {
    final SMSDao messages = mock(SMSDao.class);
    final NotificationDao notifications = mock(NotificationDao.class);
    final SMSService service = new SMSService(messages, notifications);

    @Test void processesEveryStatusWithoutPricingAndTreatsReadAsDelivered() {
        SMS first = message(1), second = message(2);
        when(messages.lockWhatsAppMessage("a")).thenReturn(Optional.of(first));
        when(messages.lockWhatsAppMessage("b")).thenReturn(Optional.of(second));
        Notification one = new Notification(), two = new Notification();
        when(notifications.findById(1)).thenReturn(Optional.of(one));
        when(notifications.findById(2)).thenReturn(Optional.of(two));
        service.receiveWhatsAppCallback(payload(status("a", "delivered"), status("b", "read")), "127.0.0.1");
        assertTrue(one.isDelivered()); assertTrue(two.isDelivered());
        assertEquals("read", second.getStatus());
        verify(messages, times(2)).saveSMS(any());
    }

    @Test void duplicateAndOutOfOrderCallbacksCannotRegressDelivery() {
        SMS stored = message(1); stored.setStatus("read");
        when(messages.lockWhatsAppMessage("a")).thenReturn(Optional.of(stored));
        service.receiveWhatsAppCallback(payload(status("a", "sent"), status("a", "failed"), status("a", "read")), "127.0.0.1");
        assertEquals("read", stored.getStatus());
        verify(messages, never()).saveSMS(any()); verifyNoInteractions(notifications);
    }

    @Test void inboundMessagesMissingStatusesAndEmptyPayloadsAreSafe() {
        service.receiveWhatsAppCallback(null, "127.0.0.1");
        service.receiveWhatsAppCallback(new WAWebHook("whatsapp_business_account", null), "127.0.0.1");
        service.receiveWhatsAppCallback(new WAWebHook("whatsapp_business_account", List.of(new WAEntry("id",
                List.of(new WAChange(new WAValue("whatsapp", null, null), "messages"))))), "127.0.0.1");
        verifyNoInteractions(messages, notifications);
    }

    @Test void unknownMessageIdsDoNotChangeNotifications() {
        when(messages.lockWhatsAppMessage("unknown")).thenReturn(Optional.empty());
        service.receiveWhatsAppCallback(payload(status("unknown", "delivered")), "127.0.0.1");
        verifyNoInteractions(notifications);
    }
    private SMS message(long notification) { SMS s = new SMS(); s.setNotificationId(notification); s.setStatus("accepted"); return s; }
    private WAStatus status(String id, String status) { return new WAStatus(id, status, "123", "254700000000", null); }
    private WAWebHook payload(WAStatus... statuses) { return new WAWebHook("whatsapp_business_account", List.of(
            new WAEntry("account", List.of(new WAChange(new WAValue("whatsapp", null, List.of(statuses)), "messages"))))); }
}
