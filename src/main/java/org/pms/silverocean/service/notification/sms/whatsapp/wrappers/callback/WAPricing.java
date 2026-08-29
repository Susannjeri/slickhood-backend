package org.pms.silverocean.service.notification.sms.whatsapp.wrappers.callback;

import com.fasterxml.jackson.annotation.JsonProperty;

public record WAPricing(boolean billable,
                        @JsonProperty("pricing_model") String pricingModel,
                        String category,
                        String type) {
}
