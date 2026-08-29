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
    public void receiveATCallback(String ip, String id, ATSMSStatus ATsmsStatus, String phoneNumber, ATSMSNetworkCode ATsmsNetworkCode,
                                  ATSMSFailureReason ATsmsFailureReason) {
        log.info("Received AT Callback ip:({}) id:({}) status:({}) phone number:({}) network:({}) failureReason({})",
                ip, id, ATsmsStatus, StringUtils.isNotBlank(phoneNumber) ? phoneNumber.substring(phoneNumber.length() - 4) : "", ATsmsNetworkCode, ATsmsFailureReason);

        smsDao.findByThirdPartyId(id)
                .ifPresent(smsEntity -> {
                    smsEntity.setCallBackIP(ip);
                    smsEntity.setStatus(ATsmsStatus.name());
                    smsEntity.setDescription(ATsmsStatus.getDescription());
                    smsEntity.setNetwork(ATsmsNetworkCode.name());
                    smsEntity.setUpdatedOn(LocalDateTime.now());
                    if (ATsmsFailureReason != null) {
                        smsEntity.setDescription(ATsmsFailureReason.name());
                    }
                    smsDao.saveSMS(smsEntity);
                    updateNotification(smsEntity.getNotificationId(), ATSMSStatus.Success.equals(ATsmsStatus));
                });
    }

    private void updateNotification(long notificationId, boolean delivered) {
        notificationDao.findById(notificationId)
                .ifPresent(notification -> {
                    notification.setUpdatedOn(LocalDateTime.now());
                    notification.setDelivered(delivered);
                    notificationDao.save(notification);
                });
    }

    public void receiveWhatsAppCallback(WAWebHook waWebHook, String ip) {
        log.info("Processing whatsApp callback {} ", waWebHook);
        waWebHook.entry().stream()
                .flatMap(e -> e.changes().stream())
                .map(WAChange::value)
                .flatMap(v -> v.statuses().stream())
                .findFirst().ifPresent(waStatus -> smsDao.findByThirdPartyId(waStatus.id())
                        .ifPresent(smsEntity -> {
                            smsEntity.setCallBackIP(ip);
                            smsEntity.setStatus(waStatus.status());
                            smsEntity.setDescription(String.format("Billable: %s Category %s", waStatus.pricing().billable(), waStatus.pricing().category()));
                            smsEntity.setUpdatedOn(LocalDateTime.now());
                            smsDao.saveSMS(smsEntity);
                            if (WhatsAppStatus.DELIVERED.getName().equals(waStatus.status())) {
                                updateNotification(smsEntity.getNotificationId(), true);
                            }
                        }));
    }

}
