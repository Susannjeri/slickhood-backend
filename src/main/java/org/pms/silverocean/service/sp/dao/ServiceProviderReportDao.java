package org.pms.silverocean.service.sp.dao;

import lombok.RequiredArgsConstructor;
import org.pms.silverocean.common.PMSUtils;
import org.pms.silverocean.database.pms.ServiceBookingRepo;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.time.ZonedDateTime;

@Service
@RequiredArgsConstructor
public class ServiceProviderReportDao {
    private final ServiceBookingRepo repo;

    public int getServiceProviderBookingCountForCurrentMonth(long userId) {
        YearMonth now = YearMonth.now(PMSUtils.getZoneId());
        ZonedDateTime start = now.atDay(1).atStartOfDay(PMSUtils.getZoneId());
        return repo.countBookingWithinSpecifiedMonth(userId, start, ZonedDateTime.now(PMSUtils.getZoneId()));
    }

    public int getServiceProviderBookingCountForPreviousMonth(long userId) {
        YearMonth previousMonth = YearMonth.now(PMSUtils.getZoneId()).minusMonths(1);
        ZonedDateTime start = previousMonth.atDay(1).atStartOfDay(PMSUtils.getZoneId());
        YearMonth now = YearMonth.now(PMSUtils.getZoneId());
        ZonedDateTime end = now.atDay(1).atStartOfDay(PMSUtils.getZoneId());
        return repo.countBookingWithinSpecifiedMonth(userId, start, end);
    }

    public double getServiceProviderAverageRatingForAllBookings(long userId) {
        return repo.getRatingAveragePerServiceProvider(userId);
    }

    public int getMostRecentRatingForServiceProvider(long userId) {
        Integer rating = repo.getMostRecentRatingForServiceProvider(userId);
        return rating == null ? 0 : rating;
    }
}
