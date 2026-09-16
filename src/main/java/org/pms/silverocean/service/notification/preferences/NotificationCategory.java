package org.pms.silverocean.service.notification.preferences;

import java.util.Locale;

public enum NotificationCategory {
    BILLING,
    PROPERTY,
    MARKETPLACE_DELIVERY,
    SECURITY,
    MARKETING;

    public static NotificationCategory fromEvent(String type) {
        String value = type == null ? "" : type.toUpperCase(Locale.ROOT);
        if (value.contains("SOKO") || value.contains("DELIVERY") || value.contains("SERVICE_BOOKING")
                || value.contains("SP_")) return MARKETPLACE_DELIVERY;
        if (value.contains("OTP") || value.contains("VERIFICATION") || value.contains("SECURITY")
                || value.contains("LOGIN") || value.contains("PASSWORD") || value.contains("MFA")) return SECURITY;
        if (value.contains("PAYMENT") || value.contains("INVOICE") || value.contains("RECEIVABLE")
                || value.contains("OVERDUE") || value.contains("LATE_FEE") || value.contains("CHARGE")
                || value.contains("SUBSCRIPTION")) return BILLING;
        if (value.contains("MARKETING") || value.contains("PROMOTION") || value.contains("CAMPAIGN")) return MARKETING;
        return PROPERTY;
    }
}
