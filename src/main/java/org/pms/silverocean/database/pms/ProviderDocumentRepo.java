package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.ProviderDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.Set;

public interface ProviderDocumentRepo extends JpaRepository<ProviderDocument, Long> {
    @Query("SELECT d FROM ProviderDocument d JOIN ProviderService s ON s.id=d.serviceId JOIN ProviderProfile p ON p.id=s.profileId WHERE p.userId=:userId AND p.active=true AND s.active=true AND d.active=true AND d.verificationStatus='VERIFIED' AND (:categoryId IS NULL OR s.categoryId=:categoryId) AND (d.expiryDate IS NULL OR d.expiryDate>CURRENT_TIMESTAMP)")
    java.util.List<ProviderDocument> findVerifiedEvidence(long userId,Long categoryId);
    @Query("SELECT d FROM ProviderDocument d WHERE d.serviceId = :serviceId AND d.active = true ORDER BY d.createdOn DESC")
    Page<ProviderDocument> findByServiceId(long serviceId, Pageable pageable);

    @Query("SELECT d FROM ProviderDocument d WHERE d.id = :id AND d.serviceId = :serviceId AND d.active = true")
    Optional<ProviderDocument> findByIdAndServiceId(long id, long serviceId);

    @Query("SELECT d.documentType FROM ProviderDocument d WHERE d.serviceId = :serviceId AND d.active = true AND d.verificationStatus <> 'REJECTED' AND (d.expiryDate IS NULL OR d.expiryDate>CURRENT_TIMESTAMP)")
    Set<String> findUploadedDocumentTypesByServiceId(long serviceId);

    @Query("SELECT d.documentType FROM ProviderDocument d WHERE d.serviceId = :serviceId AND d.verificationStatus = 'VERIFIED' AND d.active = true AND (d.expiryDate IS NULL OR d.expiryDate>CURRENT_TIMESTAMP)")
    Set<String> findVerifiedDocumentTypesByServiceId(long serviceId);

    @Query("SELECT DISTINCT d.documentType FROM ProviderDocument d JOIN ProviderService s ON s.id=d.serviceId WHERE s.profileId=:profileId AND s.categoryId=:categoryId AND d.active=true AND s.active=true AND d.verificationStatus <> 'REJECTED' AND (d.expiryDate IS NULL OR d.expiryDate>CURRENT_TIMESTAMP)")
    Set<String> findReusableUploadedDocumentTypes(long profileId,long categoryId);

    @Query("SELECT DISTINCT d.documentType FROM ProviderDocument d JOIN ProviderService s ON s.id=d.serviceId WHERE s.profileId=:profileId AND s.categoryId=:categoryId AND d.verificationStatus='VERIFIED' AND d.active=true AND s.active=true AND (d.expiryDate IS NULL OR d.expiryDate>CURRENT_TIMESTAMP)")
    Set<String> findReusableVerifiedDocumentTypes(long profileId,long categoryId);
}
