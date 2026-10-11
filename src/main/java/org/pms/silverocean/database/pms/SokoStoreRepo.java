package org.pms.silverocean.database.pms;

import jakarta.persistence.LockModeType;
import org.pms.silverocean.database.pms.entities.SokoStore;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SokoStoreRepo extends JpaRepository<SokoStore, Long> {
    Optional<SokoStore> findByIdAndActiveTrue(long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SokoStore s where s.id=:id and s.active=true")
    Optional<SokoStore> findByIdForCheckout(@Param("id") long id);
    Optional<SokoStore> findByIdAndOwnerUserIdAndActiveTrue(long id, long ownerUserId);
    boolean existsByOwnerUserIdAndActiveTrue(long ownerUserId);
    List<SokoStore> findAllByOwnerUserIdAndActiveTrueOrderByName(long ownerUserId);
    Page<SokoStore> findAllByActiveTrue(Pageable pageable);
    Page<SokoStore> findAllByStatusAndActiveTrue(String status,Pageable pageable);
    @Query(value="""
            SELECT s.* FROM pms_soko_store s
            WHERE s.active=1 AND s.status='PUBLISHED'
              AND EXISTS (SELECT 1 FROM pms_soko_product p WHERE p.store_id=s.id AND p.active=1
                          AND p.status='PUBLISHED' AND p.stock_quantity>0)
              AND EXISTS (SELECT 1 FROM pms_user_subscription usub
                          WHERE usub.created_by=s.owner_user_id AND usub.product_key='SOKO'
                          AND usub.status='ACTIVE' AND usub.active=1
                          AND (usub.end_at IS NULL OR usub.end_at>UTC_TIMESTAMP(6)))
              AND EXISTS (SELECT 1 FROM pms_payment_account pacc
                          WHERE pacc.id=s.payment_account_id AND pacc.created_by=s.owner_user_id
                          AND pacc.category='MERCHANT' AND pacc.active=1 AND pacc.verified=1
                          AND pacc.channel IS NOT NULL)
              AND (:query IS NULL OR LOWER(s.name) LIKE LOWER(CONCAT('%',:query,'%'))
                   OR LOWER(s.address) LIKE LOWER(CONCAT('%',:query,'%')))
              AND (:fulfilment='ALL'
                   OR (:fulfilment='DELIVERY' AND s.delivery_enabled=1)
                   OR (:fulfilment='PICKUP' AND s.pickup_enabled=1))
            ORDER BY LOWER(s.name),s.id
            """,
            countQuery="""
            SELECT COUNT(*) FROM pms_soko_store s
            WHERE s.active=1 AND s.status='PUBLISHED'
              AND EXISTS (SELECT 1 FROM pms_soko_product p WHERE p.store_id=s.id AND p.active=1
                          AND p.status='PUBLISHED' AND p.stock_quantity>0)
              AND EXISTS (SELECT 1 FROM pms_user_subscription usub
                          WHERE usub.created_by=s.owner_user_id AND usub.product_key='SOKO'
                          AND usub.status='ACTIVE' AND usub.active=1
                          AND (usub.end_at IS NULL OR usub.end_at>UTC_TIMESTAMP(6)))
              AND EXISTS (SELECT 1 FROM pms_payment_account pacc
                          WHERE pacc.id=s.payment_account_id AND pacc.created_by=s.owner_user_id
                          AND pacc.category='MERCHANT' AND pacc.active=1 AND pacc.verified=1
                          AND pacc.channel IS NOT NULL)
              AND (:query IS NULL OR LOWER(s.name) LIKE LOWER(CONCAT('%',:query,'%'))
                   OR LOWER(s.address) LIKE LOWER(CONCAT('%',:query,'%')))
              AND (:fulfilment='ALL'
                   OR (:fulfilment='DELIVERY' AND s.delivery_enabled=1)
                   OR (:fulfilment='PICKUP' AND s.pickup_enabled=1))
            """,nativeQuery=true)
    Page<SokoStore> searchPublicSellers(String query,String fulfilment,Pageable pageable);
    @Query(value="""
            SELECT s.* FROM pms_soko_store s
            WHERE s.id=:id AND s.active=1 AND s.status='PUBLISHED'
              AND EXISTS (SELECT 1 FROM pms_user_subscription usub
                          WHERE usub.created_by=s.owner_user_id AND usub.product_key='SOKO'
                          AND usub.status='ACTIVE' AND usub.active=1
                          AND (usub.end_at IS NULL OR usub.end_at>UTC_TIMESTAMP(6)))
              AND EXISTS (SELECT 1 FROM pms_payment_account pacc
                          WHERE pacc.id=s.payment_account_id AND pacc.created_by=s.owner_user_id
                          AND pacc.category='MERCHANT' AND pacc.active=1 AND pacc.verified=1
                          AND pacc.channel IS NOT NULL)
            """,nativeQuery=true)
    Optional<SokoStore> findPublicSaleableById(@Param("id") long id);
    long countByActiveTrue();
    long countByStatusAndActiveTrue(String status);
}
