package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.SokoStore;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

public interface SokoStoreRepo extends JpaRepository<SokoStore, Long> {
    Optional<SokoStore> findByIdAndActiveTrue(long id);
    Optional<SokoStore> findByIdAndOwnerUserIdAndActiveTrue(long id, long ownerUserId);
    boolean existsByOwnerUserIdAndActiveTrue(long ownerUserId);
    List<SokoStore> findAllByOwnerUserIdAndActiveTrueOrderByName(long ownerUserId);
    Page<SokoStore> findAllByActiveTrue(Pageable pageable);
    Page<SokoStore> findAllByStatusAndActiveTrue(String status,Pageable pageable);
    @Query(value="""
            SELECT s FROM SokoStore s
            WHERE s.active=true AND s.status='PUBLISHED'
              AND EXISTS (SELECT p.id FROM SokoProduct p WHERE p.storeId=s.id AND p.active=true
                          AND p.status='PUBLISHED' AND p.stockQuantity>0)
              AND (:query IS NULL OR LOWER(s.name) LIKE LOWER(CONCAT('%',:query,'%'))
                   OR LOWER(s.address) LIKE LOWER(CONCAT('%',:query,'%')))
              AND (:fulfilment='ALL'
                   OR (:fulfilment='DELIVERY' AND s.deliveryEnabled=true)
                   OR (:fulfilment='PICKUP' AND s.pickupEnabled=true))
            ORDER BY LOWER(s.name),s.id
            """,
            countQuery="""
            SELECT COUNT(s) FROM SokoStore s
            WHERE s.active=true AND s.status='PUBLISHED'
              AND EXISTS (SELECT p.id FROM SokoProduct p WHERE p.storeId=s.id AND p.active=true
                          AND p.status='PUBLISHED' AND p.stockQuantity>0)
              AND (:query IS NULL OR LOWER(s.name) LIKE LOWER(CONCAT('%',:query,'%'))
                   OR LOWER(s.address) LIKE LOWER(CONCAT('%',:query,'%')))
              AND (:fulfilment='ALL'
                   OR (:fulfilment='DELIVERY' AND s.deliveryEnabled=true)
                   OR (:fulfilment='PICKUP' AND s.pickupEnabled=true))
            """)
    Page<SokoStore> searchPublicSellers(String query,String fulfilment,Pageable pageable);
    long countByActiveTrue();
    long countByStatusAndActiveTrue(String status);
}
