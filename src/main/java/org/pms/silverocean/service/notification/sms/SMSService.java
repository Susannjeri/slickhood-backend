package org.pms.silverocean.service.notification.sms;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.pms.silverocean.service.notification.common.NotificationDao;
import org.pms.silverocean.service.notification.sms.africastalking.wrappers.ATSMSFailureReason;
import org.pms.silverocean.service.notification.sms.africastalking.wrappers.ATSMSNetworkCode;
import org.pms.silverocean.service.notification.sms.africastalking.wrappers.ATSMSStatus;
import org.pms.silverocean.service.notification.sms.whatsapp.wrappers.WhatsAppStatus;
import org.pms.silverocean.service.notification.sms.whatsapp.wrappers.callback.WAChange;
import org.pms.silverocean.service.notification.sms.whatsapp.wrappers.callback.WAWebHook;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@Slf4j
public class SMSService {
    private final SMSDao smsDao;
    private final NotificationDao notificationDao;


    public SMSService(SMSDao smsDao, NotificationDao notificationDao) {
        this.smsDao = smsDao;
        this.notificationDao = notificationDao;
    }

    @Async
    @org.springframework.transaction.annotation.Transactional("pmsDBTransactionManager")
    public void receiveATCallback(String ip, String id, ATSMSStatus ATsmsStatus, String phoneNumber, ATSMSNetworkCode ATsmsNetworkCode,
                                  ATSMSFailureReason ATsmsFailureReason) {
        log.info("Received AT Callback ip:({}) id:({}) status:({}) phone number:({}) network:({}) failureReason({})",
                ip, id, ATsmsStatus, StringUtils.right(StringUtils.defaultString(phoneNumber), 4), ATsmsNetworkCode, ATsmsFailureReason);
        if (StringUtils.isBlank(id) || ATsmsStatus == null || ATsmsNetworkCode == null) return;

        String description=ATSMSStatus.Success.equals(ATsmsStatus)||ATsmsFailureReason==null?ATsmsStatus.getDescription():ATsmsFailureReason.name();
        smsDao.recordProviderReceipt("Africastalking",id,ATsmsStatus.name(),description,ATsmsNetworkCode.name(),ip)
                .ifPresent(notificationDao::confirmDelivered);
    }

    private void updateNotification(long notificationId, boolean delivered) {
        if (delivered) notificationDao.confirmDelivered(notificationId);
    }

    @org.springframework.transaction.annotation.Transactional("pmsDBTransactionManager")
    public void receiveWhatsAppCallback(WAWebHook waWebHook, String ip) {
        if (waWebHook == null || !"whatsapp_business_account".equals(waWebHook.object())) return;
        stream(waWebHook.entry()).flatMap(e -> stream(e.changes()))
                .filter(change -> "messages".equals(change.field()))
                .map(WAChange::value).filter(java.util.Objects::nonNull)
                .flatMap(value -> stream(value.statuses()))
                .filter(status -> status.id() != null && !status.id().isBlank() && rank(status.status()) > 0)
                .forEach(status -> smsDao.lockWhatsAppMessage(status.id()).ifPresent(sms -> {
                    // Serialize concurrent callbacks and never regress read/delivered to sent/failed.
                    if (rank(status.status()) <= rank(sms.getStatus())) return;
                    sms.setCallBackIP(ip);
                    sms.setStatus(status.status());
                    sms.setDescription("WhatsApp delivery status: " + status.status());
                    sms.setUpdatedOn(LocalDateTime.now());
                    smsDao.saveSMS(sms);
                    if ("delivered".equals(status.status()) || "read".equals(status.status())) {
                        updateNotification(sms.getNotificationId(), true);
                    }
                }));
    }

    private static <T> java.util.stream.Stream<T> stream(java.util.List<T> items) {
        return items == null ? java.util.stream.Stream.empty() : items.stream().filter(java.util.Objects::nonNull);
    }

    private static int rank(String status) {
        if (status == null) return 0;
        return switch (status) { case "sent" -> 1; case "failed" -> 2; case "delivered" -> 3; case "read" -> 4; default -> 0; };
    }

}
