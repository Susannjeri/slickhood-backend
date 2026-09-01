package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.PropertyListingInquiry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.EntityGraph;
import java.util.Optional;

public interface PropertyListingInquiryRepo extends JpaRepository<PropertyListingInquiry, Long> {
    @EntityGraph(attributePaths = "listing")
    @Query("SELECT i FROM PropertyListingInquiry i JOIN PropertyListing l ON l.id=i.listingId JOIN Unit u ON u.id=l.unitId JOIN Property p ON p.id=u.propertyId " +
            "WHERE i.active AND (l.publisherUserId=:userId OR p.createdBy=:userId " +
            "OR EXISTS (SELECT 1 FROM PropertyManager pm WHERE pm.propertyId=p.id AND pm.userId=:userId AND pm.active) " +
            "OR EXISTS (SELECT 1 FROM PropertyOwnership po WHERE po.propertyId=p.id AND (po.unitId IS NULL OR po.unitId=u.id) AND po.homeownerUserId=:userId AND po.active))")
    Page<PropertyListingInquiry> findAccessible(long userId, Pageable pageable);

    @EntityGraph(attributePaths = "listing")
    @Query("SELECT i FROM PropertyListingInquiry i JOIN PropertyListing l ON l.id=i.listingId JOIN Unit u ON u.id=l.unitId JOIN Property p ON p.id=u.propertyId " +
            "WHERE i.id=:id AND i.active AND (l.publisherUserId=:userId OR p.createdBy=:userId " +
            "OR EXISTS (SELECT 1 FROM PropertyManager pm WHERE pm.propertyId=p.id AND pm.userId=:userId AND pm.active) " +
            "OR EXISTS (SELECT 1 FROM PropertyOwnership po WHERE po.propertyId=p.id AND (po.unitId IS NULL OR po.unitId=u.id) AND po.homeownerUserId=:userId AND po.active))")
    Optional<PropertyListingInquiry> findAccessibleById(long id, long userId);
}
