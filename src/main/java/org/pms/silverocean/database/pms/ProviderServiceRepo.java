package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.ProviderService;
import org.pms.silverocean.service.sp.wrappers.ProviderServiceDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.math.BigDecimal;

public interface ProviderServiceRepo extends JpaRepository<ProviderService, Long> {

    String ENRICHED_FIELDS =
            "s.id, s.profileId, s.categoryId, s.categoryName, s.tier, s.amount, s.currency, s.pricingUnit, s.status, s.createdOn, " +
            "p.businessName, p.latitude, p.longitude, " +
            "(SELECT r.label FROM RiskScore r WHERE r.serviceId = s.id " +
            " AND r.computedAt = (SELECT MAX(r2.computedAt) FROM RiskScore r2 WHERE r2.serviceId = s.id))";

    @Query(value = "SELECT new org.pms.silverocean.service.sp.wrappers.ProviderServiceDTO(" + ENRICHED_FIELDS + ") " +
            "FROM ProviderService s JOIN ProviderProfile p ON p.id = s.profileId " +
            "WHERE s.profileId = :profileId AND s.active = true ORDER BY s.createdOn DESC",
            countQuery = "SELECT COUNT(s) FROM ProviderService s WHERE s.profileId = :profileId AND s.active = true")
    Page<ProviderServiceDTO> findByProfileIdEnriched(long profileId, Pageable pageable);

    @Query(value = "SELECT new org.pms.silverocean.service.sp.wrappers.ProviderServiceDTO(" + ENRICHED_FIELDS + ") " +
            "FROM ProviderService s JOIN ProviderProfile p ON p.id = s.profileId " +
            "WHERE s.status IN ('SUBMITTED','UNDER_REVIEW') AND s.active = true",
            countQuery = "SELECT COUNT(s) FROM ProviderService s WHERE s.status IN ('SUBMITTED','UNDER_REVIEW') AND s.active = true")
    Page<ProviderServiceDTO> findPendingAdminReviewEnriched(Pageable pageable);

    @Query(value = "SELECT new org.pms.silverocean.service.sp.wrappers.ProviderServiceDTO(" + ENRICHED_FIELDS + ") " +
            "FROM ProviderService s JOIN ProviderProfile p ON p.id = s.profileId " +
            "WHERE s.status = 'LISTED' AND p.status='ACTIVE' AND s.active = true AND (:categoryId IS NULL OR s.categoryId = :categoryId)",
            countQuery = "SELECT COUNT(s) FROM ProviderService s JOIN ProviderProfile p ON p.id = s.profileId  WHERE s.status = 'LISTED' AND p.status='ACTIVE' " +
                    " AND s.active = true AND (:categoryId IS NULL OR s.categoryId = :categoryId)")
    Page<ProviderServiceDTO> findListedByCategoryEnriched(Pageable pageable, Long categoryId);

    @Query(value = "SELECT new org.pms.silverocean.service.sp.wrappers.ProviderServiceDTO(" + ENRICHED_FIELDS + ") " +
            "FROM ProviderService s JOIN ProviderProfile p ON p.id=s.profileId " +
            "WHERE s.status='LISTED' AND p.status='ACTIVE' AND s.active=true " +
            "AND (:categoryId IS NULL OR s.categoryId=:categoryId) " +
            "AND (:query IS NULL OR LOWER(s.categoryName) LIKE LOWER(CONCAT('%',:query,'%')) OR LOWER(p.businessName) LIKE LOWER(CONCAT('%',:query,'%'))) " +
            "AND (:minAmount IS NULL OR s.amount>=:minAmount) AND (:maxAmount IS NULL OR s.amount<=:maxAmount) " +
            "AND (:minLat IS NULL OR (p.latitude BETWEEN :minLat AND :maxLat AND p.longitude BETWEEN :minLng AND :maxLng)) " +
            "ORDER BY CASE WHEN :latitude IS NULL THEN 0.0 ELSE ((p.latitude-:latitude)*(p.latitude-:latitude)+(p.longitude-:longitude)*(p.longitude-:longitude)) END ASC, s.amount ASC",
            countQuery = "SELECT COUNT(s) FROM ProviderService s JOIN ProviderProfile p ON p.id=s.profileId " +
                    "WHERE s.status='LISTED' AND p.status='ACTIVE' AND s.active=true " +
                    "AND (:categoryId IS NULL OR s.categoryId=:categoryId) " +
                    "AND (:query IS NULL OR LOWER(s.categoryName) LIKE LOWER(CONCAT('%',:query,'%')) OR LOWER(p.businessName) LIKE LOWER(CONCAT('%',:query,'%'))) " +
                    "AND (:minAmount IS NULL OR s.amount>=:minAmount) AND (:maxAmount IS NULL OR s.amount<=:maxAmount) " +
                    "AND (:minLat IS NULL OR (p.latitude BETWEEN :minLat AND :maxLat AND p.longitude BETWEEN :minLng AND :maxLng))")
    Page<ProviderServiceDTO> searchDirectory(Pageable pageable, Long categoryId, String query, BigDecimal minAmount, BigDecimal maxAmount,
                                             Double latitude, Double longitude, Double minLat, Double maxLat, Double minLng, Double maxLng);

    @Query(value = "SELECT new org.pms.silverocean.service.sp.wrappers.ProviderServiceDTO(" + ENRICHED_FIELDS + ") " +
            "FROM ProviderService s JOIN ProviderProfile p ON p.id = s.profileId " +
            "WHERE s.id = :id AND s.status='LISTED' AND p.status='ACTIVE' AND s.active = true")
    Optional<ProviderServiceDTO> findByIdEnriched(long id);

    @Query("SELECT s FROM ProviderService s WHERE s.id = :id AND s.profileId = :profileId AND s.active = true")
    Optional<ProviderService> findByIdAndProfileId(long id, long profileId);
}
