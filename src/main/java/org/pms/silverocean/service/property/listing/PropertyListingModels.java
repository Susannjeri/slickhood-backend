package org.pms.silverocean.service.property.listing;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.ZonedDateTime;
import java.util.List;

public final class PropertyListingModels {
    private PropertyListingModels() { }

    public record PublishRequest(boolean published, @Size(max = 180) String headline,
                                 @Size(max = 2000) String description) { }
    public record ModerateRequest(@NotBlank String action, @Size(max = 500) String reason) { }
    public record InquiryStatusRequest(@NotBlank String status) { }
    public record InquiryRequest(@NotBlank @Size(max = 120) String name,
                                 @NotBlank @Email @Size(max = 180) String email,
                                 @Size(max = 40) String phone,
                                 @NotBlank @Size(max = 1000) String message,
                                 @AssertTrue boolean consent,
                                 @Size(max = 120) String website) { }
    public record ListingCard(String slug, String listingType, String headline, String propertyType,
                              String unitType, double size, double price, String currency,
                              String location, String imageUrl, ZonedDateTime publishedAt) { }
    public record ListingDetail(String slug, String listingType, String headline, String description,
                                String propertyName, String propertyType, String unitType, double size,
                                double price, String currency, String location, List<String> amenities,
                                List<String> imageUrls, ZonedDateTime publishedAt, ZonedDateTime expiresAt,
                                boolean verified) { }
    public record ListingPage(List<ListingCard> items, int page, int size, int totalPages, long totalElements) { }
    public record UnitTypeOption(String value, String label) { }
    public record ListingFilters(List<UnitTypeOption> unitTypes) { }
    public record Publication(String slug, String status, ZonedDateTime publishedAt, ZonedDateTime expiresAt) { }
    public record AdminListing(long id, String slug, long unitId, String status, String listingType,
                               String headline, long publisherUserId, ZonedDateTime publishedAt,
                               ZonedDateTime expiresAt, String suspensionReason) { }
    public record InquiryView(long id, long listingId, String listingSlug, String listingHeadline,
                              String name, String email, String phone, String message, String status,
                              ZonedDateTime createdOn) { }
}
