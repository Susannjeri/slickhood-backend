package org.pms.silverocean.service.notification.sms;

import org.junit.jupiter.api.Test;
import org.pms.silverocean.database.pms.entities.SMS;
import org.pms.silverocean.service.notification.common.NotificationDao;
import org.pms.silverocean.service.notification.sms.africastalking.wrappers.ATSMSFailureReason;
import org.pms.silverocean.service.notification.sms.africastalking.wrappers.ATSMSNetworkCode;
import org.pms.silverocean.service.notification.sms.africastalking.wrappers.ATSMSStatus;

import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SMSServiceTest {
    @Test
    void terminalAfricaTalkingFailureMarksNotificationFailed() {
        SMSDao smsDao = mock(SMSDao.class);
        NotificationDao notifications = mock(NotificationDao.class);
        SMS receipt = new SMS();
        receipt.setNotificationId(51L);
        when(smsDao.findByThirdPartyId("provider-id")).thenReturn(Optional.of(receipt));
        when(smsDao.recordProviderReceipt("Africastalking", "provider-id", "Failed",
                "DeliveryFailure", "SAFARICOM_KENYA", "127.0.0.1")).thenReturn(Optional.empty());

        new SMSService(smsDao, notifications).receiveATCallback("127.0.0.1", "provider-id",
                ATSMSStatus.Failed, "+254700000000", ATSMSNetworkCode.SAFARICOM_KENYA,
                ATSMSFailureReason.DeliveryFailure);

        verify(notifications).markDeliveryFailed(51L);
        verify(notifications, never()).confirmDelivered(51L);
    }

    @Test
    void successfulAfricaTalkingReceiptConfirmsDelivery() {
        SMSDao smsDao = mock(SMSDao.class);
        NotificationDao notifications = mock(NotificationDao.class);
        SMS receipt = new SMS();
        receipt.setNotificationId(52L);
        when(smsDao.findByThirdPartyId("provider-id")).thenReturn(Optional.of(receipt));
        when(smsDao.recordProviderReceipt("Africastalking", "provider-id", "Success",
                ATSMSStatus.Success.getDescription(), "SAFARICOM_KENYA", "127.0.0.1"))
                .thenReturn(Optional.of(52L));

        new SMSService(smsDao, notifications).receiveATCallback("127.0.0.1", "provider-id",
                ATSMSStatus.Success, "+254700000000", ATSMSNetworkCode.SAFARICOM_KENYA, null);

        verify(notifications).confirmDelivered(52L);
        verify(notifications, never()).markDeliveryFailed(52L);
    }
}
