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
import org.pms.silverocean.database.pms.entities.Unit;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.filestorage.GarageService;
import org.pms.silverocean.service.helpdesk.HelpDeskRateLimiter;
import org.pms.silverocean.service.notification.NotificationService;
import org.pms.silverocean.service.property.PMSPropertyManagementMode;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PropertyListingServiceTest {
    @Mock PropertyListingRepo listings; @Mock PropertyListingInquiryRepo inquiries; @Mock UnitRepo units;
    @Mock UserDao users; @Mock GarageService garage; @Mock HelpDeskRateLimiter limiter; @Mock NotificationService notifications;
    PropertyListingService service;

    @BeforeEach void setUp() {
        service = new PropertyListingService(listings, inquiries, units, users, garage, limiter, notifications);
        ReflectionTestUtils.setField(service, "expiryDays", 90);
        ReflectionTestUtils.setField(service, "publicApiPrefix", "/public/property-listings");
        when(users.getUserId()).thenReturn(42L);
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
        verify(units).save(unit); verify(listings).save(any(PropertyListing.class));
    }

    private Unit eligibleUnit() {
        Property property=new Property(); property.setId(3L); property.setName("Atlas Court"); property.setAddress("Kilimani, Nairobi, Kenya");
        property.setManagementMode(PMSPropertyManagementMode.RENTAL); property.setActive(true);
        Unit unit=new Unit(); unit.setId(7L); unit.setPropertyId(3L); unit.setProperty(property); unit.setUnitType("TWO_BEDROOM");
        unit.setPrice(85000); unit.setCurrency("KES"); unit.setActive(true); unit.setImagePath("properties/3/units/7"); unit.setThumbnail("cover.jpg");
        return unit;
    }
}
