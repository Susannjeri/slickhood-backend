package org.pms.silverocean.service.property.listing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pms.silverocean.database.pms.PropertyListingInquiryRepo;
import org.pms.silverocean.database.pms.PropertyListingRepo;
import org.pms.silverocean.database.pms.UnitRepo;
import org.pms.silverocean.database.pms.entities.Property;
import org.pms.silverocean.database.pms.entities.PropertyListing;
import org.pms.silverocean.database.pms.entities.PropertyListingInquiry;
import org.pms.silverocean.database.pms.entities.Unit;
import org.pms.silverocean.database.pms.entities.Users;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.audit.AuditLogService;
import org.pms.silverocean.service.filestorage.GarageService;
import org.pms.silverocean.service.helpdesk.HelpDeskRateLimiter;
import org.pms.silverocean.service.notification.NotificationService;
import org.pms.silverocean.service.property.PMSPropertyManagementMode;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PropertyListingServiceTest {
    @Mock PropertyListingRepo listings; @Mock PropertyListingInquiryRepo inquiries; @Mock UnitRepo units;
    @Mock UserDao users; @Mock GarageService garage; @Mock HelpDeskRateLimiter limiter; @Mock NotificationService notifications;
    @Mock AuditLogService audit;
    PropertyListingService service;

    @BeforeEach void setUp() {
        service = new PropertyListingService(listings, inquiries, units, users, garage, limiter, notifications, audit);
        ReflectionTestUtils.setField(service, "expiryDays", 90);
        ReflectionTestUtils.setField(service, "publicApiPrefix", "/public/property-listings");
        ReflectionTestUtils.setField(service, "inquiryLimit", 5);
        ReflectionTestUtils.setField(service, "maxPublicImageBytes", 10_485_760L);
        ReflectionTestUtils.setField(service, "inquiryConsentVersion", "property-enquiry-2026-09");
        lenient().when(users.getUserId()).thenReturn(42L);
    }

    @Test void refusesIncompleteUnitWithoutMutatingAdvertiseFlag() {
        Unit unit=eligibleUnit(); unit.setThumbnail(null);
        when(units.findAdvertisableByUser(7L,42L)).thenReturn(Optional.of(unit));
        when(listings.findByUnitId(7L)).thenReturn(Optional.empty());
        assertThatThrownBy(()->service.publish(7L,new PropertyListingModels.PublishRequest(true,null,null)))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("cover image");
        assertThat(unit.isAdvertise()).isFalse(); verify(units,never()).save(any());
    }

    @Test void publishesEligibleUnitAndCreatesStableListing() {
        Unit unit=eligibleUnit(); when(units.findAdvertisableByUser(7L,42L)).thenReturn(Optional.of(unit));
        when(listings.findByUnitId(7L)).thenReturn(Optional.empty());
        when(listings.save(any())).thenAnswer(invocation->invocation.getArgument(0));
        var result=service.publish(7L,new PropertyListingModels.PublishRequest(true,null,null));
        assertThat(unit.isAdvertise()).isTrue(); assertThat(result.status()).isEqualTo("PUBLISHED");
        assertThat(result.slug()).startsWith("atlas-court-two-bedroom-");
        assertThat(result.slug()).matches("atlas-court-two-bedroom-[0-9a-f]{32}");
        verify(units).save(unit); verify(listings).save(any(PropertyListing.class));
        verify(audit).createAuditLog(any(PropertyListing.class), eq("property_listing_publish"));
    }

    @Test void reportsPublisherVerificationTruthfully() {
        Unit unit=eligibleUnit(); unit.setAdvertise(true);
        PropertyListing listing=new PropertyListing(); listing.setId(9L); listing.setUnitId(unit.getId());
        listing.setUnit(unit); listing.setPublicSlug("atlas-court-two-bedroom-1234567890abcdef1234567890abcdef");
        listing.setListingType("RENT"); listing.setHeadline("Two bedroom at Atlas Court");
        listing.setDescription("A managed property."); listing.setImageManifest("properties/3/units/7/cover.jpg");
        listing.setPublisherUserId(42L); listing.setStatus("PUBLISHED"); listing.setActive(true);
        listing.setPublishedAt(ZonedDateTime.now(ZoneOffset.UTC).minusDays(1));
        listing.setExpiresAt(ZonedDateTime.now(ZoneOffset.UTC).plusDays(30));
        Users publisher=new Users(); publisher.setId(42L); publisher.setVerified(true);
        when(listings.findPublicBySlug(eq(listing.getPublicSlug()),any())).thenReturn(Optional.of(listing));
        when(users.findById(42L)).thenReturn(Optional.of(publisher));

        var result=service.detail(listing.getPublicSlug());

        assertThat(result.verified()).isTrue();
    }

    @Test void recordsConsentEvidenceAndAppliesEmailAndClientRateLimits() {
        Unit unit=eligibleUnit(); unit.setAdvertise(true);
        PropertyListing listing=new PropertyListing(); listing.setId(9L); listing.setUnitId(unit.getId());
        listing.setUnit(unit); listing.setPublicSlug("atlas-court-two-bedroom-1234567890abcdef1234567890abcdef");
        listing.setListingType("RENT"); listing.setHeadline("Two bedroom at Atlas Court");
        listing.setPublisherUserId(42L); listing.setStatus("PUBLISHED"); listing.setActive(true);
        listing.setExpiresAt(ZonedDateTime.now(ZoneOffset.UTC).plusDays(30));
        when(listings.findPublicBySlug(eq(listing.getPublicSlug()),any())).thenReturn(Optional.of(listing));
        when(users.findById(42L)).thenReturn(Optional.empty());

        service.inquire(listing.getPublicSlug(), new PropertyListingModels.InquiryRequest(
                "Amina", " AMINA@EXAMPLE.COM ", "+254700000000", "Please arrange a viewing.", true, ""), "203.0.113.8");

        var captor=org.mockito.ArgumentCaptor.forClass(PropertyListingInquiry.class);
        verify(inquiries).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("amina@example.com");
        assertThat(captor.getValue().getConsentVersion()).isEqualTo("property-enquiry-2026-09");
        assertThat(captor.getValue().getConsentedAt()).isNotNull();
        verify(limiter).check(startsWith("property-inquiry-email:"),eq(5));
        verify(limiter).check(startsWith("property-inquiry-client:"),eq(25));
    }

    private Unit eligibleUnit() {
        Property property=new Property(); property.setId(3L); property.setName("Atlas Court"); property.setAddress("Kilimani, Nairobi, Kenya");
        property.setManagementMode(PMSPropertyManagementMode.RENTAL); property.setActive(true);
        Unit unit=new Unit(); unit.setId(7L); unit.setPropertyId(3L); unit.setProperty(property); unit.setUnitType("TWO_BEDROOM");
        unit.setPrice(85000); unit.setCurrency("KES"); unit.setActive(true); unit.setImagePath("properties/3/units/7"); unit.setThumbnail("cover.jpg");
        return unit;
    }
}
