package org.pms.silverocean.service.insurance;

public record InsuranceEmailRequestedEvent(long exchangeId) {
    public static final String TYPE = "INSURANCE_EMAIL_REQUESTED";
}
