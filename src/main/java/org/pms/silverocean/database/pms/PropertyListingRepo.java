package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.PropertyListing;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;

import java.time.ZonedDateTime;
import java.util.Optional;

public interface PropertyListingRepo extends JpaRepository<PropertyListing, Long> {
    Optional<PropertyListing> findByUnitId(long unitId);

    @EntityGraph(attributePaths = {"unit", "unit.property"})
    @Query("SELECT l FROM PropertyListing l JOIN Unit u ON u.id=l.unitId JOIN Property p ON p.id=u.propertyId " +
            "WHERE l.active AND l.status='PUBLISHED' AND (l.expiresAt IS NULL OR l.expiresAt>:now) " +
            "AND u.active AND NOT u.occupied AND u.advertise AND p.active " +
            "AND (:type IS NULL OR l.listingType=:type) " +
            "AND (:location IS NULL OR LOWER(p.address) LIKE LOWER(CONCAT('%',:location,'%'))) " +
            "AND (:unitType IS NULL OR LOWER(u.unitType)=LOWER(:unitType)) " +
            "AND (:minPrice IS NULL OR u.price>=:minPrice) AND (:maxPrice IS NULL OR u.price<=:maxPrice)")
    Page<PropertyListing> searchPublic(String type, String location, String unitType, Double minPrice,
                                       Double maxPrice, ZonedDateTime now, Pageable pageable);

    @EntityGraph(attributePaths = {"unit", "unit.property"})
    @Query("SELECT l FROM PropertyListing l JOIN Unit u ON u.id=l.unitId JOIN Property p ON p.id=u.propertyId " +
            "WHERE l.publicSlug=:slug AND l.active AND l.status='PUBLISHED' " +
            "AND (l.expiresAt IS NULL OR l.expiresAt>:now) AND u.active AND NOT u.occupied AND u.advertise AND p.active")
    Optional<PropertyListing> findPublicBySlug(String slug, ZonedDateTime now);

    @EntityGraph(attributePaths = {"unit", "unit.property"})
    Page<PropertyListing> findAllByOrderByCreatedOnDesc(Pageable pageable);

    @EntityGraph(attributePaths = {"unit", "unit.property"})
    Page<PropertyListing> findByStatusAndExpiresAtBeforeOrderByExpiresAtAsc(String status, ZonedDateTime before, Pageable pageable);
}
