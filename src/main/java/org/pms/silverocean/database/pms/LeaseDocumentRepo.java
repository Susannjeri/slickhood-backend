package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.LeaseDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Optional;
import org.pms.silverocean.service.leasedocument.LeaseDocumentStatus;
import org.pms.silverocean.service.leasedocument.LeaseDocumentType;

public interface LeaseDocumentRepo extends JpaRepository<LeaseDocument, Long> {
    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE LeaseDocument d SET d.status='EXPIRED' WHERE d.saleId=:saleId AND d.active " +
            "AND d.documentType='PROPERTY_SALE_LETTER_OF_OFFER' AND d.status IN ('DRAFT','ISSUED','ACKNOWLEDGED','PARTIALLY_SIGNED') " +
            "AND d.responseDueDate<:today")
    int expireSaleOffers(long saleId, java.time.LocalDate today);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM LeaseDocument d WHERE d.id=:id AND d.active AND (d.issuerUserId=:userId OR d.recipientUserId=:userId)")
    Optional<LeaseDocument> findAccessibleForUpdate(long id, long userId);

    @Query("SELECT COUNT(d)>0 FROM LeaseDocument d WHERE d.leaseId=:leaseId AND d.active AND d.status NOT IN ('CANCELLED','EXPIRED') " +
            "AND d.documentType IN ('RESIDENTIAL_LEASE_AGREEMENT','COMMERCIAL_LEASE_AGREEMENT')")
    boolean existsCurrentAgreement(long leaseId);

    @Query("SELECT COUNT(d)>0 FROM LeaseDocument d WHERE d.propertyId=:propertyId AND d.recipientUserId=:recipientId " +
            "AND ((:unitId IS NULL AND d.unitId IS NULL) OR d.unitId=:unitId) AND d.active AND d.status NOT IN ('CANCELLED','EXPIRED') " +
            "AND (d.effectiveDate IS NULL OR d.effectiveDate>=:ownershipStart) " +
            "AND (:ownershipCreatedAt IS NULL OR d.createdOn IS NULL OR d.createdOn>=:ownershipCreatedAt) " +
            "AND d.documentType='ESTATE_RESIDENTIAL_AGREEMENT'")
    boolean existsCurrentEstateAgreement(long propertyId, Long unitId, long recipientId, java.time.LocalDate ownershipStart, java.time.ZonedDateTime ownershipCreatedAt);
    @Query("SELECT d FROM LeaseDocument d WHERE d.id=:id AND d.active AND (d.issuerUserId=:userId OR d.recipientUserId=:userId)")
    Optional<LeaseDocument> findAccessible(long id, long userId);
    @Query("SELECT d FROM LeaseDocument d WHERE d.active AND (d.issuerUserId=:userId OR d.recipientUserId=:userId) ORDER BY d.createdOn DESC")
    Page<LeaseDocument> findAllAccessible(long userId, Pageable pageable);
    @Query("SELECT d FROM LeaseDocument d WHERE d.active AND (d.issuerUserId=:userId OR d.recipientUserId=:userId) " +
            "AND (:leaseId IS NULL OR d.leaseId=:leaseId) AND (:saleId IS NULL OR d.saleId=:saleId) " +
            "AND (:propertyId IS NULL OR d.propertyId=:propertyId) AND (:unitId IS NULL OR d.unitId=:unitId) ORDER BY d.createdOn DESC,d.id DESC")
    Page<LeaseDocument> findAccessiblePage(long userId, Long leaseId, Long saleId, Long propertyId, Long unitId, Pageable pageable);

    @Query("SELECT d FROM LeaseDocument d WHERE d.leaseId=:leaseId AND d.active " +
            "AND (d.issuerUserId=:userId OR d.recipientUserId=:userId) " +
            "AND d.documentType IN ('RESIDENTIAL_LEASE_AGREEMENT','COMMERCIAL_LEASE_AGREEMENT') " +
            "AND d.status NOT IN ('CANCELLED','EXPIRED') ORDER BY d.createdOn DESC,d.id DESC")
    java.util.List<LeaseDocument> findAccessibleAgreement(long leaseId, long userId, Pageable pageable);
    long countByRecipientUserIdAndStatusAndActiveTrue(long userId, LeaseDocumentStatus status);
    Optional<LeaseDocument> findByIdAndPropertyIdAndUnitIdAndActiveTrue(long id, long propertyId, Long unitId);
    boolean existsByLeaseIdAndDocumentTypeAndStatusAndActiveTrue(long leaseId, LeaseDocumentType type, LeaseDocumentStatus status);
    boolean existsBySaleIdAndDocumentTypeAndStatusAndActiveTrue(long saleId, LeaseDocumentType type, LeaseDocumentStatus status);

    @Query("SELECT CASE WHEN COUNT(d)>0 THEN true ELSE false END FROM LeaseDocument d WHERE d.leaseId=:leaseId " +
            "AND d.documentType=:type AND d.active=true AND d.status NOT IN ('CANCELLED','EXPIRED')")
    boolean existsOpen(long leaseId, LeaseDocumentType type);

    @Query("SELECT CASE WHEN COUNT(d)>0 THEN true ELSE false END FROM LeaseDocument d WHERE d.saleId=:saleId " +
            "AND d.documentType=:type AND d.active=true AND d.status NOT IN ('CANCELLED','EXPIRED')")
    boolean existsOpenForSale(long saleId, LeaseDocumentType type);
}
