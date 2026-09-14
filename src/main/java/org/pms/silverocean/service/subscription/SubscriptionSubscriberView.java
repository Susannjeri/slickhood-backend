package org.pms.silverocean.service.subscription;

import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.pms.silverocean.service.subscription.enums.SubscriptionProduct;
import org.pms.silverocean.service.subscription.enums.SubscriptionStatus;

import java.time.ZonedDateTime;

public record SubscriptionSubscriberView(long subscriptionId, long userId,
                                         String fullName, String email, String phoneNumber,
                                         String accountStatus, PMSRole role, SubscriptionProduct product,
                                         String planCode, SubscriptionStatus status,
                                         ZonedDateTime startAt, ZonedDateTime endAt,
                                         boolean autoRenew, ZonedDateTime createdOn) {
}
