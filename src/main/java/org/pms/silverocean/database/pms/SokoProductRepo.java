package org.pms.silverocean.database.pms;

import jakarta.persistence.LockModeType;
import org.pms.silverocean.database.pms.entities.SokoProduct;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SokoProductRepo extends JpaRepository<SokoProduct, Long> {
    @Query(value="""
            SELECT p.*
            FROM pms_soko_product p
            JOIN pms_soko_store s ON s.id = p.store_id
            WHERE p.active = 1
              AND p.status = 'PUBLISHED'
              AND p.stock_quantity > 0
              AND s.active = 1
              AND s.status = 'PUBLISHED'
              AND :sortMode IN ('RELEVANCE', 'PRICE', 'NEAREST')
              AND (:storeId IS NULL OR p.store_id = :storeId)
              AND (:category IS NULL OR LOWER(p.category) = LOWER(:category))
              AND (:query IS NULL
                   OR LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%'))
                   OR LOWER(p.description) LIKE LOWER(CONCAT('%', :query, '%'))
                   OR LOWER(s.name) LIKE LOWER(CONCAT('%', :query, '%')))
              AND (:fulfilment = 'ALL'
                   OR (:fulfilment = 'DELIVERY' AND s.delivery_enabled = 1)
                   OR (:fulfilment = 'PICKUP' AND s.pickup_enabled = 1))
              AND (:minLatitude IS NULL OR s.latitude BETWEEN :minLatitude AND :maxLatitude)
              AND (:minLongitude IS NULL OR (
                   (:wrapLongitude = 0 AND s.longitude BETWEEN :minLongitude AND :maxLongitude)
                   OR (:wrapLongitude = 1 AND (s.longitude >= :minLongitude OR s.longitude <= :maxLongitude))
              ))
              AND (:latitude IS NULL OR (
                   s.latitude IS NOT NULL
                   AND s.longitude IS NOT NULL
                   AND 6371.0 * 2.0 * ASIN(SQRT(LEAST(1.0, GREATEST(0.0,
                       POWER(SIN(RADIANS(s.latitude - :latitude) / 2.0), 2)
                       + COS(RADIANS(:latitude)) * COS(RADIANS(s.latitude))
                       * POWER(SIN(RADIANS(s.longitude - :longitude) / 2.0), 2)
                   )))) <= :radiusKm
              ))
              AND (:fulfilment <> 'DELIVERY' OR :latitude IS NULL OR (
                   s.service_radius_km IS NOT NULL
                   AND s.service_radius_km > 0
                   AND 6371.0 * 2.0 * ASIN(SQRT(LEAST(1.0, GREATEST(0.0,
                       POWER(SIN(RADIANS(s.latitude - :latitude) / 2.0), 2)
                       + COS(RADIANS(:latitude)) * COS(RADIANS(s.latitude))
                       * POWER(SIN(RADIANS(s.longitude - :longitude) / 2.0), 2)
                   )))) <= s.service_radius_km
              ))
            ORDER BY
              CASE WHEN :sortMode = 'RELEVANCE' AND :query IS NOT NULL THEN
                CASE
                  WHEN LOWER(p.name) = LOWER(:query) THEN 0
                  WHEN LOWER(p.name) LIKE LOWER(CONCAT(:query, '%')) THEN 1
                  WHEN LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%')) THEN 2
                  WHEN LOWER(s.name) LIKE LOWER(CONCAT('%', :query, '%')) THEN 3
                  ELSE 4
                END
              END ASC,
              CASE WHEN :sortMode = 'PRICE' THEN p.price END ASC,
              CASE WHEN :sortMode = 'NEAREST' THEN
                6371.0 * 2.0 * ASIN(SQRT(LEAST(1.0, GREATEST(0.0,
                    POWER(SIN(RADIANS(s.latitude - :latitude) / 2.0), 2)
                    + COS(RADIANS(:latitude)) * COS(RADIANS(s.latitude))
                    * POWER(SIN(RADIANS(s.longitude - :longitude) / 2.0), 2)
                ))))
              END ASC,
              p.name ASC,
              p.id ASC
            """,
            countQuery="""
            SELECT COUNT(*)
            FROM pms_soko_product p
            JOIN pms_soko_store s ON s.id = p.store_id
            WHERE p.active = 1
              AND p.status = 'PUBLISHED'
              AND p.stock_quantity > 0
              AND s.active = 1
              AND s.status = 'PUBLISHED'
              AND :sortMode IN ('RELEVANCE', 'PRICE', 'NEAREST')
              AND (:storeId IS NULL OR p.store_id = :storeId)
              AND (:category IS NULL OR LOWER(p.category) = LOWER(:category))
              AND (:query IS NULL
                   OR LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%'))
                   OR LOWER(p.description) LIKE LOWER(CONCAT('%', :query, '%'))
                   OR LOWER(s.name) LIKE LOWER(CONCAT('%', :query, '%')))
              AND (:fulfilment = 'ALL'
                   OR (:fulfilment = 'DELIVERY' AND s.delivery_enabled = 1)
                   OR (:fulfilment = 'PICKUP' AND s.pickup_enabled = 1))
              AND (:minLatitude IS NULL OR s.latitude BETWEEN :minLatitude AND :maxLatitude)
              AND (:minLongitude IS NULL OR (
                   (:wrapLongitude = 0 AND s.longitude BETWEEN :minLongitude AND :maxLongitude)
                   OR (:wrapLongitude = 1 AND (s.longitude >= :minLongitude OR s.longitude <= :maxLongitude))
              ))
              AND (:latitude IS NULL OR (
                   s.latitude IS NOT NULL
                   AND s.longitude IS NOT NULL
                   AND 6371.0 * 2.0 * ASIN(SQRT(LEAST(1.0, GREATEST(0.0,
                       POWER(SIN(RADIANS(s.latitude - :latitude) / 2.0), 2)
                       + COS(RADIANS(:latitude)) * COS(RADIANS(s.latitude))
                       * POWER(SIN(RADIANS(s.longitude - :longitude) / 2.0), 2)
                   )))) <= :radiusKm
              ))
              AND (:fulfilment <> 'DELIVERY' OR :latitude IS NULL OR (
                   s.service_radius_km IS NOT NULL
                   AND s.service_radius_km > 0
                   AND 6371.0 * 2.0 * ASIN(SQRT(LEAST(1.0, GREATEST(0.0,
                       POWER(SIN(RADIANS(s.latitude - :latitude) / 2.0), 2)
                       + COS(RADIANS(:latitude)) * COS(RADIANS(s.latitude))
                       * POWER(SIN(RADIANS(s.longitude - :longitude) / 2.0), 2)
                   )))) <= s.service_radius_km
              ))
            """, nativeQuery=true)
    Page<SokoProduct> searchCatalog(
            Pageable pageable,
            @Param("storeId") Long storeId,
            @Param("category") String category,
            @Param("query") String query,
            @Param("fulfilment") String fulfilment,
            @Param("sortMode") String sortMode,
            @Param("latitude") Double latitude,
            @Param("longitude") Double longitude,
            @Param("radiusKm") Double radiusKm,
            @Param("minLatitude") Double minLatitude,
            @Param("maxLatitude") Double maxLatitude,
            @Param("minLongitude") Double minLongitude,
            @Param("maxLongitude") Double maxLongitude,
            @Param("wrapLongitude") int wrapLongitude);

    List<SokoProduct> findAllByStoreIdAndActiveTrueOrderByName(long storeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from SokoProduct p where p.id=:id and p.active=true")
    Optional<SokoProduct> findByIdForUpdate(long id);
    Page<SokoProduct> findAllByActiveTrue(Pageable pageable);
    long countByActiveTrue();
    long countByStatusAndActiveTrue(String status);
}
