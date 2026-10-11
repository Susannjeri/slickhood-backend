package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.ServiceRating;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.Collection;
import java.util.List;

public interface ServiceRatingRepo extends JpaRepository<ServiceRating, Long> {
    @Query("SELECT r FROM ServiceRating r WHERE r.serviceId = :serviceId AND r.active=true")
    Page<ServiceRating> findByServiceId(Pageable pageable, long serviceId);

    @Query("SELECT COUNT(r) > 0 FROM ServiceRating r WHERE r.bookingId = :bookingId AND r.ratedByUserId = :userId AND r.active=true")
    boolean existsByBookingIdAndRater(long bookingId, long userId);

    @Query("SELECT COUNT(r) FROM ServiceRating r WHERE r.serviceId = :serviceId AND r.stars >= :minStars AND r.active=true")
    int countHighlyRated(long serviceId, int minStars);

    @Query("SELECT COALESCE(AVG(r.stars), 0) FROM ServiceRating r WHERE r.serviceId = :serviceId AND r.active=true")
    double avgStars(long serviceId);

    @Query("SELECT COUNT(r) FROM ServiceRating r WHERE r.serviceId = :serviceId AND r.active=true")
    long countByServiceId(long serviceId);

    interface ProviderRatingAggregate {
        Long getProfileId();
        Double getAverageRating();
        Long getRatingCount();
    }

    @Query("SELECT ps.profileId AS profileId, COALESCE(AVG(r.stars),0) AS averageRating, COUNT(r.id) AS ratingCount " +
            "FROM ServiceRating r JOIN ProviderService ps ON r.serviceId=ps.id " +
            "WHERE ps.profileId IN :profileIds AND r.active=true AND ps.active=true GROUP BY ps.profileId")
    List<ProviderRatingAggregate> aggregateByProfileIds(Collection<Long> profileIds);
}
