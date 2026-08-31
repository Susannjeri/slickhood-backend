package org.pms.silverocean.service.notification.common;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.pms.silverocean.service.config.ConfigService;
import org.pms.silverocean.service.notification.NotificationDTO;
import org.pms.silverocean.service.notification.NotificationSender;
import org.pms.silverocean.service.threadpooling.PMSThreadPoolExecutorService;
import org.pms.silverocean.service.threadpooling.ThreadPoolBeans;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Slf4j
public abstract class AbstractNotificationRetryService  implements NotificationSender {
    protected final NotificationDao notificationDao;
    protected final ConfigService configService;
    protected final ThreadPoolBeans threadPoolBeans;

    private PMSThreadPoolExecutorService retryFailedPool;
    private PMSThreadPoolExecutorService senderPool;

    protected int retryDelayInSeconds;
    protected int maxRetries;

    protected AbstractNotificationRetryService(NotificationDao notificationDao, ConfigService configService, ThreadPoolBeans threadPoolBeans) {
        this.notificationDao = notificationDao;
        this.configService = configService;
        this.threadPoolBeans = threadPoolBeans;
    }

    /**
     * Implementations must provide their specific configuration keys using this method.
     */
    protected abstract NotificationPoolConfigs getPoolConfigs();

    @PostConstruct
    public void init() {
        NotificationPoolConfigs configs = getPoolConfigs();
        this.retryDelayInSeconds = configService.getConfigByName(configs.retryDelayConfig()).get().intValue();
        this.maxRetries = configService.getConfigByName(configs.maxRetriesConfig()).get().intValue();
        this.retryFailedPool = threadPoolBeans.ioExecutorService(configs.poolName());
        this.senderPool = threadPoolBeans.ioExecutorService("SEND-NOTIFICATION-");
    }

    public void send(NotificationDTO notificationDTO, long notificationId) {
        senderPool.submit(() -> {
                    try {
                        // This is the bridge between checked Exception and RuntimeException
                        return callProviderApi(notificationDTO, notificationId);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .whenComplete((statusCode, throwable) -> {
                    if (throwable != null) {
                        // Use getCause() because the original Exception is wrapped in a RuntimeException/CompletionException
                        Throwable actualError = throwable.getCause() != null ? throwable.getCause() : throwable;
                        log.error("Provider API failed for notification {}: {}", notificationId, actualError.getMessage());

                        triggerRetryIfAllowed(notificationDTO, notificationId);
                    } else if (isRetryableStatusCode(statusCode)) {
                        log.warn("Retryable status code {} for notification {}", statusCode, notificationId);
                        triggerRetryIfAllowed(notificationDTO, notificationId);
                    }
                });
    }

    @Override
    public void retry(NotificationDTO notificationDTO, long notificationId) {
        scheduledRetryOperation(notificationDTO, notificationId);
    }

    @Override public int retryDelaySeconds() { return retryDelayInSeconds; }
    @Override public int maxRetries() { return maxRetries; }

    private void triggerRetryIfAllowed(NotificationDTO dto, long id) {
        if (dto.notificationType().isRetry()) {
            notificationDao.findById(id).ifPresent(notification -> {
                if (notification.getRetries() >= maxRetries) {
                    notificationDao.stopRetry(id);
                    log.error("Notification {} exhausted its {} delivery retries", id, maxRetries);
                } else {
                    retryFailedPool.schedule(() -> scheduledRetryOperation(dto, id),
                            retryDelayInSeconds, TimeUnit.SECONDS);
                }
            });
        }
    }

    /**
     * Common logic for scheduled retry operation. Checks retry limits and schedules
     * a new send attempt using the abstract send method.
     */
    protected void scheduledRetryOperation(NotificationDTO notificationDTO, long notificationId) {
        LocalDateTime now = LocalDateTime.now();
        if (notificationDao.claimRetry(notificationId, now.minusSeconds(retryDelayInSeconds), now, maxRetries)) {
            notificationDao.findById(notificationId).ifPresent(notification -> {
                NotificationPoolConfigs configs = getPoolConfigs();
                log.info("Retrying {} for notification {}", configs.poolName(), notificationId);
                send(notificationDTO, notification.getId());
            });
        }
    }

    protected abstract boolean isRetryableStatusCode(int statusCode);

    protected abstract int callProviderApi(NotificationDTO dto, long id) throws Exception;
}
