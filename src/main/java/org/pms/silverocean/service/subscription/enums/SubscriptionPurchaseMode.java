package org.pms.silverocean.service.subscription.enums;

/** Controls how a catalogue item may become active. Price alone is never an authorisation rule. */
public enum SubscriptionPurchaseMode {
    FREE,
    SELF_SERVICE,
    SALES_MANAGED
}
