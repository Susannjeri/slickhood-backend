package org.pms.silverocean.service.notification.common;

import java.util.Locale;

/**
 * External security challenges stay out of the ordinary inbox. Delivery codes
 * use a separate buyer-only IN_APP record so the SMS/email transport records
 * cannot appear as duplicate or cross-channel inbox messages.
 */
public final class NotificationVisibility {
    private NotificationVisibility() {}

    public static final String PERSONAL_QUERY = "n.type IS NOT NULL AND UPPER(n.type) NOT LIKE '%OTP%' "
            + "AND UPPER(n.type) NOT IN ('SOKO_DELIVERY_RECOVERY_EMAIL','SOKO_DELIVERY_CODE_EMAIL','SOKO_DELIVERY_CODE_SMS','SOKO_RIDER_ASSIGNMENT_SMS')";

    public static boolean personal(String type) {
        if (type == null || type.isBlank()) return false;
        String normalized = type.toUpperCase(Locale.ROOT);
        return !normalized.contains("OTP") && !normalized.equals("SOKO_DELIVERY_RECOVERY_EMAIL")
                && !normalized.equals("SOKO_DELIVERY_CODE_EMAIL")
                && !normalized.equals("SOKO_DELIVERY_CODE_SMS")
                && !normalized.equals("SOKO_RIDER_ASSIGNMENT_SMS");
    }

    /** Personal-feed visibility does not make a security secret safe for a generic broadcast API. */
    public static boolean businessAlertSafe(String type) {
        return personal(type) && !"SOKO_DELIVERY_CODE_IN_APP".equals(type.toUpperCase(Locale.ROOT));
    }
}
