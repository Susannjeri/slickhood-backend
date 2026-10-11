package org.pms.silverocean.service.sp.wrappers;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.pms.silverocean.database.pms.entities.ProviderService;
import org.pms.silverocean.service.sp.enums.PricingUnit;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProviderServiceDTO(
    long id, long profileId, long categoryId, String categoryName,
    String title, String description,
    String tier, BigDecimal amount, String currency, PricingUnit pricingUnit,
    String status, ZonedDateTime createdOn,
    String serviceProviderName, String providerDescription, String providerServiceArea,
    Double latitude, Double longitude, String riskLabel,
    Double averageRating, Long ratingCount
) {
    public ProviderServiceDTO(long id, long profileId, long categoryId, String categoryName,
                              String title, String description, String tier, BigDecimal amount,
                              String currency, PricingUnit pricingUnit, String status,
                              ZonedDateTime createdOn, String serviceProviderName,
                              String providerDescription, String providerServiceArea,
                              Double latitude, Double longitude, String riskLabel) {
        this(id, profileId, categoryId, categoryName, title, description, tier, amount, currency,
                pricingUnit, status, createdOn, serviceProviderName, providerDescription,
                providerServiceArea, latitude, longitude, riskLabel, null, null);
    }

    public ProviderServiceDTO(long id, long profileId, long categoryId, String categoryName,
                              String title, String description, String tier, BigDecimal amount,
                              String currency, PricingUnit pricingUnit, String status,
                              ZonedDateTime createdOn, String serviceProviderName, Double latitude,
                              Double longitude, String riskLabel) {
        this(id, profileId, categoryId, categoryName, title, description, tier, amount, currency,
                pricingUnit, status, createdOn, serviceProviderName, null, null, latitude, longitude,
                riskLabel, null, null);
    }

    public ProviderServiceDTO(ProviderService s) {
        this(s.getId(), s.getProfileId(), s.getCategoryId(), s.getCategoryName(),
             s.getTitle(), s.getDescription(), s.getTier(), s.getAmount(), s.getCurrency(), s.getPricingUnit(),
             s.getStatus(), s.getCreatedOn(),
             null, null, null, null, null, null, null, null);
    }

    public ProviderServiceDTO(ProviderService s, String serviceProviderName, Double latitude, Double longitude, String riskLabel) {
        this(s.getId(), s.getProfileId(), s.getCategoryId(), s.getCategoryName(),
             s.getTitle(), s.getDescription(), s.getTier(), s.getAmount(), s.getCurrency(), s.getPricingUnit(),
             s.getStatus(), s.getCreatedOn(),
             serviceProviderName, null, null, latitude, longitude, riskLabel, null, null);
    }

    public ProviderServiceDTO withRatings(double averageRating, long ratingCount) {
        return new ProviderServiceDTO(id, profileId, categoryId, categoryName, title, description,
                tier, amount, currency, pricingUnit, status, createdOn, serviceProviderName,
                providerDescription, providerServiceArea, latitude, longitude, riskLabel,
                averageRating, ratingCount);
    }

    public ProviderServiceDTO withPublicLocation() {
        Double publicLatitude = latitude == null ? null : Math.round(latitude * 100.0) / 100.0;
        Double publicLongitude = longitude == null ? null : Math.round(longitude * 100.0) / 100.0;
        return new ProviderServiceDTO(id, profileId, categoryId, categoryName, title, description,
                tier, amount, currency, pricingUnit, status, createdOn, serviceProviderName,
                providerDescription, providerServiceArea, publicLatitude, publicLongitude, riskLabel,
                averageRating, ratingCount);
    }
}
