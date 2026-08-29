package org.pms.silverocean.service.sp.wrappers;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.pms.silverocean.database.pms.entities.ProviderProfile;

import java.time.ZonedDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProviderProfileDTO(
    long id, long userId, String businessName, String status,
    ZonedDateTime consentTimestamp, Double latitude, Double longitude,
    String adminNotes, Long paymentAccountId, ZonedDateTime createdOn
) {
    public ProviderProfileDTO(ProviderProfile p) {
        this(p.getId(), p.getUserId(), p.getBusinessName(), p.getStatus(),
             p.getConsentTimestamp(), p.getLatitude(), p.getLongitude(),
             p.getAdminNotes(), p.getPaymentAccountId(), p.getCreatedOn());
    }
}
