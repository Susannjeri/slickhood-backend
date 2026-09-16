package org.pms.silverocean.service.notification.whatsapp;

import org.pms.silverocean.service.config.ConfigService;
import org.pms.silverocean.service.config.enums.PMSConfigs;
import org.pms.silverocean.service.notification.NotificationDTO;
import org.pms.silverocean.service.notification.common.AbstractNotificationRetryService;
import org.pms.silverocean.service.notification.common.NotificationDao;
import org.pms.silverocean.service.notification.common.NotificationPoolConfigs;
import org.pms.silverocean.service.notification.preferences.NotificationCategory;
import org.pms.silverocean.service.notification.sms.whatsapp.WhatsAppService;
import org.pms.silverocean.service.threadpooling.ThreadPoolBeans;
import org.springframework.stereotype.Service;

@Service("WHATSAPP")
public class WhatsAppNotificationSender extends AbstractNotificationRetryService {
    private static final NotificationPoolConfigs CONFIGS = new NotificationPoolConfigs(
            "WHATSAPP-RETRY", PMSConfigs.SMS_THREAD_POOL_SIZE, PMSConfigs.SMS_RETRY_QUEUE_SIZE,
            PMSConfigs.SMS_RETRY_DELAY_IN_SECONDS, PMSConfigs.SMS_MAX_RETRIES);
    private final WhatsAppService whatsAppService;
    private final WhatsAppTemplateRegistry templates;

    public WhatsAppNotificationSender(NotificationDao notificationDao, ConfigService configService,
                                      ThreadPoolBeans threadPoolBeans, WhatsAppService whatsAppService,
                                      WhatsAppTemplateRegistry templates) {
        super(notificationDao, configService, threadPoolBeans);
        this.whatsAppService = whatsAppService; this.templates = templates;
    }

    @Override protected NotificationPoolConfigs getPoolConfigs() { return CONFIGS; }
    @Override protected boolean isRetryableStatusCode(int statusCode) { return false; }

    @Override protected int callProviderApi(NotificationDTO dto, long id) {
        if (dto.notificationType().name().contains("OTP"))
            throw new IllegalStateException("OTP must use SMS or email until an authentication template is approved");
        NotificationCategory category = NotificationCategory.fromEvent(dto.notificationType().name());
        var template = templates.approved(category)
                .orElseThrow(() -> new IllegalStateException("No approved WhatsApp template for " + category));
        whatsAppService.sendTemplateMessage(dto.recipient(), "SlickHood User", dto.formattedMessage(), id, template);
        // Meta acceptance is not delivery. The signed webhook marks the notification delivered.
        return 0;
    }
}
