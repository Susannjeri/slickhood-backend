package org.pms.silverocean.service.sp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.entities.ServiceBooking;
import org.pms.silverocean.database.pms.entities.ServiceRating;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.sp.dao.ServiceBookingDao;
import org.pms.silverocean.service.sp.dao.ServiceRatingDao;
import org.pms.silverocean.service.sp.enums.BookingStatus;
import org.pms.silverocean.service.sp.wrappers.RateServiceRequest;
import org.pms.silverocean.service.sp.wrappers.ServiceRatingDTO;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServiceRatingServiceTest {

    @Mock
    private ServiceRatingDao ratingDao;
    @Mock
    private ServiceBookingDao bookingDao;
    @Mock
    private RiskScoreService riskScoreService;
    @Mock
    private UserDao userDao;
    @Mock
    private ProviderServiceDao providerServiceDao;

    private ServiceRatingService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new ServiceRatingService(ratingDao, bookingDao, riskScoreService, userDao, providerServiceDao);
    }

    private ServiceBooking makeBooking(long id, long serviceId, String status) {
        ServiceBooking b = new ServiceBooking();
        b.setId(id);
        b.setServiceId(serviceId);
        b.setStatus(status);
        return b;
    }

    @Test
    void rateService_throwsSP_BOOKING_NOT_FOUND_whenBookingAbsent() {
        when(userDao.getUserId()).thenReturn(1L);
        when(bookingDao.findByIdAndBookingCreatedByForRead(100L, 1L)).thenReturn(Optional.empty());

        RateServiceRequest request = new RateServiceRequest(100L, 5, "Great service");

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> service.rateService(request));
        assertEquals(ResponseCode.SP_BOOKING_NOT_FOUND, ex.getResponseCode());
    }

    @Test
    void rateService_throwsSP_BOOKING_NOT_COMPLETED_whenBookingNotCompleted() {
        when(userDao.getUserId()).thenReturn(1L);
        ServiceBooking b = makeBooking(100L, 10L, BookingStatus.CONFIRMED.name());
        when(bookingDao.findByIdAndBookingCreatedByForRead(100L, 1L)).thenReturn(Optional.of(b));

        RateServiceRequest request = new RateServiceRequest(100L, 4, "Was good");

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> service.rateService(request));
        assertEquals(ResponseCode.SP_BOOKING_NOT_COMPLETED, ex.getResponseCode());
    }

    @Test
    void rateService_throwsSP_RATING_ALREADY_SUBMITTED_whenDuplicate() {
        when(userDao.getUserId()).thenReturn(1L);
        ServiceBooking b = makeBooking(100L, 10L, BookingStatus.COMPLETED.name());
        when(bookingDao.findByIdAndBookingCreatedByForRead(100L, 1L)).thenReturn(Optional.of(b));
        when(ratingDao.existsByBookingIdAndRater(100L, 1L)).thenReturn(true);

        RateServiceRequest request = new RateServiceRequest(100L, 3, "Changed my mind");

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> service.rateService(request));
        assertEquals(ResponseCode.SP_RATING_ALREADY_SUBMITTED, ex.getResponseCode());
    }

    @Test
    void rateService_savesRating_andCallsRecomputeRiskLabel_onSuccess() {
        when(userDao.getUserId()).thenReturn(1L);
        when(bookingDao.findByIdAndBookingCreatedByForRead(100L, 1L)).thenReturn(Optional.of(makeBooking(100L, 10L, BookingStatus.COMPLETED.name())));
        when(ratingDao.existsByBookingIdAndRater(100L, 1L)).thenReturn(false);
        doAnswer(inv -> { ((ServiceRating) inv.getArgument(0)).setId(1L); return null; })
                .when(ratingDao).save(any(ServiceRating.class), anyString());
        doNothing().when(riskScoreService).recomputeRiskLabel(anyLong());

        RateServiceRequest request = new RateServiceRequest(100L, 5, "Excellent work!");

        ServiceRatingDTO dto = service.rateService(request);

        ArgumentCaptor<ServiceRating> captor = ArgumentCaptor.forClass(ServiceRating.class);
        verify(ratingDao).save(captor.capture(), anyString());
        ServiceRating saved = captor.getValue();

        assertEquals(10L, saved.getServiceId());
        assertEquals(100L, saved.getBookingId());
        assertEquals(1L, saved.getRatedByUserId());
        assertEquals(5, saved.getStars());
        assertEquals("Excellent work!", saved.getComment());
        verify(riskScoreService).recomputeRiskLabel(10L);
        assertEquals(5, dto.stars());
    }

    @Test
    void rateService_savesRating_withMinimumStars() {
        when(userDao.getUserId()).thenReturn(2L);
        when(bookingDao.findByIdAndBookingCreatedByForRead(101L, 2L)).thenReturn(Optional.of(makeBooking(101L, 20L, BookingStatus.COMPLETED.name())));
        when(ratingDao.existsByBookingIdAndRater(101L, 2L)).thenReturn(false);
        doAnswer(inv -> { ((ServiceRating) inv.getArgument(0)).setId(2L); return null; })
                .when(ratingDao).save(any(ServiceRating.class), anyString());
        doNothing().when(riskScoreService).recomputeRiskLabel(anyLong());

        RateServiceRequest request = new RateServiceRequest(101L, 1, "Poor experience");

        ServiceRatingDTO dto = service.rateService(request);

        ArgumentCaptor<ServiceRating> captor = ArgumentCaptor.forClass(ServiceRating.class);
        verify(ratingDao).save(captor.capture(), anyString());
        assertEquals(1, captor.getValue().getStars());
        verify(riskScoreService).recomputeRiskLabel(20L);
    }

    @Test
    void rateService_mapsConcurrentDuplicateInsertToAlreadySubmitted() {
        when(userDao.getUserId()).thenReturn(1L);
        when(bookingDao.findByIdAndBookingCreatedByForRead(100L, 1L))
                .thenReturn(Optional.of(makeBooking(100L, 10L, BookingStatus.COMPLETED.name())));
        when(ratingDao.existsByBookingIdAndRater(100L, 1L)).thenReturn(false);
        doThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate rating"))
                .when(ratingDao).save(any(ServiceRating.class), anyString());

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> service.rateService(new RateServiceRequest(100L, 5, "Excellent")));

        assertEquals(ResponseCode.SP_RATING_ALREADY_SUBMITTED, ex.getResponseCode());
        verify(riskScoreService, never()).recomputeRiskLabel(anyLong());
    }

    @Test
    void listRatingsForService_rejectsHiddenOrUnavailableListingWithoutReadingRatings() {
        when(providerServiceDao.findByIdEnriched(10L)).thenReturn(Optional.empty());

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> service.listRatingsForService(10L, org.springframework.data.domain.PageRequest.of(0, 20)));

        assertEquals(ResponseCode.SP_SERVICE_NOT_FOUND, ex.getResponseCode());
        verify(ratingDao, never()).findByServiceId(any(), anyLong());
    }

    @Test
    void listRatingsForService_returnsRatingsForPublicDirectoryListing() {
        var listing = org.mockito.Mockito.mock(org.pms.silverocean.service.sp.wrappers.ProviderServiceDTO.class);
        when(providerServiceDao.findByIdEnriched(10L)).thenReturn(Optional.of(listing));
        ServiceRating rating = new ServiceRating();
        rating.setId(5L);
        rating.setServiceId(10L);
        rating.setStars(4);
        when(ratingDao.findByServiceId(any(), eq(10L)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of(rating)));

        var result = service.listRatingsForService(10L, org.springframework.data.domain.PageRequest.of(0, 20));

        assertEquals(1, result.getTotalElements());
        assertEquals(4, result.getContent().get(0).stars());
    }
}
