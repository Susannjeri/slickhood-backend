package org.pms.silverocean.service.notification.common;

import org.pms.silverocean.service.config.enums.PMSConfigs;

public record NotificationPoolConfigs(
        String poolName,
        PMSConfigs threadPoolSizeConfig,
        PMSConfigs retryQueueSizeConfig,
        PMSConfigs retryDelayConfig,
        PMSConfigs maxRetriesConfig
) {
}
