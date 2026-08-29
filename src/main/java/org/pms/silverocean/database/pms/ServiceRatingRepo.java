package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.ServiceRating;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ServiceRatingRepo extends JpaRepository<ServiceRating, Long> {
    @Query("SELECT r FROM ServiceRating r WHERE r.serviceId = :serviceId")
    Page<ServiceRating> findByServiceId(Pageable pageable, long serviceId);

    @Query("SELECT COUNT(r) > 0 FROM ServiceRating r WHERE r.bookingId = :bookingId AND r.ratedByUserId = :userId")
    boolean existsByBookingIdAndRater(long bookingId, long userId);

    @Query("SELECT COUNT(r) FROM ServiceRating r WHERE r.serviceId = :serviceId AND r.stars >= :minStars")
    int countHighlyRated(long serviceId, int minStars);

    @Query("SELECT COALESCE(AVG(r.stars), 0) FROM ServiceRating r WHERE r.serviceId = :serviceId")
    double avgStars(long serviceId);

    long countByServiceId(long serviceId);
}
