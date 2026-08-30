package org.pms.silverocean.service.estate;

public record ServiceChargeReminderEvent(long chargeId, Phase phase) {
    public static final String TYPE = "SERVICE_CHARGE_REMINDER_REQUESTED";
    public enum Phase { PRE_DUE, OVERDUE }
}
