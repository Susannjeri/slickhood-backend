package org.pms.silverocean.service.notification.sms;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.pms.silverocean.service.config.ConfigService;
import org.pms.silverocean.service.config.enums.PMSConfigs;
import org.pms.silverocean.service.notification.NotificationDTO;
import org.pms.silverocean.service.notification.common.AbstractNotificationRetryService;
import org.pms.silverocean.service.notification.common.NotificationDao;
import org.pms.silverocean.service.notification.common.NotificationPoolConfigs;
import org.pms.silverocean.service.threadpooling.ThreadPoolBeans;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service("SMS")
@Primary
@Slf4j
public class SmsDispatcherService extends AbstractNotificationRetryService {
    private final Set<SmsProvider> providers;
    private SmsProvider provider;

    private static final NotificationPoolConfigs CONFIGS = new NotificationPoolConfigs(
            "SMS-RETRY",
            PMSConfigs.SMS_THREAD_POOL_SIZE,
            PMSConfigs.SMS_RETRY_QUEUE_SIZE,
            PMSConfigs.SMS_RETRY_DELAY_IN_SECONDS,
            PMSConfigs.SMS_MAX_RETRIES
    );

    public SmsDispatcherService(List<SmsProvider> providers, ConfigService configService, NotificationDao notificationDao, ThreadPoolBeans threadPoolBeans) {
        super(notificationDao, configService, threadPoolBeans);
        this.providers = new HashSet<>();
        this.providers.addAll(providers);
    }

    @PostConstruct
    public void init() {
        super.init();
    }

    @Override
    protected boolean isRetryableStatusCode(int statusCode) {
        getActiveProvider();
        return provider.isRetryable(statusCode);
    }

    @Override
    protected int callProviderApi(NotificationDTO dto, long notificationId) throws Exception {
        getActiveProvider();
        return provider.executeSend(dto, notificationId);
    }

    @Override
    protected NotificationPoolConfigs getPoolConfigs() {
        return CONFIGS;
    }

    private void getActiveProvider() {
        String providerName = configService.getConfigByName(PMSConfigs.ACTIVE_SMS_PROVIDER).get().stringValue();
        provider = providers.stream()
                .filter(p ->  p.supports(providerName))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No provider found for: " + providerName));
    }
}
