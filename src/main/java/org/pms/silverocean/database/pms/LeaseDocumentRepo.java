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
    @Query("SELECT d FROM LeaseDocument d WHERE d.id=:id AND d.active AND (d.issuerUserId=:userId OR d.recipientUserId=:userId)")
    Optional<LeaseDocument> findAccessible(long id, long userId);
    @Query("SELECT d FROM LeaseDocument d WHERE d.active AND (d.issuerUserId=:userId OR d.recipientUserId=:userId) ORDER BY d.createdOn DESC")
    Page<LeaseDocument> findAllAccessible(long userId, Pageable pageable);
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
