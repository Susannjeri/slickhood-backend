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
            "s.id, s.profileId, s.categoryId, s.categoryName, s.title, s.description, s.tier, s.amount, s.currency, s.pricingUnit, s.status, s.createdOn, " +
            "p.businessName, p.description, p.serviceArea, p.latitude, p.longitude, " +
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
            "FROM ProviderService s JOIN ProviderProfile p ON p.id = s.profileId JOIN ServiceCategory c ON c.id = s.categoryId " +
            "JOIN Users owner ON owner.id=p.userId JOIN PaymentAccount pay ON pay.id=p.paymentAccountId " +
            "WHERE s.status = 'LISTED' AND s.pricingUnit IN :pricingUnits AND p.status='ACTIVE' AND p.active=true AND c.active=true AND s.active = true " +
            "AND owner.active=true AND owner.verified=true AND owner.accountStatus=:activeAccountStatus " +
            "AND pay.active=true AND pay.verified=true AND pay.createdBy=p.userId AND pay.category=:merchantCategory AND pay.channel IN :availableChannels " +
            "AND EXISTS (SELECT subscription.id FROM UserSubscription subscription WHERE subscription.createdBy=p.userId AND subscription.productKey=:productKey AND subscription.status=:subscriptionStatus AND subscription.active=true AND (subscription.endAt IS NULL OR subscription.endAt>CURRENT_TIMESTAMP)) " +
            "AND (:categoryId IS NULL OR s.categoryId = :categoryId) ORDER BY s.createdOn DESC, s.id DESC",
            countQuery = "SELECT COUNT(s) FROM ProviderService s JOIN ProviderProfile p ON p.id = s.profileId JOIN ServiceCategory c ON c.id=s.categoryId JOIN Users owner ON owner.id=p.userId JOIN PaymentAccount pay ON pay.id=p.paymentAccountId WHERE s.status = 'LISTED' AND s.pricingUnit IN :pricingUnits AND p.status='ACTIVE' AND p.active=true AND c.active=true " +
                    "AND owner.active=true AND owner.verified=true AND owner.accountStatus=:activeAccountStatus " +
                    "AND pay.active=true AND pay.verified=true AND pay.createdBy=p.userId AND pay.category=:merchantCategory AND pay.channel IN :availableChannels " +
                    "AND EXISTS (SELECT subscription.id FROM UserSubscription subscription WHERE subscription.createdBy=p.userId AND subscription.productKey=:productKey AND subscription.status=:subscriptionStatus AND subscription.active=true AND (subscription.endAt IS NULL OR subscription.endAt>CURRENT_TIMESTAMP)) " +
                    " AND s.active = true AND (:categoryId IS NULL OR s.categoryId = :categoryId)")
    Page<ProviderServiceDTO> findListedByCategoryEnriched(Pageable pageable, Long categoryId,
            java.util.Collection<org.pms.silverocean.service.sp.enums.PricingUnit> pricingUnits,
            org.pms.silverocean.service.subscription.enums.SubscriptionProduct productKey,
            org.pms.silverocean.service.subscription.enums.SubscriptionStatus subscriptionStatus,
            String activeAccountStatus,
            org.pms.silverocean.service.account.enums.AccountCategory merchantCategory,
            java.util.Collection<org.pms.silverocean.service.payment.wrappers.PaymentChannel> availableChannels);

    @Query(value = "SELECT new org.pms.silverocean.service.sp.wrappers.ProviderServiceDTO(" + ENRICHED_FIELDS + ") " +
            "FROM ProviderService s JOIN ProviderProfile p ON p.id=s.profileId JOIN ServiceCategory c ON c.id=s.categoryId JOIN Users owner ON owner.id=p.userId JOIN PaymentAccount pay ON pay.id=p.paymentAccountId " +
            "WHERE s.status='LISTED' AND s.pricingUnit IN :pricingUnits AND p.status='ACTIVE' AND p.active=true AND c.active=true AND s.active=true " +
            "AND owner.active=true AND owner.verified=true AND owner.accountStatus=:activeAccountStatus " +
            "AND pay.active=true AND pay.verified=true AND pay.createdBy=p.userId AND pay.category=:merchantCategory AND pay.channel IN :availableChannels " +
            "AND EXISTS (SELECT subscription.id FROM UserSubscription subscription WHERE subscription.createdBy=p.userId AND subscription.productKey=:productKey AND subscription.status=:subscriptionStatus AND subscription.active=true AND (subscription.endAt IS NULL OR subscription.endAt>CURRENT_TIMESTAMP)) " +
            "AND (:categoryId IS NULL OR s.categoryId=:categoryId) " +
            "AND (:query IS NULL OR LOWER(s.categoryName) LIKE LOWER(CONCAT('%',:query,'%')) OR LOWER(s.title) LIKE LOWER(CONCAT('%',:query,'%')) OR LOWER(s.description) LIKE LOWER(CONCAT('%',:query,'%')) OR LOWER(p.businessName) LIKE LOWER(CONCAT('%',:query,'%')) OR LOWER(p.description) LIKE LOWER(CONCAT('%',:query,'%')) OR LOWER(p.serviceArea) LIKE LOWER(CONCAT('%',:query,'%'))) " +
            "AND (:minAmount IS NULL OR s.amount>=:minAmount) AND (:maxAmount IS NULL OR s.amount<=:maxAmount) " +
            "AND (:minLat IS NULL OR (ROUND(p.latitude,2) BETWEEN :minLat AND :maxLat AND ROUND(p.longitude,2) BETWEEN :minLng AND :maxLng)) " +
            "ORDER BY CASE WHEN :latitude IS NULL THEN 0.0 ELSE ((ROUND(p.latitude,2)-:latitude)*(ROUND(p.latitude,2)-:latitude)+(ROUND(p.longitude,2)-:longitude)*(ROUND(p.longitude,2)-:longitude)) END ASC, s.amount ASC, s.id ASC",
            countQuery = "SELECT COUNT(s) FROM ProviderService s JOIN ProviderProfile p ON p.id=s.profileId JOIN ServiceCategory c ON c.id=s.categoryId JOIN Users owner ON owner.id=p.userId JOIN PaymentAccount pay ON pay.id=p.paymentAccountId " +
                    "WHERE s.status='LISTED' AND s.pricingUnit IN :pricingUnits AND p.status='ACTIVE' AND p.active=true AND c.active=true AND s.active=true " +
                    "AND owner.active=true AND owner.verified=true AND owner.accountStatus=:activeAccountStatus " +
                    "AND pay.active=true AND pay.verified=true AND pay.createdBy=p.userId AND pay.category=:merchantCategory AND pay.channel IN :availableChannels " +
                    "AND EXISTS (SELECT subscription.id FROM UserSubscription subscription WHERE subscription.createdBy=p.userId AND subscription.productKey=:productKey AND subscription.status=:subscriptionStatus AND subscription.active=true AND (subscription.endAt IS NULL OR subscription.endAt>CURRENT_TIMESTAMP)) " +
                    "AND (:categoryId IS NULL OR s.categoryId=:categoryId) " +
                    "AND (:query IS NULL OR LOWER(s.categoryName) LIKE LOWER(CONCAT('%',:query,'%')) OR LOWER(s.title) LIKE LOWER(CONCAT('%',:query,'%')) OR LOWER(s.description) LIKE LOWER(CONCAT('%',:query,'%')) OR LOWER(p.businessName) LIKE LOWER(CONCAT('%',:query,'%')) OR LOWER(p.description) LIKE LOWER(CONCAT('%',:query,'%')) OR LOWER(p.serviceArea) LIKE LOWER(CONCAT('%',:query,'%'))) " +
                    "AND (:minAmount IS NULL OR s.amount>=:minAmount) AND (:maxAmount IS NULL OR s.amount<=:maxAmount) " +
                    "AND (:minLat IS NULL OR (ROUND(p.latitude,2) BETWEEN :minLat AND :maxLat AND ROUND(p.longitude,2) BETWEEN :minLng AND :maxLng))")
    Page<ProviderServiceDTO> searchDirectory(Pageable pageable, Long categoryId, String query, BigDecimal minAmount, BigDecimal maxAmount,
                                             Double latitude, Double longitude, Double minLat, Double maxLat, Double minLng, Double maxLng,
                                             java.util.Collection<org.pms.silverocean.service.sp.enums.PricingUnit> pricingUnits,
                                             org.pms.silverocean.service.subscription.enums.SubscriptionProduct productKey,
                                             org.pms.silverocean.service.subscription.enums.SubscriptionStatus subscriptionStatus,
                                             String activeAccountStatus,
                                             org.pms.silverocean.service.account.enums.AccountCategory merchantCategory,
                                             java.util.Collection<org.pms.silverocean.service.payment.wrappers.PaymentChannel> availableChannels);

    @Query(value = "SELECT new org.pms.silverocean.service.sp.wrappers.ProviderServiceDTO(" + ENRICHED_FIELDS + ") " +
            "FROM ProviderService s JOIN ProviderProfile p ON p.id = s.profileId JOIN ServiceCategory c ON c.id=s.categoryId JOIN Users owner ON owner.id=p.userId JOIN PaymentAccount pay ON pay.id=p.paymentAccountId " +
            "WHERE s.id = :id AND s.status='LISTED' AND s.pricingUnit IN :pricingUnits AND p.status='ACTIVE' AND p.active=true AND c.active=true AND s.active = true " +
            "AND owner.active=true AND owner.verified=true AND owner.accountStatus=:activeAccountStatus " +
            "AND pay.active=true AND pay.verified=true AND pay.createdBy=p.userId AND pay.category=:merchantCategory AND pay.channel IN :availableChannels " +
            "AND EXISTS (SELECT subscription.id FROM UserSubscription subscription WHERE subscription.createdBy=p.userId AND subscription.productKey=:productKey AND subscription.status=:subscriptionStatus AND subscription.active=true AND (subscription.endAt IS NULL OR subscription.endAt>CURRENT_TIMESTAMP))")
    Optional<ProviderServiceDTO> findByIdEnriched(long id,
            java.util.Collection<org.pms.silverocean.service.sp.enums.PricingUnit> pricingUnits,
            org.pms.silverocean.service.subscription.enums.SubscriptionProduct productKey,
            org.pms.silverocean.service.subscription.enums.SubscriptionStatus subscriptionStatus,
            String activeAccountStatus,
            org.pms.silverocean.service.account.enums.AccountCategory merchantCategory,
            java.util.Collection<org.pms.silverocean.service.payment.wrappers.PaymentChannel> availableChannels);

    @Query("SELECT s FROM ProviderService s WHERE s.id = :id AND s.profileId = :profileId AND s.active = true")
    Optional<ProviderService> findByIdAndProfileId(long id, long profileId);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ProviderService s where s.id=:id and s.active=true")
    Optional<ProviderService> findByIdForUpdate(long id);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ProviderService s where s.id=:id")
    Optional<ProviderService> findAnyByIdForUpdate(long id);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ProviderService s where s.id=:id and s.profileId=:profileId and s.active=true")
    Optional<ProviderService> findOwnedForUpdate(long id,long profileId);
}
