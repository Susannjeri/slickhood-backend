package org.pms.silverocean.service.sp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.entities.ServiceRating;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.roles.enums.Permission;
import org.pms.silverocean.service.sp.dao.ServiceBookingDao;
import org.pms.silverocean.service.sp.dao.ServiceRatingDao;
import org.pms.silverocean.service.sp.enums.BookingStatus;
import org.pms.silverocean.service.sp.wrappers.RateServiceRequest;
import org.pms.silverocean.service.sp.wrappers.ServiceRatingDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class ServiceRatingService {
    private final ServiceRatingDao ratingDao;
    private final ServiceBookingDao bookingDao;
    private final RiskScoreService riskScoreService;
    private final UserDao userDao;

    @Transactional(transactionManager = "pmsDBTransactionManager")
    public ServiceRatingDTO rateService(RateServiceRequest request) {
        long userId = userDao.getUserId();
        var booking = bookingDao.findByIdAndBookingCreatedByForRead(request.bookingId(), userId)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SP_BOOKING_NOT_FOUND));
        if (!BookingStatus.COMPLETED.name().equals(booking.getStatus())) {
            throw new PMSCustomException(ResponseCode.SP_BOOKING_NOT_COMPLETED);
        }
        if (ratingDao.existsByBookingIdAndRater(request.bookingId(), userId)) {
            throw new PMSCustomException(ResponseCode.SP_RATING_ALREADY_SUBMITTED);
        }
        ServiceRating rating = new ServiceRating();
        rating.setServiceId(booking.getServiceId());
        rating.setBookingId(request.bookingId());
        rating.setRatedByUserId(userId);
        rating.setStars(request.stars());
        rating.setComment(request.comment());
        rating.setActive(true);
        rating.setCreatedBy(userId);
        ratingDao.save(rating, Permission.RATE_SP_SERVICE);

        riskScoreService.recomputeRiskLabel(booking.getServiceId());

        return new ServiceRatingDTO(rating);
    }

    public Page<ServiceRatingDTO> listRatingsForService(long serviceId, Pageable pageable) {
        return ratingDao.findByServiceId(pageable, serviceId).map(ServiceRatingDTO::new);
    }
}
