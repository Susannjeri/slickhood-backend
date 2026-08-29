package org.pms.silverocean.service.sp.wrappers;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.pms.silverocean.database.pms.entities.ProviderService;
import org.pms.silverocean.service.sp.enums.PricingUnit;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProviderServiceDTO(
    long id, long profileId, long categoryId, String categoryName,
    String tier, BigDecimal amount, String currency, PricingUnit pricingUnit,
    String status, ZonedDateTime createdOn,
    String serviceProviderName, Double latitude, Double longitude, String riskLabel
) {
    public ProviderServiceDTO(ProviderService s) {
        this(s.getId(), s.getProfileId(), s.getCategoryId(), s.getCategoryName(),
             s.getTier(), s.getAmount(), s.getCurrency(), s.getPricingUnit(),
             s.getStatus(), s.getCreatedOn(),
             null, null, null, null);
    }

    public ProviderServiceDTO(ProviderService s, String serviceProviderName, Double latitude, Double longitude, String riskLabel) {
        this(s.getId(), s.getProfileId(), s.getCategoryId(), s.getCategoryName(),
             s.getTier(), s.getAmount(), s.getCurrency(), s.getPricingUnit(),
             s.getStatus(), s.getCreatedOn(),
             serviceProviderName, latitude, longitude, riskLabel);
    }
}
