package org.pms.silverocean.service.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.pms.silverocean.database.pms.entities.Notification;
import org.pms.silverocean.database.pms.entities.Users;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.notification.common.NotificationChannel;
import org.pms.silverocean.service.notification.common.NotificationDao;
import org.pms.silverocean.service.notification.common.NotificationType;
import org.pms.silverocean.service.notification.common.NotificationVisibility;
import org.pms.silverocean.service.notification.preferences.NotificationCategory;
import org.pms.silverocean.service.notification.preferences.NotificationPreferenceService;
import org.pms.silverocean.service.security.EncryptionService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;
import java.time.LocalDateTime;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {
    private final EncryptionService encryptionService;
    private final NotificationDao notificationDao;
    private final UserDao userDao;
    private final Map<String, NotificationSender> senders;
    private final ApplicationEventPublisher events;
    private final NotificationPreferenceService notificationPreferenceService;
    @org.springframework.transaction.annotation.Transactional("pmsDBTransactionManager")
    public void sendNotification(NotificationDTO notificationDTO) {
        queueNotification(notificationDTO);
    }

    @org.springframework.transaction.annotation.Transactional("pmsDBTransactionManager")
    public long queueNotification(NotificationDTO notificationDTO) {
        long notificationId = createNotification(notificationDTO.recipient(), notificationDTO.formattedMessage(), notificationDTO.notificationType());
        events.publishEvent(new NotificationQueued(notificationId, notificationDTO));
        return notificationId;
    }

    public String awaitProviderDecision(long notificationId, long timeoutMillis) {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(Math.max(0, timeoutMillis));
        do {
            Notification notification = notificationDao.findById(notificationId).orElse(null);
            if (notification == null || notification.getFailedAt() != null || "FAILED".equals(notification.getProviderStatus())) return "FAILED";
            if (notification.isDelivered() || "DELIVERED".equals(notification.getProviderStatus())) return "DELIVERED";
            if ("ACCEPTED".equals(notification.getProviderStatus())) return "ACCEPTED";
            if (System.nanoTime() >= deadline) return "QUEUED";
            try {
                Thread.sleep(50);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return "QUEUED";
            }
        } while (true);
    }

    /**
     * Stores an actionable notification for an existing active SlickHood user without
     * invoking an external delivery provider. The email-bound recipient is checked
     * here so callers cannot accidentally create account-visible alerts for arbitrary
     * addresses that do not belong to an active account.
     */
    @org.springframework.transaction.annotation.Transactional("pmsDBTransactionManager")
    public boolean queueInAppNotificationForExistingUser(String recipient, String type, String message) {
        if (recipient == null || recipient.isBlank() || !NotificationVisibility.personal(type)
                || message == null || message.isBlank()) {
            return false;
        }
        String normalizedRecipient = recipient.trim().toLowerCase(Locale.ROOT);
        if (userDao.findByEmail(normalizedRecipient).filter(Users::isActive).isEmpty()) {
            return false;
        }

        Notification notification = new Notification();
        notification.setType(type);
        notification.setRecipient(normalizedRecipient);
        notification.setMessage(encryptionService.encrypt(message));
        notification.setChannel("IN_APP");
        notification.setDelivered(true);
        notification.setRetry(false);
        notification.setRetries(0);
        notification.setUpdatedOn(LocalDateTime.now());
        notification.setActive(true);
        notificationDao.save(notification);
        return true;
    }

    /**
     * Queues the customer email and mirrors the same business event into the
     * authenticated notification centre when the recipient already has an
     * active SlickHood account. External delivery and the in-app record remain
     * separate evidence: an accepted email is not represented as an in-app
     * delivery, and a missing account does not prevent the email invitation or
     * lifecycle notice from being queued.
     */
    @org.springframework.transaction.annotation.Transactional("pmsDBTransactionManager")
    public void queueEmailAndInApp(String recipient, NotificationType emailType,
                                   String emailMessage, String inAppType, String inAppMessage) {
        if (recipient == null || recipient.isBlank()) return;
        queueEmailAndInAppOnce(java.util.UUID.randomUUID().toString(),recipient,emailType,emailMessage,inAppType,inAppMessage,null);
    }

    @org.springframework.transaction.annotation.Transactional("pmsDBTransactionManager")
    public void queueEmailAndInAppOnce(String key,String recipient,NotificationType emailType,
                                       String emailMessage,String inAppType,String inAppMessage,String actionPath) {
        if(recipient==null||recipient.isBlank())return;
        if(key==null||key.isBlank()||emailType==null||emailMessage==null||emailMessage.isBlank()||inAppMessage==null||inAppMessage.isBlank()||!NotificationVisibility.personal(emailType.name())||!NotificationVisibility.personal(inAppType))
            throw new IllegalArgumentException("Verification challenges cannot be mirrored as business alerts");
        if(actionPath!=null&&!BusinessNotificationService.safePath(actionPath))throw new IllegalArgumentException("Unsafe notification action");
        String normalized=recipient.trim().toLowerCase(Locale.ROOT),eventKey=digest(key+":"+normalized);
        String emailKey=digest(eventKey+":EMAIL"),smsKey=digest(eventKey+":SMS"),
                whatsappKey=digest(eventKey+":WHATSAPP"),appKey=digest(eventKey+":IN_APP");
        java.util.Optional<Users> account=userDao.findByEmail(normalized).filter(Users::isActive);
        NotificationCategory category=NotificationCategory.fromEvent(inAppType+":"+emailType.name());
        var plan=account.map(user->notificationPreferenceService.plan(user,category)).orElse(null);
        if((plan==null||plan.email())&&!notificationDao.hasDeliveryKey(emailKey)){
            NotificationDTO dto=new NotificationDTO(emailMessage,normalized,emailType);
            long id=createNotification(normalized,emailMessage,emailType,eventKey,emailKey,actionPath);
            events.publishEvent(new NotificationQueued(id,dto));
        }
        if(account.isPresent()&&plan!=null&&plan.sms()&&!notificationDao.hasDeliveryKey(smsKey)){
            NotificationType smsType=smsType(category);
            NotificationDTO dto=new NotificationDTO(inAppMessage,account.get().getPhoneNumber(),smsType);
            long id=createNotification(account.get().getPhoneNumber(),inAppMessage,smsType,eventKey,smsKey,actionPath);
            events.publishEvent(new NotificationQueued(id,dto));
        }
        if(account.isPresent()&&plan!=null&&plan.whatsapp()&&!notificationDao.hasDeliveryKey(whatsappKey)){
            NotificationType whatsappType=whatsappType(category);
            NotificationDTO dto=new NotificationDTO(inAppMessage,account.get().getPhoneNumber(),whatsappType);
            long id=createNotification(account.get().getPhoneNumber(),inAppMessage,whatsappType,eventKey,whatsappKey,actionPath);
            events.publishEvent(new NotificationQueued(id,dto));
        }
        if(!notificationDao.hasDeliveryKey(appKey)&&account.isPresent()){
            Notification n=new Notification();n.setType(inAppType);n.setRecipient(normalized);n.setMessage(encryptionService.encrypt(inAppMessage));
            n.setChannel("IN_APP");n.setDelivered(true);n.setRetry(false);n.setActive(true);n.setUpdatedOn(LocalDateTime.now());
            n.setBusinessEventKey(eventKey);n.setDeliveryKey(appKey);n.setActionPath(actionPath);notificationDao.save(n);
        }
    }

    private static NotificationType smsType(NotificationCategory category){return switch(category){
        case BILLING->NotificationType.BILLING_ALERT_SMS; case PROPERTY->NotificationType.PROPERTY_ALERT_SMS;
        case MARKETPLACE_DELIVERY->NotificationType.MARKETPLACE_DELIVERY_ALERT_SMS;
        case SECURITY->NotificationType.SECURITY_ALERT_SMS; case MARKETING->NotificationType.MARKETING_ALERT_SMS;};}
    private static NotificationType whatsappType(NotificationCategory category){return switch(category){
        case BILLING->NotificationType.BILLING_ALERT_WHATSAPP; case PROPERTY->NotificationType.PROPERTY_ALERT_WHATSAPP;
        case MARKETPLACE_DELIVERY->NotificationType.MARKETPLACE_DELIVERY_ALERT_WHATSAPP;
        case SECURITY->NotificationType.SECURITY_ALERT_WHATSAPP; case MARKETING->NotificationType.MARKETING_ALERT_WHATSAPP;};}

    public static String digest(String value) {
        try{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void deliverAfterCommit(NotificationQueued queued) {
        try {
            NotificationSender sender = getPlatform(queued.notification().notificationType().getChannel());
            sender.send(queued.notification(), queued.notificationId());
        } catch (RuntimeException routingFailure) {
            notificationDao.markDeliveryFailed(queued.notificationId());
            log.error("Notification {} could not be routed to channel {} ({})",
                    queued.notificationId(), queued.notification().notificationType().getChannel(),
                    routingFailure.getClass().getSimpleName());
        }
    }

    public void sendEmailToSuperAdmin(NotificationType notificationType, String formattedMessage) {
        if (NotificationChannel.EMAIL.equals(notificationType.getChannel())) {
            // Staff membership can change without restarting the application.
            // Resolve active recipients at dispatch time so escalation never uses a stale startup cache.
            userDao.findActiveSuperAdminAccounts().stream().map(Users::getEmail)
                    .forEach(email -> sendNotification(new NotificationDTO(formattedMessage, email, notificationType)));
        }
    }

    private long createNotification(String recipient, String message, NotificationType notificationType) {
        return createNotification(recipient,message,notificationType,null,null,null);
    }

    private long createNotification(String recipient,String message,NotificationType notificationType,String eventKey,String deliveryKey,String actionPath) {
        Notification notification = new Notification();
        notification.setType(notificationType.name());
        notification.setRecipient(recipient);
        notification.setMessage(encryptionService.encrypt(message));
        notification.setChannel(notificationType.getChannel().name());
        notification.setRetry(notificationType.isRetry());
        notification.setRetries(0);
        notification.setUpdatedOn(LocalDateTime.now());
        notification.setActive(true);
        notification.setBusinessEventKey(eventKey);
        notification.setDeliveryKey(deliveryKey);
        notification.setActionPath(actionPath);


        notificationDao.save(notification);
        return notification.getId();
    }

    private NotificationSender getPlatform(NotificationChannel channel) {
        NotificationSender sender = senders.get(channel.name());
        if (sender == null) {
            throw new IllegalArgumentException("Unsupported notification channel type: " + channel.name());
        }
        return sender;
    }

    public record NotificationQueued(long notificationId, NotificationDTO notification) {}
}
