package org.pms.silverocean.service.sp.dao;

import org.junit.jupiter.api.Test;
import org.pms.silverocean.database.pms.ServiceBookingRepo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ServiceProviderReportDaoTest {

    private final ServiceBookingRepo repo = mock(ServiceBookingRepo.class);
    private final ServiceProviderReportDao dao = new ServiceProviderReportDao(repo);

    @Test
    void returnsZeroWhenProviderHasNoRatings() {
        when(repo.getMostRecentRatingForServiceProvider(42L)).thenReturn(null);

        assertEquals(0, dao.getMostRecentRatingForServiceProvider(42L));
    }

    @Test
    void returnsMostRecentRatingWhenOneExists() {
        when(repo.getMostRecentRatingForServiceProvider(42L)).thenReturn(5);

        assertEquals(5, dao.getMostRecentRatingForServiceProvider(42L));
    }
}
