package org.pms.silverocean.service.notification;

import org.pms.silverocean.database.pms.entities.SMS;
import org.pms.silverocean.database.pms.entities.Users;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.notification.common.NotificationDao;
import org.pms.silverocean.service.notification.sms.africastalking.wrappers.ATSMSDTO;
import org.pms.silverocean.service.notification.sms.SMSDao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.pms.silverocean.service.security.DecryptDTO;
import org.pms.silverocean.service.security.EncryptionService;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

@Service
public class NotificationReportService {
    private final NotificationDao notificationDao;
    private final SMSDao smsDao;
    private final UserDao userDao;
    private final EncryptionService encryptionService;

    public NotificationReportService(NotificationDao notificationDao, SMSDao smsDao, UserDao userDao,
                                     EncryptionService encryptionService) {
        this.notificationDao = notificationDao;
        this.smsDao = smsDao;
        this.userDao = userDao;
        this.encryptionService = encryptionService;
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

    public Page<MyNotificationDTO> getMyNotifications(Pageable pageable) {
        Users user = userDao.getUserObject();
        if (user == null) {
            return Page.empty(pageable);
        }
        Set<String> recipients = new LinkedHashSet<>();
        addRecipientVariants(recipients, user.getEmail());
        addRecipientVariants(recipients, user.getPhoneNumber());
        return notificationDao.getNotificationsForRecipients(pageable, recipients).map(notification -> {
            DecryptDTO decrypted = encryptionService.decrypt(notification.getMessage());
            return new MyNotificationDTO(notification.getId(), notification.getChannel(), notification.getType(),
                    decrypted == null ? "" : decrypted.decryptedValue(), notification.isDelivered(),
                    notification.getCreatedOn(), notification.getUpdatedOn());
        });
    }

    private void addRecipientVariants(Set<String> recipients, String recipient) {
        if (recipient == null || recipient.isBlank()) return;
        String trimmed = recipient.trim();
        recipients.add(trimmed);
        recipients.add(trimmed.toLowerCase(java.util.Locale.ROOT));
        if (trimmed.startsWith("+")) recipients.add(trimmed.substring(1));
        else if (trimmed.chars().allMatch(Character::isDigit)) recipients.add("+" + trimmed);
    }
}
