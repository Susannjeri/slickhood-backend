package org.pms.silverocean.service.sp.wrappers;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.pms.silverocean.database.pms.entities.ProviderProfile;

import java.time.ZonedDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProviderProfileDTO(
    long id, long version, long userId, String businessName, String description, String serviceArea, String status,
    ZonedDateTime consentTimestamp, Double latitude, Double longitude,
    String adminNotes, Long reviewedByUserId, ZonedDateTime reviewedAt,
    Long paymentAccountId, ZonedDateTime createdOn
) {
    public ProviderProfileDTO(ProviderProfile p) {
        this(p.getId(), p.getVersion(), p.getUserId(), p.getBusinessName(), p.getDescription(), p.getServiceArea(), p.getStatus(),
             p.getConsentTimestamp(), p.getLatitude(), p.getLongitude(),
             p.getAdminNotes(), p.getReviewedByUserId(), p.getReviewedAt(),
             p.getPaymentAccountId(), p.getCreatedOn());
    }
}
