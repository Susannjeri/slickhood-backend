package org.pms.silverocean.service.sp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.ServiceRatingRepo;
import org.pms.silverocean.database.pms.entities.ProviderService;
import org.pms.silverocean.database.pms.entities.RiskScore;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.sp.dao.RiskScoreDao;
import org.pms.silverocean.service.sp.dao.ServiceRatingDao;
import org.pms.silverocean.service.sp.enums.PricingUnit;
import org.pms.silverocean.service.sp.wrappers.ProviderServiceDTO;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServiceDirectoryServiceTest {

    @Mock private ProviderServiceDao serviceDao;
    @Mock private RiskScoreDao riskScoreDao;
    @Mock private ServiceRatingDao ratingDao;

    private ServiceDirectoryService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new ServiceDirectoryService(serviceDao, riskScoreDao, ratingDao);
    }

    @Test
    void searchDirectory_rejectsInvertedPriceRange() {
        PMSCustomException ex = assertThrows(PMSCustomException.class, () -> service.searchDirectory(
                PageRequest.of(0, 12), null, null, new BigDecimal("500"), new BigDecimal("100")));

        assertEquals(ResponseCode.INVALID_FIELD_DATA, ex.getResponseCode());
        verify(serviceDao, never()).searchDirectory(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void searchDirectory_rejectsHalfSpecifiedLocation() {
        PMSCustomException ex = assertThrows(PMSCustomException.class, () -> service.searchDirectory(
                PageRequest.of(0, 12), null, "cleaning", null, null, -1.28, null, 10.0));

        assertEquals(ResponseCode.INVALID_FIELD_DATA, ex.getResponseCode());
        verify(serviceDao, never()).searchDirectory(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void searchDirectory_usesGlobalPriceOrderWhileKeepingLocationRadiusFilter() {
        when(serviceDao.searchDirectory(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        service.searchDirectory(PageRequest.of(0, 24, Sort.by("amount").ascending()), null, null,
                null, null, -1.2864, 36.8172, 10.0);

        verify(serviceDao).searchDirectory(any(), eq(null), eq(null), eq(null), eq(null),
                eq(null), eq(null), any(), any(), any(), any());
    }

    @Test
    void searchDirectory_usesDistanceOrderForLocationAwareDefaultResults() {
        when(serviceDao.searchDirectory(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        service.searchDirectory(PageRequest.of(0, 24), null, null,
                null, null, -1.2864, 36.8172, 10.0);

        verify(serviceDao).searchDirectory(any(), eq(null), eq(null), eq(null), eq(null),
                eq(-1.2864), eq(36.8172), any(), any(), any(), any());
    }

    @Test
    void listDirectory_boundsPageSizeEnrichesProviderRatingAndReducesLocationPrecision() {
        ProviderServiceDTO listing = listing(7L, 11L, -1.286389, 36.817223);
        when(serviceDao.findListedByCategoryEnriched(any(), eq(4L)))
                .thenReturn(new PageImpl<>(List.of(listing), PageRequest.of(0, 48), 1));
        ServiceRatingRepo.ProviderRatingAggregate aggregate = mock(ServiceRatingRepo.ProviderRatingAggregate.class);
        when(aggregate.getProfileId()).thenReturn(11L);
        when(aggregate.getAverageRating()).thenReturn(4.75);
        when(aggregate.getRatingCount()).thenReturn(24L);
        when(ratingDao.aggregateByProfileIds(List.of(11L))).thenReturn(List.of(aggregate));

        ProviderServiceDTO result = service.listDirectory(PageRequest.of(0, 500), 4L).getContent().get(0);

        assertEquals(4.75, result.averageRating());
        assertEquals(24L, result.ratingCount());
        assertEquals("Trusted residential cleaning team.", result.providerDescription());
        assertEquals("Nairobi and Kiambu", result.providerServiceArea());
        assertEquals(-1.29, result.latitude());
        assertEquals(36.82, result.longitude());
        verify(serviceDao).findListedByCategoryEnriched(
                org.mockito.ArgumentMatchers.argThat(page -> page.getPageNumber() == 0 && page.getPageSize() == 48), eq(4L));
    }

    @Test
    void getServiceDetails_returnsRiskAndBothProviderAndListingRatingSummaries() {
        ProviderServiceDTO listing = listing(7L, 11L, -1.286389, 36.817223);
        when(serviceDao.findByIdEnriched(7L)).thenReturn(Optional.of(listing));
        ServiceRatingRepo.ProviderRatingAggregate aggregate = mock(ServiceRatingRepo.ProviderRatingAggregate.class);
        when(aggregate.getProfileId()).thenReturn(11L);
        when(aggregate.getAverageRating()).thenReturn(4.4);
        when(aggregate.getRatingCount()).thenReturn(10L);
        when(ratingDao.aggregateByProfileIds(List.of(11L))).thenReturn(List.of(aggregate));
        RiskScore risk = new RiskScore();
        risk.setServiceId(7L);
        risk.setLabel("VERIFIED");
        when(riskScoreDao.findLatestByServiceId(7L)).thenReturn(Optional.of(risk));
        when(ratingDao.avgStars(7L)).thenReturn(4.8);
        when(ratingDao.countByServiceId(7L)).thenReturn(6L);

        var result = service.getServiceDetails(7L);

        assertEquals("Deep home clean", result.service().title());
        assertEquals(4.4, result.service().averageRating());
        assertEquals(10L, result.service().ratingCount());
        assertEquals(-1.29, result.service().latitude());
        assertEquals("VERIFIED", result.riskScore().label());
        assertEquals(4.8, result.averageRating());
        assertEquals(6L, result.ratingCount());
    }

    @Test
    void getServiceDetails_hidesUnknownOrUnlistedService() {
        when(serviceDao.findByIdEnriched(404L)).thenReturn(Optional.empty());

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> service.getServiceDetails(404L));

        assertEquals(ResponseCode.SP_SERVICE_NOT_FOUND, ex.getResponseCode());
    }

    private ProviderServiceDTO listing(long id, long profileId, double latitude, double longitude) {
        ProviderService listing = new ProviderService();
        listing.setId(id);
        listing.setProfileId(profileId);
        listing.setCategoryId(4L);
        listing.setCategoryName("Cleaning");
        listing.setTitle("Deep home clean");
        listing.setDescription("A detailed home cleaning service for occupied homes.");
        listing.setAmount(new BigDecimal("1500"));
        listing.setCurrency("KES");
        listing.setPricingUnit(PricingUnit.PER_JOB);
        listing.setStatus("LISTED");
        return new ProviderServiceDTO(listing.getId(), listing.getProfileId(), listing.getCategoryId(),
                listing.getCategoryName(), listing.getTitle(), listing.getDescription(), listing.getTier(),
                listing.getAmount(), listing.getCurrency(), listing.getPricingUnit(), listing.getStatus(),
                listing.getCreatedOn(), "CleanCo", "Trusted residential cleaning team.",
                "Nairobi and Kiambu", latitude, longitude, "VERIFIED", null, null);
    }
}
