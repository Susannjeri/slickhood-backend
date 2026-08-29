package org.pms.silverocean.service.notification;

import org.pms.silverocean.database.pms.entities.SMS;
import org.pms.silverocean.service.notification.common.NotificationDao;
import org.pms.silverocean.service.notification.sms.africastalking.wrappers.ATSMSDTO;
import org.pms.silverocean.service.notification.sms.SMSDao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class NotificationReportService {
    private final NotificationDao notificationDao;
    private final SMSDao smsDao;

    public NotificationReportService(NotificationDao notificationDao, SMSDao smsDao) {
        this.notificationDao = notificationDao;
        this.smsDao = smsDao;
    }

    public Page<NotificationProjection> getNotifications(Pageable pageable, String filter) {
        return notificationDao.getNotifications(pageable, filter);
    }


    public Page<ATSMSDTO> getSentSMS(Pageable pageable, Optional<Long> notificationId) {
        Page<SMS> smsPage = notificationId.isPresent() ? smsDao.findAllByNotificationId(pageable, notificationId.get()) : smsDao.findAll(pageable);

        return smsPage
                .map(smsFromDb -> new ATSMSDTO(smsFromDb.getId(), smsFromDb.getNotificationId(), smsFromDb.getStatus(), smsFromDb.getDescription(),
                        smsFromDb.getNetwork(), smsFromDb.getCost(), smsFromDb.getCurrency(), smsFromDb.getCallBackIP(),
                        smsFromDb.getCreatedOn().toLocalDateTime(), smsFromDb.getUpdatedOn()));
    }
}
