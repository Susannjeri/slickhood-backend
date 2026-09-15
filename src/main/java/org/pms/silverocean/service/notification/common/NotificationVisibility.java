package org.pms.silverocean.service.notification.common;

import java.util.Locale;

/** Security challenges are available only through their dedicated, expiring workflows. */
public final class NotificationVisibility {
    private NotificationVisibility() {}

    public static final String PERSONAL_QUERY = "n.type IS NOT NULL AND UPPER(n.type) NOT LIKE '%OTP%' "
            + "AND UPPER(n.type) NOT IN ('SOKO_DELIVERY_RECOVERY_EMAIL','SOKO_DELIVERY_CODE_EMAIL')";

    public static boolean personal(String type) {
        if (type == null || type.isBlank()) return false;
        String normalized = type.toUpperCase(Locale.ROOT);
        return !normalized.contains("OTP") && !normalized.equals("SOKO_DELIVERY_RECOVERY_EMAIL")
                && !normalized.equals("SOKO_DELIVERY_CODE_EMAIL");
    }
}
