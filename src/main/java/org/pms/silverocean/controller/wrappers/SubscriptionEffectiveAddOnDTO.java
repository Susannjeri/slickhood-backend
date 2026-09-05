package org.pms.silverocean.controller.wrappers;

import java.time.ZonedDateTime;
import java.util.List;

public record SubscriptionEffectiveAddOnDTO(
        String productKey,
        String planCode,
        ZonedDateTime endAt,
        List<String> features
) {
}
